package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de DEADLOCK e de integridade de {@link Conta#transferirPara}: várias threads fazem
 * transferências em sentidos opostos entre as MESMAS contas.
 *
 * Como o teste detecta travamento sem depender de pausas fixas:
 * - cada tarefa é um {@code Callable} enviado a um ExecutorService com uma thread por tarefa;
 * - {@code Future.get(timeout)} espera o fim da tarefa; se estourar o tempo, o teste FALHA
 *   (em vez de ficar preso para sempre) e usa {@link ThreadMXBean#findDeadlockedThreads()} para
 *   informar se o motivo foi um deadlock de verdade (ciclo de locks);
 * - dois {@code CountDownLatch} fazem todas as threads largarem ao mesmo tempo, o que maximiza
 *   a chance de A→B e B→A se cruzarem (sem sleep).
 *
 * Limite honesto: passar aqui é evidência, não prova. A garantia vem do raciocínio sobre a
 * ordem global de locks (ver Javadoc de {@link Conta}); o teste serve para detectar a violação
 * dela, e foi verificado contra uma versão que trava origem→destino sem ordem, onde falha.
 */
class ContaTransferenciaConcorrenteTest {

    private static final long TIMEOUT_SEGUNDOS = 30;

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static Conta novaConta(String nome, String saldoInicial) {
        return new Conta(new Cliente(nome), dinheiro(saldoInicial));
    }

    /**
     * Executa as tarefas ao mesmo tempo (uma thread por tarefa) e espera TODAS terminarem.
     * Falha se alguma lançar exceção ou se o tempo estourar (indicando deadlock, se for o caso).
     */
    private static void executarEmParalelo(List<Runnable> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        try {
            CountDownLatch prontas = new CountDownLatch(tarefas.size());
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Object>> futuros = new ArrayList<>();
            for (Runnable tarefa : tarefas) {
                Callable<Object> envolvida = () -> {
                    prontas.countDown();
                    largada.await();
                    tarefa.run();
                    return null;
                };
                futuros.add(pool.submit(envolvida));
            }
            assertTrue(prontas.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS),
                    "As threads não ficaram prontas a tempo.");
            largada.countDown();

            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SEGUNDOS);
            for (Future<Object> futuro : futuros) {
                long restante = Math.max(0, limite - System.nanoTime());
                try {
                    futuro.get(restante, TimeUnit.NANOSECONDS);   // repassa exceções da thread
                } catch (TimeoutException e) {
                    fail("Tempo esgotado: as transferências não terminaram em " + TIMEOUT_SEGUNDOS
                            + "s. " + descreverDeadlock());
                }
            }
        } finally {
            pool.shutdownNow();
        }
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

    private static Runnable repetir(int vezes, Runnable operacao) {
        return () -> {
            for (int i = 0; i < vezes; i++) {
                operacao.run();
            }
        };
    }

    private static void adicionar(List<Runnable> tarefas, int threads, Runnable tarefa) {
        for (int i = 0; i < threads; i++) {
            tarefas.add(tarefa);
        }
    }

    // ---------- Sentidos opostos, todas as transferências aceitas ----------

    @Test
    @DisplayName("Transferências A→B e B→A simultâneas terminam sem deadlock e dão os saldos exatos")
    void transferenciasEmSentidosOpostosSemDeadlock() throws Exception {
        int rodadas = 5;
        int threadsPorSentido = 4;
        int operacoes = 25_000;

        for (int rodada = 1; rodada <= rodadas; rodada++) {
            // Saldos grandes: nenhuma transferência é recusada, então o resultado é determinístico.
            Conta a = novaConta("Ana", "10000000.00");
            Conta b = novaConta("Bruno", "10000000.00");
            BigDecimal totalAntes = a.getSaldo().add(b.getSaldo());

            List<Runnable> tarefas = new ArrayList<>();
            adicionar(tarefas, threadsPorSentido, repetir(operacoes, () -> a.transferirPara(b, dinheiro("3.00"))));
            adicionar(tarefas, threadsPorSentido, repetir(operacoes, () -> b.transferirPara(a, dinheiro("1.00"))));
            executarEmParalelo(tarefas);

            // A→B: 4 * 25.000 * 3,00 = 300.000,00 ; B→A: 4 * 25.000 * 1,00 = 100.000,00
            assertEquals(dinheiro("9800000.00"), a.getSaldo(), "Saldo de A incorreto na rodada " + rodada);
            assertEquals(dinheiro("10200000.00"), b.getSaldo(), "Saldo de B incorreto na rodada " + rodada);
            assertEquals(totalAntes, a.getSaldo().add(b.getSaldo()), "Valor total não conservado na rodada " + rodada);
        }
    }

    // ---------- Sentidos opostos com saldo limitado: há recusas ----------

    @Test
    @DisplayName("Transferências opostas com saldo limitado: recusas não alteram saldos e o total é conservado")
    void transferenciasOpostasComSaldoLimitadoConservamTotal() throws Exception {
        int rodadas = 5;
        int threadsPorSentido = 4;
        int tentativas = 25_000;

        for (int rodada = 1; rodada <= rodadas; rodada++) {
            // Saldos pequenos perto do volume transferido: muitas transferências são recusadas.
            Conta a = novaConta("Carla", "50.00");
            Conta b = novaConta("Diego", "50.00");
            AtomicInteger aceitasAB = new AtomicInteger();
            AtomicInteger aceitasBA = new AtomicInteger();
            AtomicInteger recusadas = new AtomicInteger();

            List<Runnable> tarefas = new ArrayList<>();
            adicionar(tarefas, threadsPorSentido, repetir(tentativas, () -> {
                try {
                    a.transferirPara(b, dinheiro("7.00"));
                    aceitasAB.incrementAndGet();
                } catch (SaldoInsuficienteException e) {
                    recusadas.incrementAndGet();
                }
            }));
            adicionar(tarefas, threadsPorSentido, repetir(tentativas, () -> {
                try {
                    b.transferirPara(a, dinheiro("5.00"));
                    aceitasBA.incrementAndGet();
                } catch (SaldoInsuficienteException e) {
                    recusadas.incrementAndGet();
                }
            }));
            executarEmParalelo(tarefas);

            // Cada tentativa foi aceita ou recusada, nunca "meio aplicada".
            assertEquals(2 * threadsPorSentido * tentativas,
                    aceitasAB.get() + aceitasBA.get() + recusadas.get(), "Tentativas perdidas na rodada " + rodada);

            // Saldo esperado calculado a partir do que foi ACEITO: uma recusa que alterasse
            // algum saldo, ou um débito sem o crédito correspondente, quebraria estas igualdades.
            BigDecimal esperadoA = dinheiro("50.00")
                    .subtract(dinheiro("7.00").multiply(BigDecimal.valueOf(aceitasAB.get())))
                    .add(dinheiro("5.00").multiply(BigDecimal.valueOf(aceitasBA.get())));
            BigDecimal esperadoB = dinheiro("50.00")
                    .add(dinheiro("7.00").multiply(BigDecimal.valueOf(aceitasAB.get())))
                    .subtract(dinheiro("5.00").multiply(BigDecimal.valueOf(aceitasBA.get())));
            assertEquals(esperadoA, a.getSaldo(), "Saldo de A incorreto na rodada " + rodada);
            assertEquals(esperadoB, b.getSaldo(), "Saldo de B incorreto na rodada " + rodada);
            assertEquals(dinheiro("100.00"), a.getSaldo().add(b.getSaldo()),
                    "Valor total não conservado na rodada " + rodada);
            assertTrue(a.getSaldo().signum() >= 0 && b.getSaldo().signum() >= 0, "Saldo negativo na rodada " + rodada);
            assertTrue(recusadas.get() > 0, "A carga deveria provocar recusas (teste sem poder de detecção).");
        }
    }

    // ---------- Ciclo com mais de duas contas ----------

    @Test
    @DisplayName("Ciclo A→B, B→C, C→A simultâneo não trava e conserva o total")
    void cicloDeTresContasSemDeadlock() throws Exception {
        int operacoes = 30_000;
        Conta a = novaConta("Elisa", "1000000.00");
        Conta b = novaConta("Fabio", "1000000.00");
        Conta c = novaConta("Gabi", "1000000.00");

        List<Runnable> tarefas = new ArrayList<>();
        adicionar(tarefas, 3, repetir(operacoes, () -> a.transferirPara(b, dinheiro("1.00"))));
        adicionar(tarefas, 3, repetir(operacoes, () -> b.transferirPara(c, dinheiro("1.00"))));
        adicionar(tarefas, 3, repetir(operacoes, () -> c.transferirPara(a, dinheiro("1.00"))));
        executarEmParalelo(tarefas);

        // Cada conta envia e recebe a mesma quantia: todas voltam ao saldo inicial.
        assertEquals(dinheiro("1000000.00"), a.getSaldo());
        assertEquals(dinheiro("1000000.00"), b.getSaldo());
        assertEquals(dinheiro("1000000.00"), c.getSaldo());
    }
}