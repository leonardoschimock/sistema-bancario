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
import domain.TipoTransacao;
import domain.Transacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.ConexaoFactory;
import persistence.PersistenciaException;
import persistence.TransacaoRepository;

/**
 * Testes UNITÁRIOS da exportação do extrato: não usam banco. O repository é substituído por uma
 * subclasse que devolve lançamentos prontos (nenhuma conexão é aberta), e os arquivos são
 * gravados em diretório temporário, apagado pelo JUnit ao final.
 */
class ExtratoExportadorTest {

    private static final LocalDateTime GERADO_EM = LocalDateTime.of(2026, 11, 6, 14, 30, 0);

    @TempDir
    Path pasta;

    /** Repository falso: só implementa a consulta; qualquer outra chamada tentaria abrir conexão. */
    private static class RepositorioFalso extends TransacaoRepository {
        private final List<LancamentoExtrato> lancamentos;
        private final RuntimeException falha;
        int consultas;

        RepositorioFalso(List<LancamentoExtrato> lancamentos, RuntimeException falha) {
            super(new ConexaoFactory("jdbc:mariadb://host-inexistente/banco", "usuario", ""));
            this.lancamentos = lancamentos;
            this.falha = falha;
        }

        @Override
        public List<LancamentoExtrato> buscarExtrato(Conta conta) {
            consultas++;
            if (falha != null) {
                throw falha;
            }
            return lancamentos;
        }
    }

    private static Conta conta() {
        return Conta.restaurar(7, new Cliente("Ana Souza"), new BigDecimal("1734.06"));
    }

    private static LancamentoExtrato lancamento(TipoTransacao tipo, String valor, LocalDateTime horario,
                                                Sentido sentido, Long relacionada) {
        return new LancamentoExtrato(new Transacao(tipo, new BigDecimal(valor), horario), sentido, relacionada);
    }

    private static List<LancamentoExtrato> extratoDeExemplo() {
        return List.of(
                lancamento(TipoTransacao.DEPOSITO, "1000.00", LocalDateTime.of(2026, 11, 3, 9, 0, 0), Sentido.ENTRADA, null),
                lancamento(TipoTransacao.SAQUE, "200.50", LocalDateTime.of(2026, 11, 3, 9, 15, 30), Sentido.SAIDA, null),
                lancamento(TipoTransacao.TRANSFERENCIA, "300.00", LocalDateTime.of(2026, 11, 4, 10, 0, 0), Sentido.SAIDA, 8L),
                lancamento(TipoTransacao.TRANSFERENCIA, "1234.56", LocalDateTime.of(2026, 11, 5, 16, 45, 10), Sentido.ENTRADA, 9L));
    }

    private static final String EXTRATO_ESPERADO = String.join("\n",
            "==========================================================================",
            "EXTRATO DE CONTA",
            "==========================================================================",
            "Conta         : #7",
            "Titular       : Ana Souza",
            "Gerado em     : 06/11/2026 14:30:00",
            "Saldo atual   : R$ 1.734,06",
            "--------------------------------------------------------------------------",
            "DATA/HORA            DESCRIÇÃO                                       VALOR",
            "03/11/2026 09:00:00  Depósito                                + R$ 1.000,00",
            "03/11/2026 09:15:30  Saque                                     - R$ 200,50",
            "04/11/2026 10:00:00  Transferência enviada para conta #8       - R$ 300,00",
            "05/11/2026 16:45:10  Transferência recebida da conta #9      + R$ 1.234,56",
            "--------------------------------------------------------------------------",
            "Lançamentos   : 4",
            "Total entradas: R$ 2.234,56",
            "Total saídas  : R$ 500,50",
            "==========================================================================",
            "");

    @Test
    @DisplayName("Texto do extrato traz cabeçalho, lançamentos com sinal e totais, no layout esperado")
    void formataExtratoCompleto() {
        String texto = ExtratoExportador.formatar(conta(), extratoDeExemplo(), GERADO_EM);

        assertEquals(EXTRATO_ESPERADO, texto);
    }

    @Test
    @DisplayName("Extrato sem transações informa que está vazio e zera os totais")
    void formataExtratoVazio() {
        String texto = ExtratoExportador.formatar(conta(), List.of(), GERADO_EM);

        assertTrue(texto.contains("(nenhuma transação registrada para esta conta)"));
        assertTrue(texto.contains("Lançamentos   : 0"));
        assertTrue(texto.contains("Total entradas: R$ 0,00"));
        assertTrue(texto.contains("Total saídas  : R$ 0,00"));
    }

