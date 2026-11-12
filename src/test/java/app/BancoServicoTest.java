package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import domain.SaldoInsuficienteException;
import domain.TipoTransacao;
import domain.Transacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.ConexaoFactory;
import persistence.ContaRepository;
import persistence.PersistenciaException;
import persistence.TransacaoRepository;

/**
 * Testes UNITÁRIOS de {@link BancoServico}: não usam banco. Os repositories são substituídos por
 * subclasses escritas à mão que guardam tudo em memória (nenhuma conexão é aberta). Sem Mockito.
 *
 * <p>Os repositories falsos imitam o banco no que importa para o serviço: o INSERT define o id da
 * conta, {@code buscarPorId} devolve uma instância NOVA a cada chamada (como o banco real) e o
 * saldo gravado é só um instantâneo.
 */
class BancoServicoTest {

    private static final Duration PRAZO = Duration.ofSeconds(30);

    @TempDir
    Path pasta;

    private ContasFalsas contas;
    private TransacoesFalsas transacoes;
    private BancoServico servico;

    @BeforeEach
    void preparar() {
        contas = new ContasFalsas();
        transacoes = new TransacoesFalsas();
        servico = new BancoServico(contas, transacoes);
    }

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    // ------------------------------------------------------------------ fakes

    private static ConexaoFactory conexaoInutil() {
        return new ConexaoFactory("jdbc:mariadb://host-inexistente/banco", "usuario", "");
    }

    /** Banco de contas em memória. Qualquer chamada que tentasse abrir conexão falharia. */
    private static class ContasFalsas extends ContaRepository {
        private final Map<Long, BigDecimal> saldos = new ConcurrentHashMap<>();
        private final Map<Long, String> titulares = new ConcurrentHashMap<>();
        private final AtomicLong proximoId = new AtomicLong(100);
        final AtomicInteger salvamentos = new AtomicInteger();
        final AtomicInteger buscas = new AtomicInteger();
        volatile RuntimeException falhaAoSalvar;

        ContasFalsas() {
            super(conexaoInutil());
        }

        /** Simula uma linha que já existia no banco antes de o serviço abrir. */
        long linhaExistente(String titular, String saldo) {
            long id = proximoId.incrementAndGet();
            titulares.put(id, titular);
            saldos.put(id, d(saldo));
            return id;
        }

        BigDecimal saldoGravado(long id) {
            return saldos.get(id);
        }

        @Override
        public void salvar(Conta conta) {
            salvamentos.incrementAndGet();
            if (falhaAoSalvar != null) {
                throw falhaAoSalvar;
            }
            Long id = conta.getIdPersistencia();
            if (id == null) {
                id = proximoId.incrementAndGet();
                conta.definirIdPersistencia(id);
                titulares.put(id, conta.getTitular().getNome());
            }
            saldos.put(id, conta.getSaldo());
        }

        @Override
        public Optional<Conta> buscarPorId(long id) {
            buscas.incrementAndGet();
            BigDecimal saldo = saldos.get(id);
            if (saldo == null) {
                return Optional.empty();
            }
            // Instância NOVA a cada busca, como o banco real.
            return Optional.of(Conta.restaurar(id, new Cliente(titulares.get(id)), saldo));
        }

        @Override
        public List<Conta> listarTodas() {
            List<Long> ids = new ArrayList<>(saldos.keySet());
            Collections.sort(ids);
            List<Conta> todas = new ArrayList<>();
            for (long id : ids) {
                todas.add(Conta.restaurar(id, new Cliente(titulares.get(id)), saldos.get(id)));
            }
            return todas;
        }
    }

    private record Linha(long contaId, Long destinoId, Transacao transacao) { }

    /** Banco de transações em memória; monta o extrato com a mesma regra de sentido do real. */
    private static class TransacoesFalsas extends TransacaoRepository {
        final List<Linha> linhas = Collections.synchronizedList(new ArrayList<>());
        volatile RuntimeException falhaAoGravar;

