package app;

import domain.Conta;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import domain.TipoTransacao;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import persistence.PersistenciaException;
import persistence.TransacaoRepository;

/**
 * Consulta o extrato de uma conta, apresenta-o em texto legível e o exporta para um arquivo .txt.
 *
 * <h2>Responsabilidades</h2>
 * <ul>
 *   <li><b>Consulta:</b> delegada a {@code TransacaoRepository.buscarExtrato}; esta classe não
 *       contém SQL.</li>
 *   <li><b>Apresentação:</b> {@link #formatar} é uma função pura (não lê banco nem relógio):
 *       dados entram, texto sai. Por isso é fácil de testar.</li>
 *   <li><b>I/O:</b> {@link #exportar} grava o texto em UTF-8.</li>
 * </ul>
 *
 * <h2>Somente leitura</h2>
 * Nenhum método chama {@code depositar}, {@code sacar} ou {@code transferirPara}, nem grava no
 * banco. O único acesso à {@link Conta} além do titular e do id é {@code getSaldo()}, que apenas
 * lê (dentro do lock da própria conta).
 *
 * <h2>Decisões de implementação (a especificação não define o layout)</h2>
 * <ul>
 *   <li>Cabeçalho (conta, titular, data de geração, saldo atual), uma linha por lançamento em
 *       ordem cronológica e um resumo (quantidade, total de entradas e de saídas).</li>
 *   <li>Entradas são exibidas com "+" e saídas com "-". Transferências indicam a outra conta.</li>
 *   <li>Valores no formato brasileiro (R$ 1.234,56), independentemente do idioma do sistema.</li>
 *   <li>O texto inteiro é montado ANTES de abrir o arquivo: se a consulta ao banco falhar, o
 *       arquivo existente não é apagado nem deixado pela metade.</li>
 *   <li>O arquivo é gravado em UTF-8 e substitui um arquivo anterior de mesmo nome. Diretórios
 *       ausentes no caminho são criados.</li>
 *   <li>O "Saldo atual" é o saldo em memória da {@link Conta} no momento da geração. Se houver
 *       threads operando a conta, ele pode não refletir transações ainda não gravadas; gere o
 *       extrato quando a simulação terminar.</li>
 * </ul>
 */
public class ExtratoExportador {

    private static final int LARGURA_MINIMA = 60;
    private static final int LARGURA_DATA_HORA = 19;  // dd/MM/yyyy HH:mm:ss
    private static final int LARGURA_VALOR = 16;      // "- R$ 1.234.567,89" cabe com folga
    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final TransacaoRepository transacoes;

    public ExtratoExportador(TransacaoRepository transacoes) {
        if (transacoes == null) {
            throw new IllegalArgumentException("O repository de transações é obrigatório.");
        }
        this.transacoes = transacoes;
    }

    /**
     * Busca o extrato da conta no banco e devolve o texto formatado, sem gravar arquivo.
     *
     * @throws IllegalStateException               se a conta ainda não foi persistida
     * @throws PersistenciaException   se a consulta ao banco falhar
     */
    public String gerarTexto(Conta conta) {
        exigirConta(conta);
        List<LancamentoExtrato> lancamentos = transacoes.buscarExtrato(conta);
        return formatar(conta, lancamentos, LocalDateTime.now());
    }

    /**
     * Gera o extrato da conta e o grava em {@code arquivo} (UTF-8), substituindo-o se já existir.
     *
     * @return o próprio caminho gravado
     * @throws IllegalArgumentException            se o caminho for nulo ou terminar em algo que
     *                                             não seja um arquivo .txt
     * @throws IllegalStateException               se a conta ainda não foi persistida
     * @throws PersistenciaException   se a consulta ao banco falhar (nada é gravado)
     * @throws ExportacaoExtratoException          se não for possível escrever o arquivo
     */
    public Path exportar(Conta conta, Path arquivo) {
        exigirConta(conta);
        if (arquivo == null) {
            throw new IllegalArgumentException("O caminho do arquivo é obrigatório.");
        }
        if (!arquivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new IllegalArgumentException("O arquivo do extrato deve ter a extensão .txt: " + arquivo);
        }
        if (Files.isDirectory(arquivo)) {
            throw new ExportacaoExtratoException("O caminho informado é um diretório, não um arquivo: " + arquivo);
        }

        String texto = gerarTexto(conta); // consulta primeiro: falha aqui não toca no arquivo
        try {
            Path pai = arquivo.toAbsolutePath().getParent();
            if (pai != null) {
                Files.createDirectories(pai);
            }
            Files.writeString(arquivo, texto, StandardCharsets.UTF_8); // CREATE + TRUNCATE + WRITE
            return arquivo;
        } catch (IOException e) {
            throw new ExportacaoExtratoException(
                    "Não foi possível gravar o extrato em " + arquivo + ": " + e.getMessage(), e);
        }
    }

