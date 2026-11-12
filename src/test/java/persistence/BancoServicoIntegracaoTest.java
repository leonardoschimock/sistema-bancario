package persistence;

import app.BancoServico;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import domain.SaldoInsuficienteException;
import domain.TipoTransacao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TESTES DE INTEGRAÇÃO do fluxo completo (operação -> persistência -> extrato -> arquivo): precisam
 * de um servidor MariaDB com o schema.sql aplicado no banco de teste. Não rodam no "mvn test"
 * comum; use "mvn test -Pintegracao".
 *
 * Para provar que os dados vieram do BANCO e não da memória, as verificações são feitas por um
 * SEGUNDO {@link BancoServico}, recém-criado (sem nenhuma conta aberta), sobre as mesmas tabelas.
 * Os testes não apagam dados; cada um cria as próprias contas e consulta só pelos ids delas.
 */
@Tag("integracao")
class BancoServicoIntegracaoTest {

    @TempDir
    Path pasta;

    private static BancoServico novoServico() {
        ConexaoFactory conexoes = BancoDeTeste.conexoes();
        return new BancoServico(new ContaRepository(conexoes), new TransacaoRepository(conexoes));
    }

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("Depósito, saque e transferência ficam gravados: extrato e saldos lidos do banco")
    void fluxoCompletoFicaNoBanco() {
        BancoServico servico = novoServico();
        long ana = servico.criarConta("Ana Integração", d("1000.00")).id();
        long bia = servico.criarConta("Bia Integração", d("500.00")).id();

        servico.depositar(ana, d("200.00"));
        servico.sacar(ana, d("50.00"));
        servico.transferir(ana, bia, d("80.00"));

        BancoServico outro = novoServico(); // nenhuma conta aberta: tudo vem do banco
        List<LancamentoExtrato> deAna = outro.consultarExtrato(ana);
        List<LancamentoExtrato> deBia = outro.consultarExtrato(bia);

        assertEquals(3, deAna.size());
        assertEquals(TipoTransacao.DEPOSITO, deAna.get(0).transacao().getTipo());
        assertEquals(Sentido.ENTRADA, deAna.get(0).sentido());
        assertEquals(TipoTransacao.SAQUE, deAna.get(1).transacao().getTipo());
        assertEquals(Sentido.SAIDA, deAna.get(1).sentido());
        assertEquals(TipoTransacao.TRANSFERENCIA, deAna.get(2).transacao().getTipo());
        assertEquals(Sentido.SAIDA, deAna.get(2).sentido());
        assertEquals(Long.valueOf(bia), deAna.get(2).contaRelacionadaId());

        assertEquals(1, deBia.size());
        assertEquals(Sentido.ENTRADA, deBia.get(0).sentido());
        assertEquals(Long.valueOf(ana), deBia.get(0).contaRelacionadaId());

        assertEquals(0, d("1070.00").compareTo(outro.consultarSaldo(ana)));
        assertEquals(0, d("580.00").compareTo(outro.consultarSaldo(bia)));
    }

    @Test
    @DisplayName("Saque recusado por saldo insuficiente não grava transação nem altera o saldo no banco")
    void recusadoNaoGravaNoBanco() {
        BancoServico servico = novoServico();
        long ana = servico.criarConta("Ana Recusa", d("1000.00")).id();

        assertThrows(SaldoInsuficienteException.class, () -> servico.sacar(ana, d("5000.00")));

        BancoServico outro = novoServico();
        assertTrue(outro.consultarExtrato(ana).isEmpty());
        assertEquals(0, d("1000.00").compareTo(outro.consultarSaldo(ana)));
    }

    @Test
    @DisplayName("Exportação .txt do extrato gerado pelo fluxo real (sem inserir linhas à mão)")
    void exportacaoDoFluxoReal() throws IOException {
        BancoServico servico = novoServico();
        long ana = servico.criarConta("Ana Exemplo", d("1000.00")).id();
        long bia = servico.criarConta("Bia Exemplo", d("500.00")).id();
        servico.depositar(ana, d("200.00"));
        servico.sacar(ana, d("50.00"));
        servico.transferir(ana, bia, d("80.00"));

        Path gravado = novoServico().exportarExtrato(ana, pasta.resolve("extrato.txt"));

        String texto = Files.readString(gravado, StandardCharsets.UTF_8);
        assertTrue(texto.contains("Depósito"));
        assertTrue(texto.contains("Saque"));
        assertTrue(texto.contains("Transferência enviada para conta #" + bia));
        assertTrue(texto.contains("R$ 1.070,00"));
    }
}
