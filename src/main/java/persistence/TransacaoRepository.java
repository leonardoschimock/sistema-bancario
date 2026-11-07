package persistence;

import domain.Conta;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistência JDBC de {@link Transacao}. Todo o SQL de transações fica aqui.
 *
 * <h2>Como o vínculo com a conta é guardado</h2>
 * {@link Transacao} é imutável e não conhece a conta; o vínculo vive na tabela, em duas colunas:
 * <ul>
 *   <li>{@code conta_id}: a conta dona do lançamento (depósito, saque) ou a ORIGEM da
 *       transferência;</li>
 *   <li>{@code conta_destino_id}: preenchida somente em transferências (a conta de DESTINO).</li>
 * </ul>
 * Uma transferência é gravada em UMA linha só e aparece no extrato das duas contas.
 *
 * <h2>Decisões de implementação</h2>
 * <ul>
 *   <li>O valor é gravado em DECIMAL(19,2). Valores com mais de 2 casas decimais são recusados
 *       (nunca arredondados em silêncio), igual à regra de {@code Conta}.</li>
 *   <li>O horário é gravado em DATETIME(6): precisão de microssegundos. Frações menores são
 *       perdidas pelo banco.</li>
 *   <li>Este repository só grava e lê registros; não altera saldo nem valida regra bancária.</li>
 * </ul>
 */
public class TransacaoRepository {

    private static final String INSERT =
            "INSERT INTO transacoes (conta_id, conta_destino_id, tipo, valor, horario) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_POR_CONTA =
            "SELECT conta_id, conta_destino_id, tipo, valor, horario FROM transacoes "
                    + "WHERE conta_id = ? OR conta_destino_id = ? ORDER BY horario, id";

    private final ConexaoFactory conexoes;

    public TransacaoRepository(ConexaoFactory conexoes) {
        if (conexoes == null) {
            throw new IllegalArgumentException("A fábrica de conexões é obrigatória.");
        }
        this.conexoes = conexoes;
    }

    /**
     * Grava um depósito ou saque da conta. A conta precisa já estar persistida.
     *
     * @throws IllegalArgumentException se a transação for do tipo TRANSFERENCIA (use
     *                                  {@link #salvarTransferencia})
     * @throws IllegalStateException    se a conta ainda não foi persistida
     */
    public void salvar(Conta conta, Transacao transacao) {
        exigirTransacao(transacao);
        if (transacao.getTipo() == TipoTransacao.TRANSFERENCIA) {
            throw new IllegalArgumentException(
                    "Transferência envolve duas contas: use salvarTransferencia.");
        }
        inserir(idPersistido(conta), null, transacao);
    }

    /**
     * Grava uma transferência (uma linha, visível no extrato de origem e de destino). As duas
     * contas precisam já estar persistidas.
     *
     * @throws IllegalArgumentException se a transação não for do tipo TRANSFERENCIA ou se origem e
     *                                  destino forem a mesma conta
     * @throws IllegalStateException    se alguma das contas ainda não foi persistida
     */
    public void salvarTransferencia(Conta origem, Conta destino, Transacao transacao) {
        exigirTransacao(transacao);
        if (transacao.getTipo() != TipoTransacao.TRANSFERENCIA) {
            throw new IllegalArgumentException("A transação deve ser do tipo TRANSFERENCIA.");
        }
        long idOrigem = idPersistido(origem);
        long idDestino = idPersistido(destino);
        if (idOrigem == idDestino) {
            throw new IllegalArgumentException("Origem e destino da transferência são a mesma conta.");
        }
        inserir(idOrigem, idDestino, transacao);
    }

    /**
     * Recupera as transações da conta: depósitos e saques dela e as transferências em que ela é
     * origem ou destino, em ordem cronológica. Não informa o sentido das transferências; para
     * isso use {@link #buscarExtrato}.
     */
    public List<Transacao> buscarPorConta(Conta conta) {
        List<Transacao> transacoes = new ArrayList<>();
        for (LancamentoExtrato lancamento : buscarExtrato(conta)) {
            transacoes.add(lancamento.transacao());
        }
        return transacoes;
    }

