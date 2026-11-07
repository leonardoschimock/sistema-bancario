package persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import domain.TipoTransacao;
import domain.Transacao;
import app.ExtratoExportador;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TESTES DE INTEGRAÇÃO: precisam de um servidor MariaDB com o schema.sql aplicado no banco
 * de teste. Não rodam no "mvn test" comum; use "mvn test -Pintegracao".
 */
@Tag("integracao")
class TransacaoRepositoryIntegracaoTest {

    private ContaRepository contas;
    private TransacaoRepository transacoes;

    @BeforeEach
    void preparar() {
        ConexaoFactory conexoes = BancoDeTeste.conexoes();
        contas = new ContaRepository(conexoes);
        transacoes = new TransacaoRepository(conexoes);
    }

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    /** DATETIME(6) guarda microssegundos: truncar evita falso negativo na comparação. */
    private static LocalDateTime agora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Conta contaPersistida(String nome) {
        Conta conta = new Conta(new Cliente(nome), dinheiro("1000.00"));
        contas.salvar(conta);
        return conta;
    }

    @Test
    @DisplayName("Transação salva é recuperada com tipo, valor e horário iguais")
    void salvaERecuperaTransacao() {
        Conta conta = contaPersistida("Ana");
        LocalDateTime horario = agora();
        transacoes.salvar(conta, new Transacao(TipoTransacao.DEPOSITO, dinheiro("150.75"), horario));

        List<Transacao> extrato = transacoes.buscarPorConta(conta);

        assertEquals(1, extrato.size());
        assertEquals(TipoTransacao.DEPOSITO, extrato.get(0).getTipo());
        assertEquals(0, dinheiro("150.75").compareTo(extrato.get(0).getValor()));
        assertEquals(horario, extrato.get(0).getHorario());
    }

    @Test
    @DisplayName("Extrato vem em ordem cronológica, mesmo gravado fora de ordem")
    void extratoEmOrdemCronologica() {
        Conta conta = contaPersistida("Bruno");
        LocalDateTime base = agora();
        transacoes.salvar(conta, new Transacao(TipoTransacao.SAQUE, dinheiro("3.00"), base.plusSeconds(20)));
        transacoes.salvar(conta, new Transacao(TipoTransacao.DEPOSITO, dinheiro("1.00"), base));
        transacoes.salvar(conta, new Transacao(TipoTransacao.DEPOSITO, dinheiro("2.00"), base.plusSeconds(10)));

        List<Transacao> extrato = transacoes.buscarPorConta(conta);

        assertEquals(3, extrato.size());
        assertEquals(0, dinheiro("1.00").compareTo(extrato.get(0).getValor()));
        assertEquals(0, dinheiro("2.00").compareTo(extrato.get(1).getValor()));
        assertEquals(0, dinheiro("3.00").compareTo(extrato.get(2).getValor()));
    }

    @Test
    @DisplayName("Transferência aparece no extrato da origem e do destino, em uma linha só")
    void transferenciaApareceNasDuasContas() {
        Conta origem = contaPersistida("Carla");
        Conta destino = contaPersistida("Diego");
        transacoes.salvarTransferencia(origem, destino,
                new Transacao(TipoTransacao.TRANSFERENCIA, dinheiro("25.00"), agora()));

        List<Transacao> extratoOrigem = transacoes.buscarPorConta(origem);
        List<Transacao> extratoDestino = transacoes.buscarPorConta(destino);

        assertEquals(1, extratoOrigem.size());
        assertEquals(1, extratoDestino.size());
        assertEquals(TipoTransacao.TRANSFERENCIA, extratoOrigem.get(0).getTipo());
        assertEquals(TipoTransacao.TRANSFERENCIA, extratoDestino.get(0).getTipo());
    }

    @Test
    @DisplayName("Transações de outras contas não aparecem no extrato")
    void extratoIsoladoPorConta() {
        Conta a = contaPersistida("Elisa");
        Conta b = contaPersistida("Fábio");
        transacoes.salvar(a, new Transacao(TipoTransacao.DEPOSITO, dinheiro("10.00"), agora()));
        transacoes.salvar(b, new Transacao(TipoTransacao.SAQUE, dinheiro("5.00"), agora()));

        assertEquals(1, transacoes.buscarPorConta(a).size());
        assertEquals(TipoTransacao.DEPOSITO, transacoes.buscarPorConta(a).get(0).getTipo());
        assertEquals(1, transacoes.buscarPorConta(b).size());
        assertEquals(TipoTransacao.SAQUE, transacoes.buscarPorConta(b).get(0).getTipo());
    }

    @Test
    @DisplayName("Conta sem transações tem extrato vazio")
    void contaSemTransacoes() {
        Conta conta = contaPersistida("Gabi");

        assertTrue(transacoes.buscarPorConta(conta).isEmpty());
    }