        TransacoesFalsas() {
            super(conexaoInutil());
        }

        @Override
        public void salvar(Conta conta, Transacao transacao) {
            if (falhaAoGravar != null) {
                throw falhaAoGravar;
            }
            linhas.add(new Linha(conta.getIdPersistencia(), null, transacao));
        }

        @Override
        public void salvarTransferencia(Conta origem, Conta destino, Transacao transacao) {
            if (falhaAoGravar != null) {
                throw falhaAoGravar;
            }
            linhas.add(new Linha(origem.getIdPersistencia(), destino.getIdPersistencia(), transacao));
        }

        @Override
        public List<LancamentoExtrato> buscarExtrato(Conta conta) {
            long id = conta.getIdPersistencia();
            List<Linha> copia;
            synchronized (linhas) {
                copia = new ArrayList<>(linhas);
            }
            List<LancamentoExtrato> extrato = new ArrayList<>();
            for (Linha l : copia) {
                switch (l.transacao().getTipo()) {
                    case DEPOSITO -> {
                        if (l.contaId() == id) {
                            extrato.add(new LancamentoExtrato(l.transacao(), Sentido.ENTRADA, null));
                        }
                    }
                    case SAQUE -> {
                        if (l.contaId() == id) {
                            extrato.add(new LancamentoExtrato(l.transacao(), Sentido.SAIDA, null));
                        }
                    }
                    case TRANSFERENCIA -> {
                        if (l.contaId() == id) {
                            extrato.add(new LancamentoExtrato(l.transacao(), Sentido.SAIDA, l.destinoId()));
                        } else if (l.destinoId() != null && l.destinoId() == id) {
                            extrato.add(new LancamentoExtrato(l.transacao(), Sentido.ENTRADA, l.contaId()));
                        }
                    }
                    default -> throw new IllegalStateException("tipo não tratado");
                }
            }
            return extrato;
        }
    }

    // --------------------------------------------------- operações aceitas

    @Test
    @DisplayName("criarConta grava a conta, devolve o id gerado e o saldo fica consultável")
    void criarContaGravaEDevolveResumo() {
        ContaResumo resumo = servico.criarConta("  Ana  ", d("1000.00"));

        assertEquals("Ana", resumo.titular());
        assertEquals(0, d("1000.00").compareTo(resumo.saldo()));
        assertEquals(0, d("1000.00").compareTo(contas.saldoGravado(resumo.id())));
        assertEquals(0, d("1000.00").compareTo(servico.consultarSaldo(resumo.id())));
        assertTrue(transacoes.linhas.isEmpty(), "saldo inicial não gera lançamento");
    }

    @Test
    @DisplayName("Depósito aceito grava a Transacao e atualiza o saldo gravado")
    void depositoAceitoGravaTransacao() {
        long ana = servico.criarConta("Ana", d("1000.00")).id();

        Transacao t = servico.depositar(ana, d("200.00"));

        assertEquals(TipoTransacao.DEPOSITO, t.getTipo());
        assertEquals(0, d("200.00").compareTo(t.getValor()));
        assertEquals(1, transacoes.linhas.size());
        assertEquals(ana, transacoes.linhas.get(0).contaId());
        assertEquals(0, d("1200.00").compareTo(servico.consultarSaldo(ana)));
        assertEquals(0, d("1200.00").compareTo(contas.saldoGravado(ana)));
    }

    @Test
    @DisplayName("Saque aceito grava a Transacao e atualiza o saldo gravado")
    void saqueAceitoGravaTransacao() {
        long ana = servico.criarConta("Ana", d("1000.00")).id();

        Transacao t = servico.sacar(ana, d("50.00"));

        assertEquals(TipoTransacao.SAQUE, t.getTipo());
        assertEquals(1, transacoes.linhas.size());
        assertEquals(0, d("950.00").compareTo(servico.consultarSaldo(ana)));
        assertEquals(0, d("950.00").compareTo(contas.saldoGravado(ana)));
    }