    /**
     * Recupera o extrato da conta em ordem cronológica (empates pelo id da linha), já com o
     * SENTIDO de cada lançamento em relação a esta conta e, nas transferências, a conta
     * relacionada. Somente leitura (SELECT): não altera nenhum registro.
     *
     * @throws IllegalStateException se a conta ainda não foi persistida
     */
    public List<LancamentoExtrato> buscarExtrato(Conta conta) {
        long id = idPersistido(conta);
        List<LancamentoExtrato> extrato = new ArrayList<>();
        try (Connection conexao = conexoes.obterConexao();
             PreparedStatement ps = conexao.prepareStatement(SELECT_POR_CONTA)) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    extrato.add(mapearLancamento(rs, id));
                }
            }
            return extrato;
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Erro ao buscar as transações da conta " + id + ": " + e.getMessage(), e);
        }
    }

    private void inserir(long contaId, Long contaDestinoId, Transacao transacao) {
        BigDecimal valor = duasCasas(transacao.getValor());
        try (Connection conexao = conexoes.obterConexao();
             PreparedStatement ps = conexao.prepareStatement(INSERT)) {
            ps.setLong(1, contaId);
            if (contaDestinoId == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, contaDestinoId);
            }
            ps.setString(3, transacao.getTipo().name());
            ps.setBigDecimal(4, valor);
            ps.setObject(5, transacao.getHorario());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Erro ao gravar a transação da conta " + contaId + ": " + e.getMessage(), e);
        }
    }

    /** Monta o lançamento do ponto de vista da conta {@code idConsultado}. */
    private static LancamentoExtrato mapearLancamento(ResultSet rs, long idConsultado) throws SQLException {
        try {
            Transacao transacao = new Transacao(
                    TipoTransacao.valueOf(rs.getString("tipo")),
                    rs.getBigDecimal("valor"),
                    rs.getObject("horario", LocalDateTime.class));
            long contaId = rs.getLong("conta_id");
            long destinoId = rs.getLong("conta_destino_id");
            boolean semDestino = rs.wasNull();

            switch (transacao.getTipo()) {
                case DEPOSITO:
                    return new LancamentoExtrato(transacao, Sentido.ENTRADA, null);
                case SAQUE:
                    return new LancamentoExtrato(transacao, Sentido.SAIDA, null);
                case TRANSFERENCIA:
                    if (semDestino) {
                        throw new IllegalArgumentException("transferência sem conta de destino");
                    }
                    // conta_id é a ORIGEM; conta_destino_id é o DESTINO.
                    return (contaId == idConsultado)
                            ? new LancamentoExtrato(transacao, Sentido.SAIDA, destinoId)
                            : new LancamentoExtrato(transacao, Sentido.ENTRADA, contaId);
                default:
                    throw new IllegalArgumentException("tipo não tratado: " + transacao.getTipo());
            }
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Uma linha de transação contém dados inválidos: " + e.getMessage(), e);
        }
    }

    private static long idPersistido(Conta conta) {
        if (conta == null) {
            throw new IllegalArgumentException("A conta é obrigatória.");
        }
        Long id = conta.getIdPersistencia();
        if (id == null) {
            throw new IllegalStateException("Persista a conta (ContaRepository.salvar) antes de gravar transações.");
        }
        return id;
    }

    private static void exigirTransacao(Transacao transacao) {
        if (transacao == null) {
            throw new IllegalArgumentException("A transação é obrigatória.");
        }
    }

    /** Devolve o valor com 2 casas ou recusa, em vez de deixar o banco arredondar em silêncio. */
    private static BigDecimal duasCasas(BigDecimal valor) {
        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("O valor da transação deve ter no máximo 2 casas decimais.", e);
        }
    }
}