package sim.didatico;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * *** DEMONSTRAÇÃO DIDÁTICA (Semana 2) — expõe a race condition ***
 *
 * Várias threads fazem depósitos e saques na MESMA {@link ContaInsegura}.
 * Como as quantidades são conhecidas, o saldo esperado é calculado antes:
 *
 *   esperado = saldoInicial + (threadsDeposito * ops * valorDeposito)
 *                           - (threadsSaque    * ops * valorSaque)
 *
 * O resultado observado pode divergir do esperado, mas isso NÃO é garantido em
 * toda execução: depende do escalonamento das threads, do número de núcleos e da JVM.
 *
 * Mecanismo de coordenação: um CountDownLatch ("portão de largada") faz todas as threads
 * começarem ao mesmo tempo. Ele só aumenta a chance de sobreposição; não altera nenhum
 * cálculo e não há sleep nem resultado simulado.
 *
 * Controle sequencial: antes das rodadas concorrentes, a MESMA carga de trabalho é executada
 * em uma única thread. O resultado deve ser igual ao esperado; isso comprova que a fórmula
 * está certa e que qualquer divergência posterior vem somente da concorrência.
 *
 * Saques recusados: se algum saque fosse recusado por "saldo insuficiente", o saldo esperado
 * deixaria de valer. Por isso as recusas são contadas e exibidas (devem ser 0).
 *
 * Uso: DemonstracaoRaceCondition [rodadas] [operacoesPorThread]
 */
public class DemonstracaoRaceCondition {

    private static final int THREADS_DEPOSITO = 4;
    private static final int THREADS_SAQUE = 4;
    private static final BigDecimal VALOR_DEPOSITO = new BigDecimal("10.00");
    private static final BigDecimal VALOR_SAQUE = new BigDecimal("5.00");
    // Saldo inicial alto o bastante para que nenhum saque seja recusado por saldo insuficiente:
    // assim o saldo esperado é sempre calculável de antemão.
    private static final BigDecimal SALDO_INICIAL = new BigDecimal("1000000.00");

    public static void main(String[] args) throws InterruptedException {
        int rodadas = lerArgumento(args, 0, 5);
        int operacoes = lerArgumento(args, 1, 50_000);

        BigDecimal esperado = SALDO_INICIAL
                .add(VALOR_DEPOSITO.multiply(BigDecimal.valueOf((long) THREADS_DEPOSITO * operacoes)))
                .subtract(VALOR_SAQUE.multiply(BigDecimal.valueOf((long) THREADS_SAQUE * operacoes)));

        System.out.println("=== Demonstração de race condition (ContaInsegura) ===");
        System.out.println("Java                : " + System.getProperty("java.version"));
        System.out.println("Núcleos disponíveis : " + Runtime.getRuntime().availableProcessors());
        System.out.println("Threads de depósito : " + THREADS_DEPOSITO + " x " + operacoes + " x R$ " + VALOR_DEPOSITO);
        System.out.println("Threads de saque    : " + THREADS_SAQUE + " x " + operacoes + " x R$ " + VALOR_SAQUE);
        System.out.println("Saldo inicial       : R$ " + SALDO_INICIAL);
        System.out.println("Saldo ESPERADO      : R$ " + esperado);
        System.out.println();

        BigDecimal controle = executarControleSequencial(operacoes);
        System.out.printf("Controle sequencial (1 thread): esperado=R$ %s | observado=R$ %s | %s%n%n",
                esperado, controle, controle.compareTo(esperado) == 0 ? "OK" : "FÓRMULA INCONSISTENTE");

        int comDivergencia = 0;
        for (int rodada = 1; rodada <= rodadas; rodada++) {
            BigDecimal observado = executarRodada(operacoes);
            BigDecimal diferenca = observado.subtract(esperado);
            boolean divergiu = diferenca.signum() != 0;
            if (divergiu) {
                comDivergencia++;
            }
            System.out.printf("Rodada %d: esperado=R$ %s | observado=R$ %s | diferença=R$ %s | %s%n",
                    rodada, esperado, observado, diferenca, divergiu ? "DIVERGIU" : "igual (nesta execução)");
        }

        System.out.println();
        System.out.println("Rodadas com divergência: " + comDivergencia + " de " + rodadas);
    }

    /** Mesma carga das rodadas concorrentes, mas em uma única thread (sem concorrência). */
    private static BigDecimal executarControleSequencial(int operacoes) {
        ContaInsegura conta = new ContaInsegura(SALDO_INICIAL);
        for (int i = 0; i < THREADS_DEPOSITO * operacoes; i++) {
            conta.depositar(VALOR_DEPOSITO);
        }
        for (int i = 0; i < THREADS_SAQUE * operacoes; i++) {
            conta.sacar(VALOR_SAQUE);
        }
        return conta.getSaldo();
    }

    private static BigDecimal executarRodada(int operacoes) throws InterruptedException {
        ContaInsegura conta = new ContaInsegura(SALDO_INICIAL);
        AtomicInteger saquesRecusados = new AtomicInteger();   // AtomicInteger: o contador em si é seguro
        CountDownLatch largada = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < THREADS_DEPOSITO; i++) {
            threads.add(criarThread("deposito-" + i, largada, operacoes, () -> conta.depositar(VALOR_DEPOSITO)));
        }
        for (int i = 0; i < THREADS_SAQUE; i++) {
            threads.add(criarThread("saque-" + i, largada, operacoes, () -> {
                try {
                    conta.sacar(VALOR_SAQUE);
                } catch (IllegalStateException e) {
                    saquesRecusados.incrementAndGet();
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }
        largada.countDown();          // libera todas as threads de uma vez
        for (Thread t : threads) {
            t.join();                 // aguarda o fim antes de ler o saldo
        }
        if (saquesRecusados.get() > 0) {
            System.out.println("  AVISO: " + saquesRecusados.get()
                    + " saque(s) recusado(s); o saldo esperado não vale para esta rodada.");
        }
        return conta.getSaldo();
    }

    private static int lerArgumento(String[] args, int posicao, int padrao) {
        if (args.length <= posicao) {
            return padrao;
        }
        try {
            int valor = Integer.parseInt(args[posicao]);
            if (valor > 0) {
                return valor;
            }
        } catch (NumberFormatException ignorado) {
            // cai na mensagem abaixo
        }
        throw new IllegalArgumentException("Argumento " + (posicao + 1) + " inválido: '" + args[posicao]
                + "'. Informe um inteiro positivo. Uso: DemonstracaoRaceCondition [rodadas] [operacoesPorThread]");
    }

    private static Thread criarThread(String nome, CountDownLatch largada, int operacoes, Runnable operacao) {
        return new Thread(() -> {
            try {
                largada.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            for (int i = 0; i < operacoes; i++) {
                operacao.run();
            }
        }, nome);
    }
}