    @Test
    @DisplayName("Transferência aceita grava UMA Transacao (origem e destino) e atualiza as duas contas")
    void transferenciaAceitaGravaUmaTransacao() {
        long ana = servico.criarConta("Ana", d("1000.00")).id();
        long bia = servico.criarConta("Bia", d("500.00")).id();

        Transacao t = servico.transferir(ana, bia, d("80.00"));

        assertEquals(TipoTransacao.TRANSFERENCIA, t.getTipo());
        assertEquals(1, transacoes.linhas.size());
        assertEquals(ana, transacoes.linhas.get(0).contaId());
        assertEquals(Long.valueOf(bia), transacoes.linhas.get(0).destinoId());
        assertEquals(0, d("920.00").compareTo(contas.saldoGravado(ana)));
        assertEquals(0, d("580.00").compareTo(contas.saldoGravado(bia)));
    }

    // --------------------------------------------------- operações recusadas

    @Test
    @DisplayName("Saque recusado por saldo insuficiente NÃO grava Transacao e NÃO altera o saldo")
    void saqueRecusadoNaoGravaNada() {
        long ana = servico.criarConta("Ana", d("100.00")).id();
        int salvamentosAntes = contas.salvamentos.get();

        assertThrows(SaldoInsuficienteException.class, () -> servico.sacar(ana, d("100.01")));

        assertTrue(transacoes.linhas.isEmpty());
        assertEquals(salvamentosAntes, contas.salvamentos.get(), "nenhum UPDATE de saldo");
        assertEquals(0, d("100.00").compareTo(servico.consultarSaldo(ana)));
        assertEquals(0, d("100.00").compareTo(contas.saldoGravado(ana)));
    }

    @Test
    @DisplayName("Transferência recusada NÃO grava Transacao e NÃO altera nenhuma das contas")
    void transferenciaRecusadaNaoGravaNada() {
        long ana = servico.criarConta("Ana", d("50.00")).id();
        long bia = servico.criarConta("Bia", d("10.00")).id();
        int salvamentosAntes = contas.salvamentos.get();

        assertThrows(SaldoInsuficienteException.class, () -> servico.transferir(ana, bia, d("50.01")));

        assertTrue(transacoes.linhas.isEmpty());
        assertEquals(salvamentosAntes, contas.salvamentos.get());
        assertEquals(0, d("50.00").compareTo(servico.consultarSaldo(ana)));
        assertEquals(0, d("10.00").compareTo(servico.consultarSaldo(bia)));
    }

    @Test
    @DisplayName("Valor inválido (zero, negativo, 3 casas) é recusado sem gravar nada")
    void valorInvalidoNaoGravaNada() {
        long ana = servico.criarConta("Ana", d("100.00")).id();
        int salvamentosAntes = contas.salvamentos.get();

        assertThrows(IllegalArgumentException.class, () -> servico.depositar(ana, d("0")));
        assertThrows(IllegalArgumentException.class, () -> servico.depositar(ana, d("-5.00")));
        assertThrows(IllegalArgumentException.class, () -> servico.sacar(ana, d("1.001")));
        assertThrows(IllegalArgumentException.class, () -> servico.depositar(ana, null));

        assertTrue(transacoes.linhas.isEmpty());
        assertEquals(salvamentosAntes, contas.salvamentos.get());
        assertEquals(0, d("100.00").compareTo(servico.consultarSaldo(ana)));
    }

    @Test
    @DisplayName("Transferir para a própria conta é recusado sem gravar nada")
    void transferirParaSiMesmaRecusado() {
        long ana = servico.criarConta("Ana", d("100.00")).id();

        assertThrows(IllegalArgumentException.class, () -> servico.transferir(ana, ana, d("10.00")));

        assertTrue(transacoes.linhas.isEmpty());
        assertEquals(0, d("100.00").compareTo(servico.consultarSaldo(ana)));
    }

