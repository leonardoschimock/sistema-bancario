package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de CONCORRÊNCIA de {@link Conta}: várias threads operam sobre a MESMA conta
 * (o mesmo estado compartilhado) e o saldo final é comparado com o valor calculado
 * matematicamente de antemão.
 *
 * Como as threads são coordenadas (sem sleep):
 * - ExecutorService: pool com exatamente uma thread por tarefa, para que todas possam
 *   aguardar o sinal de largada ao mesmo tempo (um pool menor travaria no latch).
 * - CountDownLatch "prontas": o teste só dá a largada quando todas as threads já estão
 *   aguardando, o que aumenta a sobreposição entre elas.
 * - CountDownLatch "largada": libera todas as threads de uma vez.
 * - Future.get(timeout): espera o fim de cada tarefa, repassa qualquer exceção ocorrida
 *   na thread e transforma um possível travamento em falha do teste (em vez de ficar
 *   preso para sempre).
 *
 * Limite honesto: passar nestes testes é evidência, não prova. A garantia de correção
 * vem do raciocínio sobre o lock (ver Javadoc de {@link Conta}); os testes servem para
 * detectar a violação dele — e foram verificados contra uma Conta sem sincronização,
 * onde falham.
 */
class ContaConcorrenciaTest {

    private static final long TIMEOUT_SEGUNDOS = 60;

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static Conta novaConta(String nome, String saldoInicial) {
        return new Conta(new Cliente(nome), dinheiro(saldoInicial));
    }

    /** Repete a mesma operação {@code vezes} vezes, em UMA thread. */
    private static Runnable repetir(int vezes, Runnable operacao) {
        return () -> {
            for (int i = 0; i < vezes; i++) {
                operacao.run();
            }
        };
    }

    /** Adiciona {@code threads} tarefas idênticas à lista. */
    private static void adicionar(List<Runnable> tarefas, int threads, Runnable tarefa) {
        for (int i = 0; i < threads; i++) {
            tarefas.add(tarefa);
        }
    }