    @Test
    @DisplayName("Muitas transações são todas gravadas e recuperadas")
    void muitasTransacoes() {
        Conta conta = contaPersistida("Heitor");
        LocalDateTime base = agora();
        for (int i = 0; i < 100; i++) {
            transacoes.salvar(conta,
                    new Transacao(TipoTransacao.DEPOSITO, dinheiro("1.00"), base.plusSeconds(i)));
        }

        assertEquals(100, transacoes.buscarPorConta(conta).size());
    }

    @Test
    @DisplayName("Gravar transação de conta não persistida lança IllegalStateException")
    void contaNaoPersistida() {
        Conta nova = new Conta(new Cliente("Iara"), dinheiro("1.00"));
        Transacao t = new Transacao(TipoTransacao.DEPOSITO, dinheiro("1.00"), agora());

        assertThrows(IllegalStateException.class, () -> transacoes.salvar(nova, t));
        assertThrows(IllegalStateException.class, () -> transacoes.buscarPorConta(nova));
    }

    @Test
    @DisplayName("Tipo incompatível com o método é recusado")
    void tipoIncompativel() {
        Conta a = contaPersistida("João");
        Conta b = contaPersistida("Karina");
        Transacao transferencia = new Transacao(TipoTransacao.TRANSFERENCIA, dinheiro("1.00"), agora());
        Transacao deposito = new Transacao(TipoTransacao.DEPOSITO, dinheiro("1.00"), agora());

        assertThrows(IllegalArgumentException.class, () -> transacoes.salvar(a, transferencia));
        assertThrows(IllegalArgumentException.class, () -> transacoes.salvarTransferencia(a, b, deposito));
        assertThrows(IllegalArgumentException.class, () -> transacoes.salvarTransferencia(a, a, transferencia));
    }

    @Test
    @DisplayName("Valor com mais de 2 casas decimais é recusado, não arredondado")
    void valorComTresCasasRecusado() {
        Conta conta = contaPersistida("Lia");
        Transacao t = new Transacao(TipoTransacao.DEPOSITO, dinheiro("1.005"), agora());

        assertThrows(IllegalArgumentException.class, () -> transacoes.salvar(conta, t));
        assertTrue(transacoes.buscarPorConta(conta).isEmpty());
    }

    @Test
    @DisplayName("Transação de conta inexistente no banco viola a chave estrangeira (PersistenciaException)")
    void chaveEstrangeiraViolada() {
        Conta fantasma = Conta.restaurar(Long.MAX_VALUE, new Cliente("Fantasma"), dinheiro("1.00"));
        Transacao t = new Transacao(TipoTransacao.DEPOSITO, dinheiro("1.00"), agora());

        assertThrows(PersistenciaException.class, () -> transacoes.salvar(fantasma, t));
    }

    @Test
    @DisplayName("Extrato informa o sentido e a conta relacionada de cada lançamento")
    void extratoComSentidoEContaRelacionada() {
        Conta origem = contaPersistida("Marta");
        Conta destino = contaPersistida("Nuno");
        LocalDateTime base = agora();
        transacoes.salvar(origem, new Transacao(TipoTransacao.DEPOSITO, dinheiro("100.00"), base));
        transacoes.salvar(origem, new Transacao(TipoTransacao.SAQUE, dinheiro("10.00"), base.plusSeconds(1)));
        transacoes.salvarTransferencia(origem, destino,
                new Transacao(TipoTransacao.TRANSFERENCIA, dinheiro("25.00"), base.plusSeconds(2)));

        List<LancamentoExtrato> daOrigem = transacoes.buscarExtrato(origem);
        List<LancamentoExtrato> doDestino = transacoes.buscarExtrato(destino);

        assertEquals(3, daOrigem.size());
        assertEquals(Sentido.ENTRADA, daOrigem.get(0).sentido());
        assertEquals(Sentido.SAIDA, daOrigem.get(1).sentido());
        assertEquals(Sentido.SAIDA, daOrigem.get(2).sentido());
        assertEquals(destino.getIdPersistencia(), daOrigem.get(2).contaRelacionadaId());
        assertEquals(1, doDestino.size());
        assertEquals(Sentido.ENTRADA, doDestino.get(0).sentido());
        assertEquals(origem.getIdPersistencia(), doDestino.get(0).contaRelacionadaId());
    }

    @Test
    @DisplayName("Extrato do banco é exportado para .txt sem alterar saldo nem transações")
    void exportaExtratoDoBanco(@TempDir Path pasta) throws IOException {
        Conta conta = contaPersistida("Olga");
        transacoes.salvar(conta, new Transacao(TipoTransacao.DEPOSITO, dinheiro("150.75"), agora()));
        BigDecimal saldoAntes = conta.getSaldo();
        int transacoesAntes = transacoes.buscarPorConta(conta).size();
        Path arquivo = pasta.resolve("extrato.txt");

        new ExtratoExportador(transacoes).exportar(conta, arquivo);

        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        assertTrue(conteudo.contains("Titular       : Olga"));
        assertTrue(conteudo.contains("+ R$ 150,75"));
        assertEquals(saldoAntes, conta.getSaldo());
        assertEquals(transacoesAntes, transacoes.buscarPorConta(conta).size());
    }
}