    /**
     * Monta o texto do extrato. Função pura: não consulta banco nem relógio, e não altera a
     * conta nem os lançamentos recebidos.
     *
     * @param geradoEm instante exibido como "Gerado em"
     */
    static String formatar(Conta conta, List<LancamentoExtrato> lancamentos, LocalDateTime geradoEm) {
        int larguraDescricao = "DESCRIÇÃO".length();
        for (LancamentoExtrato l : lancamentos) {
            larguraDescricao = Math.max(larguraDescricao, descricao(l).length());
        }
        // As réguas acompanham a largura da tabela (transferências têm descrição mais longa).
        int largura = Math.max(LARGURA_MINIMA, LARGURA_DATA_HORA + 2 + larguraDescricao + 2 + LARGURA_VALOR);
        String linhaDupla = "=".repeat(largura);
        String linhaSimples = "-".repeat(largura);

        StringBuilder sb = new StringBuilder();
        sb.append(linhaDupla).append('\n');
        sb.append("EXTRATO DE CONTA\n");
        sb.append(linhaDupla).append('\n');
        sb.append(String.format("%-14s: %s\n", "Conta", "#" + conta.getIdPersistencia()));
        sb.append(String.format("%-14s: %s\n", "Titular", conta.getTitular().getNome()));
        sb.append(String.format("%-14s: %s\n", "Gerado em", DATA_HORA.format(geradoEm)));
        sb.append(String.format("%-14s: %s\n", "Saldo atual", moeda(conta.getSaldo())));
        sb.append(linhaSimples).append('\n');

        if (lancamentos.isEmpty()) {
            sb.append("(nenhuma transação registrada para esta conta)\n");
        } else {
            String modelo = "%-" + LARGURA_DATA_HORA + "s  %-" + larguraDescricao + "s  %"
                    + LARGURA_VALOR + "s\n";

            sb.append(String.format(modelo, "DATA/HORA", "DESCRIÇÃO", "VALOR"));
            for (LancamentoExtrato l : lancamentos) {
                sb.append(String.format(modelo,
                        DATA_HORA.format(l.transacao().getHorario()),
                        descricao(l),
                        valorComSinal(l)));
            }
        }

        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        for (LancamentoExtrato l : lancamentos) {
            if (l.sentido() == Sentido.ENTRADA) {
                entradas = entradas.add(l.transacao().getValor());
            } else {
                saidas = saidas.add(l.transacao().getValor());
            }
        }
        sb.append(linhaSimples).append('\n');
        sb.append(String.format("%-14s: %d\n", "Lançamentos", lancamentos.size()));
        sb.append(String.format("%-14s: %s\n", "Total entradas", moeda(entradas)));
        sb.append(String.format("%-14s: %s\n", "Total saídas", moeda(saidas)));
        sb.append(linhaDupla).append('\n');
        return sb.toString();
    }

    private static String descricao(LancamentoExtrato l) {
        TipoTransacao tipo = l.transacao().getTipo();
        switch (tipo) {
            case DEPOSITO:
                return "Depósito";
            case SAQUE:
                return "Saque";
            case TRANSFERENCIA:
                return (l.sentido() == Sentido.SAIDA)
                        ? "Transferência enviada para conta #" + l.contaRelacionadaId()
                        : "Transferência recebida da conta #" + l.contaRelacionadaId();
            default:
                return tipo.name();
        }
    }

    private static String valorComSinal(LancamentoExtrato l) {
        String sinal = (l.sentido() == Sentido.ENTRADA) ? "+ " : "- ";
        return sinal + moeda(l.transacao().getValor());
    }

    /** Formato brasileiro fixo (R$ 1.234,56); DecimalFormat não é thread-safe, então é local. */
    private static String moeda(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        return "R$ " + formato.format(valor);
    }

    private static void exigirConta(Conta conta) {
        if (conta == null) {
            throw new IllegalArgumentException("A conta é obrigatória.");
        }
    }
}