    /**
     * Executa todas as tarefas ao mesmo tempo (uma thread por tarefa) e só retorna quando
     * todas terminam. Falha se alguma tarefa lançar exceção ou se passar do tempo limite.
     */
    private static void executarEmParalelo(List<Runnable> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        try {
            CountDownLatch prontas = new CountDownLatch(tarefas.size());
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Object>> futuros = new ArrayList<>();
            for (Runnable tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    prontas.countDown();
                    largada.await();
                    tarefa.run();
                    return null;
                }));
            }
            assertTrue(prontas.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS),
                    "As threads não ficaram prontas a tempo.");
            largada.countDown();
            for (Future<Object> futuro : futuros) {
                futuro.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- Depósitos e saques na MESMA conta ----------

    @Test
    @DisplayName("Depósitos concorrentes na mesma conta não perdem atualizações")
    void depositosConcorrentesNaMesmaConta() throws Exception {
        int threads = 8;
        int operacoes = 20_000;
        Conta conta = novaConta("Ana", "0.00");

        List<Runnable> tarefas = new ArrayList<>();
        adicionar(tarefas, threads, repetir(operacoes, () -> conta.depositar(dinheiro("10.00"))));
        executarEmParalelo(tarefas);

        // esperado = 0.00 + 8 * 20.000 * 10.00
        assertEquals(dinheiro("1600000.00"), conta.getSaldo());
    }

    @Test
    @DisplayName("Saques concorrentes na mesma conta não perdem atualizações")
    void saquesConcorrentesNaMesmaConta() throws Exception {
        int threads = 8;
        int operacoes = 50_000;
        // Saldo inicial = exatamente o total que será sacado: todos os saques devem ser aceitos.
        Conta conta = novaConta("Bruno", "2000000.00");
        AtomicInteger recusados = new AtomicInteger();

        List<Runnable> tarefas = new ArrayList<>();
        adicionar(tarefas, threads, repetir(operacoes, () -> {
            try {
                conta.sacar(dinheiro("5.00"));
            } catch (SaldoInsuficienteException e) {
                recusados.incrementAndGet();
            }
        }));
        executarEmParalelo(tarefas);

        // esperado = 2.000.000,00 - 8 * 50.000 * 5,00 = 0,00 e nenhuma recusa
        assertEquals(0, recusados.get(), "Nenhum saque deveria ter sido recusado.");
        assertEquals(dinheiro("0.00"), conta.getSaldo());
    }

    @Test
    @DisplayName("Saques concorrentes com saldo limitado: só o que cabe no saldo é aceito (verificar e agir é atômico)")
    void saquesConcorrentesComSaldoLimitado() throws Exception {
        int rodadas = 3;
        int threads = 8;
        int tentativasPorThread = 50_000;
        // 2.000.000,00 / 10,00 = 200.000 saques possíveis, para 8 * 50.000 = 400.000 tentativas.
        // Sem atomicidade em "verificar saldo" + "debitar", mais de 200.000 saques passariam na
        // verificação, ou atualizações se perderiam e o saldo final não seria 0,00.
        // A carga é grande de propósito: com pouca carga, cada thread termina antes de a
        // seguinte começar e a corrida nem chega a acontecer (teste fraco).
        for (int rodada = 1; rodada <= rodadas; rodada++) {
            Conta conta = novaConta("Carla", "2000000.00");
            AtomicInteger aceitos = new AtomicInteger();
            AtomicInteger recusados = new AtomicInteger();

            List<Runnable> tarefas = new ArrayList<>();
            adicionar(tarefas, threads, repetir(tentativasPorThread, () -> {
                try {
                    conta.sacar(dinheiro("10.00"));
                    aceitos.incrementAndGet();
                } catch (SaldoInsuficienteException e) {
                    recusados.incrementAndGet();
                }
            }));
            executarEmParalelo(tarefas);

            assertEquals(200_000, aceitos.get(), "Saques aceitos incorretos na rodada " + rodada);
            assertEquals(threads * tentativasPorThread - 200_000, recusados.get(),
                    "Saques recusados incorretos na rodada " + rodada);
            assertEquals(dinheiro("0.00"), conta.getSaldo(), "Saldo incorreto na rodada " + rodada);
        }
    }

    @Test
    @DisplayName("Depósitos e saques concorrentes na mesma conta (mesma carga da demonstração histórica) dão o saldo esperado em todas as rodadas")
    void depositosESaquesConcorrentesRepetidosEmVariasRodadas() throws Exception {
        // Mesma carga de simulacao.didatico.DemonstracaoRaceCondition (o "antes"),
        // agora sobre a Conta real: 4 threads depositam, 4 threads sacam.
        int rodadas = 5;
        int operacoes = 50_000;

        for (int rodada = 1; rodada <= rodadas; rodada++) {
            Conta conta = novaConta("Diego", "1000000.00");

            List<Runnable> tarefas = new ArrayList<>();
            adicionar(tarefas, 4, repetir(operacoes, () -> conta.depositar(dinheiro("10.00"))));
            adicionar(tarefas, 4, repetir(operacoes, () -> conta.sacar(dinheiro("5.00"))));
            executarEmParalelo(tarefas);

            // esperado = 1.000.000,00 + 4*50.000*10,00 - 4*50.000*5,00 = 2.000.000,00
            assertEquals(dinheiro("2000000.00"), conta.getSaldo(), "Saldo incorreto na rodada " + rodada);
        }
    }

    // ---------- Guarda da transferência (limitação provisória) ----------

    @Test
    @DisplayName("Transferências concorrentes em sentidos opostos, junto com depósitos e saques, preservam os saldos e não travam")
    void transferenciasConcorrentesJuntoComDepositosESaques() throws Exception {
        // Mistura transferências com depósitos/saques nas MESMAS contas: se uma transferência
        // ficasse só parcialmente protegida (ex.: travando só uma das contas), um depósito/saque
        // concorrente numa delas se perderia; se houvesse deadlock, o timeout falharia o teste.
        // O teste específico de deadlock está em ContaTransferenciaConcorrenteTest.
        int operacoes = 50_000;
        Conta a = novaConta("Elisa", "10000000.00");
        Conta b = novaConta("Fabio", "10000000.00");

        List<Runnable> tarefas = new ArrayList<>();
        adicionar(tarefas, 4, repetir(operacoes, () -> a.transferirPara(b, dinheiro("2.00"))));   // A -> B: 400.000,00
        adicionar(tarefas, 4, repetir(operacoes, () -> b.transferirPara(a, dinheiro("1.00"))));   // B -> A: 200.000,00
        adicionar(tarefas, 2, repetir(operacoes, () -> a.depositar(dinheiro("1.00"))));           // +100.000,00 em A
        adicionar(tarefas, 2, repetir(operacoes, () -> b.sacar(dinheiro("1.00"))));               // -100.000,00 em B
        executarEmParalelo(tarefas);

        // A = 10.000.000 - 400.000 + 200.000 + 100.000 =  9.900.000,00
        // B = 10.000.000 + 400.000 - 200.000 - 100.000 = 10.100.000,00
        assertEquals(dinheiro("9900000.00"), a.getSaldo());
        assertEquals(dinheiro("10100000.00"), b.getSaldo());
    }
}