    @Test
    @DisplayName("Conta inexistente (origem ou destino) lança IllegalArgumentException e nada muda")
    void contaInexistente() {
        long ana = servico.criarConta("Ana", d("100.00")).id();

        assertThrows(IllegalArgumentException.class, () -> servico.depositar(999_999L, d("1.00")));
        assertThrows(IllegalArgumentException.class, () -> servico.consultarSaldo(999_999L));
        assertThrows(IllegalArgumentException.class, () -> servico.transferir(ana, 999_999L, d("1.00")));

        assertTrue(transacoes.linhas.isEmpty());
        assertEquals(0, d("100.00").compareTo(servico.consultarSaldo(ana)));
    }

    // ------------------------------------------- instância única e falhas

    @Test
    @DisplayName("Conta lida do banco é carregada UMA vez: todas as chamadas usam a mesma instância")
    void mesmaInstanciaParaTodasAsChamadas() {
        long id = contas.linhaExistente("Carla", "100.00");

        servico.depositar(id, d("10.00"));
        servico.sacar(id, d("5.00"));
        BigDecimal saldo = servico.consultarSaldo(id);

        assertEquals(1, contas.buscas.get(), "uma busca no banco, não uma por operação");
        assertEquals(0, d("105.00").compareTo(saldo));
    }

    @Test
    @DisplayName("Falha ao gravar propaga PersistenciaException (com a causa); o saldo em memória é o correto")
    void falhaDeGravacaoPropaga() {
        long ana = servico.criarConta("Ana", d("100.00")).id();
        PersistenciaException causa = new PersistenciaException("banco fora do ar");
        transacoes.falhaAoGravar = causa;

        PersistenciaException lancada = assertThrows(PersistenciaException.class,
                () -> servico.depositar(ana, d("25.00")));

        assertTrue(lancada.getCause() == causa, "a causa original é preservada");
        // A operação foi aceita pela Conta: memória é a fonte de verdade; a linha do banco ficou defasada.
        assertEquals(0, d("125.00").compareTo(servico.consultarSaldo(ana)));
        assertEquals(0, d("100.00").compareTo(contas.saldoGravado(ana)));
        assertEquals(0, d("125.00").compareTo(servico.listarContas().get(0).saldo()),
                "listarContas mostra o saldo em memória das contas abertas");
    }

    // --------------------------------------------------------- concorrência

    @Test
    @DisplayName("Depósitos concorrentes pelo serviço, na mesma conta: saldo exato e uma Transacao por depósito")
    void depositosConcorrentesPeloServico() throws Exception {
        long ana = servico.criarConta("Ana", d("1000.00")).id();
        int threads = 8;
        int operacoes = 500;

        executarEmParalelo(threads, () -> {
            for (int i = 0; i < operacoes; i++) {
                servico.depositar(ana, d("1.00"));
            }
        });

        BigDecimal esperado = d("1000.00").add(BigDecimal.valueOf((long) threads * operacoes));
        assertEquals(0, esperado.compareTo(servico.consultarSaldo(ana)));
        assertEquals(threads * operacoes, transacoes.linhas.size());
    }

    @Test
    @DisplayName("Transferências concorrentes em sentidos opostos pelo serviço: sem deadlock e total preservado")
    void transferenciasOpostasPeloServico() throws Exception {
        long ana = servico.criarConta("Ana", d("10000.00")).id();
        long bia = servico.criarConta("Bia", d("10000.00")).id();
        int porSentido = 4;
        int operacoes = 250;

        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < porSentido; i++) {
            tarefas.add(() -> repetirTransferencia(ana, bia, operacoes));
            tarefas.add(() -> repetirTransferencia(bia, ana, operacoes));
        }
        executarTarefas(tarefas);

