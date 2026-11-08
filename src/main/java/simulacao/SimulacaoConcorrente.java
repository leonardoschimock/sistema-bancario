package simulacao;

import domain.Conta;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dispara as threads de um {@link CenarioSimulacao}, acompanha até todas terminarem e compara os
 * saldos obtidos com os esperados.
 *
 * <h2>Como as threads são disparadas</h2>
 * <ol>
 *   <li>Uma {@link OperacaoBancariaThread} é criada para cada plano do cenário.</li>
 *   <li>Um {@link ExecutorService} com EXATAMENTE uma thread por operador executa cada uma
 *       (pool menor deixaria operadores na fila, sem concorrência entre eles, e os faria esperar
 *       a largada travando o pool).</li>
 *   <li>Cada tarefa, antes de rodar o operador, avisa que está pronta
 *       ({@code CountDownLatch prontas}) e espera a largada ({@code CountDownLatch largada}). A
 *       thread principal só dá a largada quando TODAS estão prontas, para que comecem juntas e
 *       disputem as mesmas contas ao mesmo tempo. Isso não altera nenhum cálculo; só aumenta a
 *       sobreposição. Não há {@code sleep}.</li>
 * </ol>
 *
 * <h2>Como se sabe que todas terminaram</h2>
 * Cada tarefa devolve um {@link Future}. A thread principal chama {@code Future.get(prazo)} em
 * TODOS eles: o {@code get} só retorna quando a tarefa acabou, normalmente ou com exceção (que
 * chega como {@link ExecutionException}, com a causa original). Só depois do último {@code get}
 * os contadores dos operadores e os saldos das contas são lidos; o {@code get} também é o que
 * garante, pelo modelo de memória do Java, que a thread principal enxerga o que as outras
 * gravaram. Por fim, {@code shutdown()} + {@code awaitTermination()} confirmam que as threads do
 * pool encerraram. Como confirmação numérica, o resultado compara operações concluídas
 * (aceitas + recusadas) com as planejadas.
 *
 * <h2>Se algo travar</h2>
 * O prazo é global. Ao estourar, a simulação NÃO lê saldos (um {@code getSaldo()} bloquearia se
 * os locks estivessem presos num deadlock), pergunta à JVM se há threads em deadlock
 * ({@link ThreadMXBean#findDeadlockedThreads()}), tenta cancelar com {@code shutdownNow()} e
 * devolve um resultado com {@code terminou == false}. As threads do pool são daemon, então uma
 * thread irrecuperavelmente presa não impede o programa de encerrar.
 */
public final class SimulacaoConcorrente {

    private static final long PRAZO_ENCERRAMENTO_SEGUNDOS = 5;

    private SimulacaoConcorrente() {
    }

    /**
     * Executa o cenário (uso único) e devolve o que foi observado.
     *
     * @param prazo tempo máximo, no total, para todas as threads ficarem prontas e terminarem
     * @throws IllegalStateException se o cenário já foi executado
     * @throws InterruptedException  se a thread que chamou for interrompida enquanto aguarda
     *                               (as threads da simulação são canceladas antes)
     */
    public static ResultadoSimulacao executar(CenarioSimulacao cenario, Duration prazo)
            throws InterruptedException {
        if (cenario == null) {
            throw new IllegalArgumentException("O cenário é obrigatório.");
        }
        if (prazo == null || prazo.isZero() || prazo.isNegative()) {
            throw new IllegalArgumentException("O prazo deve ser positivo.");
        }
        cenario.consumir();

        List<OperacaoBancariaThread> operadores = new ArrayList<>();
        List<List<OperacaoPlanejada>> planos = cenario.getPlanos();
        for (int i = 0; i < planos.size(); i++) {
            operadores.add(new OperacaoBancariaThread("operador-" + (i + 1), planos.get(i)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(operadores.size(), fabricaDeThreads());
        CountDownLatch prontas = new CountDownLatch(operadores.size());
        CountDownLatch largada = new CountDownLatch(1);
        long prazoNs = prazo.toNanos();
        long limite = System.nanoTime() + prazoNs;
        List<Future<Void>> futuros = new ArrayList<>();

        try {
            for (OperacaoBancariaThread operador : operadores) {
                Callable<Void> tarefa = () -> {
                    prontas.countDown();   // "estou pronta"
                    largada.await();       // espera o sinal de largada
                    operador.run();        // as operações em si (só chamam métodos de Conta)
                    return null;
                };
                futuros.add(pool.submit(tarefa));
            }

            if (!prontas.await(prazoNs, TimeUnit.NANOSECONDS)) {
                return naoTerminou(pool, prazoNs, "As threads não ficaram prontas a tempo.");
            }
            long inicio = System.nanoTime();
            largada.countDown();           // largada simultânea

            List<ResultadoSimulacao.FalhaOperador> falhas = new ArrayList<>();
            for (int i = 0; i < futuros.size(); i++) {
                long restante = Math.max(0, limite - System.nanoTime());
                try {
                    futuros.get(i).get(restante, TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    // A thread terminou com exceção: guarda a causa ORIGINAL e segue aguardando as demais.
                    falhas.add(new ResultadoSimulacao.FalhaOperador(operadores.get(i).getNome(), e.getCause()));
                } catch (TimeoutException e) {
                    return naoTerminou(pool, System.nanoTime() - inicio,
                            "Prazo de " + prazo.toSeconds() + "s esgotado com tarefas ainda em execução ("
                                    + tarefasPendentes(futuros) + " de " + futuros.size() + ").");
                }
            }
            Duration duracao = Duration.ofNanos(System.nanoTime() - inicio);

            // Todas as tarefas acabaram. Confirma que as threads do pool também encerraram.
            pool.shutdown();
            if (!pool.awaitTermination(PRAZO_ENCERRAMENTO_SEGUNDOS, TimeUnit.SECONDS)) {
                return naoTerminou(pool, System.nanoTime() - inicio,
                        "As threads do pool não encerraram após o fim das tarefas.");
            }

            // Só agora é seguro ler contadores e saldos.
            return new ResultadoSimulacao(true, duracao, null, resumir(operadores), falhas, comparar(cenario));
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            pool.shutdownNow(); // inofensivo se já encerrou; garante que nada fique sobrando
        }
    }

    private static ResultadoSimulacao naoTerminou(ExecutorService pool, long duracaoNs, String motivo) {
        String diagnostico = motivo + " " + descreverDeadlock();
        pool.shutdownNow();
        return new ResultadoSimulacao(false, Duration.ofNanos(duracaoNs), diagnostico.trim(),
                List.of(), List.of(), List.of());
    }

    private static int tarefasPendentes(List<Future<Void>> futuros) {
        int pendentes = 0;
        for (Future<Void> futuro : futuros) {
            if (!futuro.isDone()) {
                pendentes++;
            }
        }
        return pendentes;
    }

    private static List<ResultadoSimulacao.ResumoOperador> resumir(List<OperacaoBancariaThread> operadores) {
        List<ResultadoSimulacao.ResumoOperador> resumos = new ArrayList<>();
        for (OperacaoBancariaThread o : operadores) {
            resumos.add(new ResultadoSimulacao.ResumoOperador(o.getNome(), o.getTotalPlanejado(),
                    o.getAceitas(), o.getRecusadas(), o.getAmostrasDeRecusa(), o.foiInterrompida()));
        }
        return resumos;
    }

    private static List<ResultadoSimulacao.ComparacaoSaldo> comparar(CenarioSimulacao cenario) {
        List<ResultadoSimulacao.ComparacaoSaldo> saldos = new ArrayList<>();
        List<Conta> contas = cenario.getContas();
        for (int i = 0; i < contas.size(); i++) {
            Conta conta = contas.get(i);
            BigDecimal obtido = conta.getSaldo(); // leitura pelo método de Conta (com o lock dela)
            saldos.add(new ResultadoSimulacao.ComparacaoSaldo(conta.getTitular().getNome(),
                    cenario.getSaldosIniciais().get(i), cenario.getSaldosEsperados().get(i), obtido));
        }
        return saldos;
    }

    /** Diz se a JVM enxerga threads em deadlock (ciclo de locks) e quais são. */
    private static String descreverDeadlock() {
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] ids = mx.findDeadlockedThreads();
        if (ids == null) {
            return "A JVM não detectou ciclo de locks (pode ser lentidão, não deadlock).";
        }
        StringBuilder sb = new StringBuilder("DEADLOCK detectado entre as threads:");
        for (ThreadInfo info : mx.getThreadInfo(ids)) {
            sb.append(" [").append(info.getThreadName())
                    .append(" espera o lock de ").append(info.getLockOwnerName()).append("]");
        }
        return sb.toString();
    }

    /** Threads daemon e com nome: se uma ficar presa, ela não impede a JVM de encerrar. */
    private static ThreadFactory fabricaDeThreads() {
        AtomicInteger contador = new AtomicInteger();
        return tarefa -> {
            Thread thread = new Thread(tarefa, "sim-worker-" + contador.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