    @Test
    @DisplayName("Arquivo exportado (.txt, UTF-8) contém o extrato da conta consultada")
    void exportaArquivoComConteudoEsperado() throws IOException {
        RepositorioFalso repositorio = new RepositorioFalso(extratoDeExemplo(), null);
        Path destino = pasta.resolve("extrato-conta-7.txt");

        Path gravado = new ExtratoExportador(repositorio).exportar(conta(), destino);

        assertEquals(destino, gravado);
        assertEquals(1, repositorio.consultas);
        String conteudo = Files.readString(destino, StandardCharsets.UTF_8);
        assertTrue(conteudo.startsWith("====="));
        assertTrue(conteudo.contains("Conta         : #7"));
        assertTrue(conteudo.contains("Titular       : Ana Souza"));
        assertTrue(conteudo.contains("Depósito"));
        assertTrue(conteudo.contains("+ R$ 1.000,00"));
        assertTrue(conteudo.contains("- R$ 200,50"));
        assertTrue(conteudo.contains("Transferência enviada para conta #8"));
        assertTrue(conteudo.contains("Transferência recebida da conta #9"));
        assertTrue(conteudo.contains("Total entradas: R$ 2.234,56"));
        assertTrue(conteudo.contains("Total saídas  : R$ 500,50"));
    }

    @Test
    @DisplayName("Exportar não altera o saldo da conta nem os lançamentos recebidos")
    void exportarNaoAlteraDados() {
        Conta conta = conta();
        List<LancamentoExtrato> lancamentos = extratoDeExemplo();
        BigDecimal saldoAntes = conta.getSaldo();
        LancamentoExtrato primeiro = lancamentos.get(0);
        RepositorioFalso repositorio = new RepositorioFalso(lancamentos, null);

        new ExtratoExportador(repositorio).exportar(conta, pasta.resolve("extrato.txt"));
        new ExtratoExportador(repositorio).exportar(conta, pasta.resolve("extrato.txt"));

        assertEquals(saldoAntes, conta.getSaldo());
        assertEquals(4, lancamentos.size());
        assertEquals(primeiro, lancamentos.get(0));
    }

    @Test
    @DisplayName("Nova exportação substitui o arquivo anterior e cria diretórios ausentes")
    void substituiArquivoECriaDiretorios() throws IOException {
        Path destino = pasta.resolve("saida").resolve("extratos").resolve("conta-7.txt");
        ExtratoExportador exportador = new ExtratoExportador(new RepositorioFalso(extratoDeExemplo(), null));

        exportador.exportar(conta(), destino);
        String primeira = Files.readString(destino, StandardCharsets.UTF_8);
        Files.writeString(destino, "lixo antigo que deve sumir " + "x".repeat(5000), StandardCharsets.UTF_8);
        exportador.exportar(conta(), destino);
        String segunda = Files.readString(destino, StandardCharsets.UTF_8);

        assertFalse(segunda.contains("lixo antigo"));
        assertEquals(primeira.lines().count(), segunda.lines().count());
    }

    @Test
    @DisplayName("Erro de escrita (destino é um diretório) vira ExportacaoExtratoException")
    void erroQuandoDestinoEUmDiretorio() throws IOException {
        Path diretorio = Files.createDirectory(pasta.resolve("extrato.txt")); // diretório com nome de .txt
        ExtratoExportador exportador = new ExtratoExportador(new RepositorioFalso(extratoDeExemplo(), null));

        assertThrows(ExportacaoExtratoException.class, () -> exportador.exportar(conta(), diretorio));
    }

    @Test
    @DisplayName("Erro de escrita (pasta pai é um arquivo) vira ExportacaoExtratoException com a causa")
    void erroQuandoPastaPaiEUmArquivo() throws IOException {
        Path arquivoComum = Files.writeString(pasta.resolve("naoEPasta"), "conteudo");
        Path destino = arquivoComum.resolve("extrato.txt");
        ExtratoExportador exportador = new ExtratoExportador(new RepositorioFalso(extratoDeExemplo(), null));

        ExportacaoExtratoException e = assertThrows(ExportacaoExtratoException.class,
                () -> exportador.exportar(conta(), destino));

        assertNotNull(e.getCause());
        assertTrue(e.getCause() instanceof IOException);
    }

    @Test
    @DisplayName("Falha de banco propaga PersistenciaException e não toca no arquivo existente")
    void falhaDeBancoPreservaArquivoExistente() throws IOException {
        Path destino = Files.writeString(pasta.resolve("extrato.txt"), "extrato antigo");
        ExtratoExportador exportador = new ExtratoExportador(
                new RepositorioFalso(null, new PersistenciaException("banco fora do ar")));

        assertThrows(PersistenciaException.class, () -> exportador.exportar(conta(), destino));

        assertEquals("extrato antigo", Files.readString(destino));
    }

    @Test
    @DisplayName("Argumentos inválidos são recusados: conta nula, caminho nulo e extensão diferente de .txt")
    void recusaArgumentosInvalidos() {
        ExtratoExportador exportador = new ExtratoExportador(new RepositorioFalso(List.of(), null));

        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(null, pasta.resolve("a.txt")));
        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(conta(), null));
        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(conta(), pasta.resolve("a.csv")));
        assertThrows(IllegalArgumentException.class, () -> new ExtratoExportador(null));
        assertFalse(Files.exists(pasta.resolve("a.csv")));
    }
}