        BigDecimal total = servico.consultarSaldo(ana).add(servico.consultarSaldo(bia));
        assertEquals(0, d("20000.00").compareTo(total));
        // Mesma quantidade nos dois sentidos, mesmo valor: cada conta volta ao saldo inicial.
        assertEquals(0, d("10000.00").compareTo(servico.consultarSaldo(ana)));
        assertEquals(2 * porSentido * operacoes, transacoes.linhas.size());
    }

    private Void repetirTransferencia(long origem, long destino, int vezes) {
        for (int i = 0; i < vezes; i++) {
            servico.transferir(origem, destino, d("1.00"));
        }
        return null;
    }

    // ----------------------------------------------------------------- extrato

    @Test
    @DisplayName("Extrato do fluxo completo: tipos, sentidos e conta relacionada corretos nas duas contas")
    void extratoDoFluxoCompleto() {
        long ana = servico.criarConta("Ana", d("1000.00")).id();
        long bia = servico.criarConta("Bia", d("500.00")).id();
        servico.depositar(ana, d("200.00"));
        servico.sacar(ana, d("50.00"));
        servico.transferir(ana, bia, d("80.00"));

        List<LancamentoExtrato> deAna = servico.consultarExtrato(ana);
        List<LancamentoExtrato> deBia = servico.consultarExtrato(bia);

        assertEquals(3, deAna.size());
        assertEquals(TipoTransacao.DEPOSITO, deAna.get(0).transacao().getTipo());
        assertEquals(Sentido.ENTRADA, deAna.get(0).sentido());
        assertEquals(TipoTransacao.SAQUE, deAna.get(1).transacao().getTipo());
        assertEquals(Sentido.SAIDA, deAna.get(1).sentido());
        assertEquals(Sentido.SAIDA, deAna.get(2).sentido());
        assertEquals(Long.valueOf(bia), deAna.get(2).contaRelacionadaId());

        assertEquals(1, deBia.size());
        assertEquals(Sentido.ENTRADA, deBia.get(0).sentido());
        assertEquals(Long.valueOf(ana), deBia.get(0).contaRelacionadaId());
    }

    @Test
    @DisplayName("Exportação .txt a partir do fluxo completo (operações -> extrato -> arquivo)")
    void exportacaoDoFluxoCompleto() throws IOException {
        long ana = servico.criarConta("Ana Exemplo", d("1000.00")).id();
        long bia = servico.criarConta("Bia", d("500.00")).id();
        servico.depositar(ana, d("200.00"));
        servico.sacar(ana, d("50.00"));
        servico.transferir(ana, bia, d("80.00"));

        Path destino = pasta.resolve("saida").resolve("extrato.txt");
        Path gravado = servico.exportarExtrato(ana, destino);

        assertTrue(Files.exists(gravado));
        String texto = Files.readString(gravado, StandardCharsets.UTF_8);
        assertTrue(texto.contains("EXTRATO DE CONTA"));
        assertTrue(texto.contains("Ana Exemplo"));
        assertTrue(texto.contains("Depósito"));
        assertTrue(texto.contains("+ R$ 200,00"));
        assertTrue(texto.contains("Saque"));
        assertTrue(texto.contains("- R$ 50,00"));
        assertTrue(texto.contains("Transferência enviada para conta #" + bia));
        assertTrue(texto.contains("- R$ 80,00"));
        assertTrue(texto.contains("R$ 1.070,00"), "saldo atual: 1000 + 200 - 50 - 80");
    }

    @Test
    @DisplayName("Exportar com extensão diferente de .txt é recusado")
    void exportacaoExigeTxt() {
        long ana = servico.criarConta("Ana", d("1.00")).id();

        assertThrows(IllegalArgumentException.class,
                () -> servico.exportarExtrato(ana, pasta.resolve("extrato.pdf")));
        assertFalse(Files.exists(pasta.resolve("extrato.pdf")));
    }

    // --------------------------------------------------------------- simulação

    @Test
    @DisplayName("Simulação sem persistir não grava nada e é consistente")
    void simulacaoSemPersistirNaoGrava() throws InterruptedException {
        ExecucaoSimulacao execucao = servico.executarSimulacao(
                new ParametrosSimulacao(7, 4, 200, 3, PRAZO, false));

        assertTrue(execucao.resultado().consistente(), String.valueOf(execucao.resultado().problemas()));
        assertTrue(execucao.contasGravadas().isEmpty());
        assertEquals(0, contas.salvamentos.get());
        assertTrue(transacoes.linhas.isEmpty());
    }

    @Test
    @DisplayName("Simulação com persistir grava as contas só ao final, com o saldo obtido, e mantém a mesma instância")
    void simulacaoComPersistirGravaContas() throws InterruptedException {
        ExecucaoSimulacao execucao = servico.executarSimulacao(
                new ParametrosSimulacao(7, 4, 200, 3, PRAZO, true));

        assertTrue(execucao.resultado().consistente(), String.valueOf(execucao.resultado().problemas()));
        List<ContaResumo> gravadas = execucao.contasGravadas();
        assertEquals(3, gravadas.size());
        assertEquals(3, contas.salvamentos.get(), "um INSERT por conta, depois da simulação");
        assertTrue(transacoes.linhas.isEmpty(), "operações individuais não são gravadas");
        for (int i = 0; i < gravadas.size(); i++) {
            BigDecimal obtido = execucao.resultado().saldos().get(i).obtido();
            assertEquals(0, obtido.compareTo(contas.saldoGravado(gravadas.get(i).id())));
        }

        // A conta da simulação ficou aberta: nova operação usa a MESMA instância (sem buscar no banco).
        long id = gravadas.get(0).id();
        BigDecimal antes = servico.consultarSaldo(id);
        servico.depositar(id, d("1.00"));
        assertEquals(0, antes.add(d("1.00")).compareTo(servico.consultarSaldo(id)));
        assertEquals(0, contas.buscas.get());
    }

    @Test
    @DisplayName("listarContas devolve as contas do banco e usa a instância aberta quando existe")
    void listarContas() {
        long existente = contas.linhaExistente("Carla", "300.00");
        long criada = servico.criarConta("Ana", d("100.00")).id();

        List<ContaResumo> lista = servico.listarContas();

        assertEquals(2, lista.size());
        assertEquals(existente, lista.get(0).id());
        assertEquals("Carla", lista.get(0).titular());
        assertEquals(criada, lista.get(1).id());
        assertNotNull(lista.get(1).saldo());
    }

    @Test
    @DisplayName("Parâmetros e argumentos nulos são recusados")
    void argumentosNulos() {
        assertThrows(IllegalArgumentException.class, () -> new BancoServico(null, transacoes));
        assertThrows(IllegalArgumentException.class, () -> new BancoServico(contas, null));
        assertThrows(IllegalArgumentException.class, () -> servico.executarSimulacao(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ParametrosSimulacao(1, 1, 1, 2, null, false));
        assertThrows(IllegalArgumentException.class, () -> servico.criarConta(" ", d("1.00")));
    }

    // ------------------------------------------------------------- utilidades

    /** Roda a mesma tarefa em N threads, largando todas juntas, e propaga qualquer exceção. */
    private static void executarEmParalelo(int threads, Runnable tarefa) throws Exception {
        List<Callable<Void>> tarefas = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tarefas.add(() -> {
                tarefa.run();
                return null;
            });
        }
        executarTarefas(tarefas);
    }

    private static void executarTarefas(List<Callable<Void>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch prontas = new CountDownLatch(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Void>> futuros = new ArrayList<>();
            for (Callable<Void> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    prontas.countDown();
                    largada.await();
                    return tarefa.call();
                }));
            }
            assertTrue(prontas.await(PRAZO.toSeconds(), TimeUnit.SECONDS), "threads não ficaram prontas");
            largada.countDown();
            for (Future<Void> futuro : futuros) {
                try {
                    futuro.get(PRAZO.toSeconds(), TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    throw new AssertionError("Tarefas não terminaram no prazo (possível deadlock).", e);
                } catch (ExecutionException e) {
                    throw new AssertionError("Uma thread falhou: " + e.getCause(), e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
