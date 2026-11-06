package persistence;

import domain.Cliente;
import domain.Conta;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Persistência JDBC de {@link Conta}. Todo o SQL de contas fica aqui.
 *
 * Este repository só GRAVA e LÊ o estado da conta; não aplica regra bancária. Depósito, saque e
 * transferência continuam sendo feitos exclusivamente pelos métodos de {@link Conta}.
 *
 * <h2>Pontos de atenção</h2>
 * <ul>
 *   <li><b>Saldo é um instantâneo.</b> {@link #salvar} grava o valor devolvido por
 *       {@code conta.getSaldo()} naquele instante. Se outras threads ainda estiverem operando a
 *       conta, o banco ficará com um valor que pode já estar defasado. Persista o saldo quando a
 *       simulação terminar.</li>
 *   <li><b>Sem cache.</b> Cada {@link #buscarPorId} cria um objeto {@link Conta} NOVO, com lock
 *       próprio. Duas instâncias da mesma linha não se sincronizam entre si; numa simulação
 *       concorrente, todas as threads devem receber a MESMA instância.</li>
 *   <li>A primeira gravação de uma conta (INSERT) deve ser feita por uma thread só.</li>
 * </ul>
 */
public class ContaRepository {

    private static final String INSERT =
            "INSERT INTO contas (titular_nome, saldo) VALUES (?, ?)";
    private static final String UPDATE_SALDO =
            "UPDATE contas SET saldo = ? WHERE id = ?";
    private static final String SELECT_POR_ID =
            "SELECT id, titular_nome, saldo FROM contas WHERE id = ?";
    private static final String SELECT_TODAS =
            "SELECT id, titular_nome, saldo FROM contas ORDER BY id";

    private final ConexaoFactory conexoes;

    public ContaRepository(ConexaoFactory conexoes) {
        if (conexoes == null) {
            throw new IllegalArgumentException("A fábrica de conexões é obrigatória.");
        }
        this.conexoes = conexoes;
    }

    /**
     * Grava a conta. Se ela ainda não tem identificador de persistência, faz INSERT e registra na
     * conta o id gerado pelo banco; se já tem, faz UPDATE do saldo.
     *
     * @throws PersistenciaException se houver falha no banco ou se a conta já persistida não
     *                               existir mais na tabela
     */
    public void salvar(Conta conta) {
        if (conta == null) {
            throw new IllegalArgumentException("A conta é obrigatória.");
        }
        Long id = conta.getIdPersistencia();
        if (id == null) {
            inserir(conta);
        } else {
            atualizarSaldo(id, conta);
        }
    }

    /** Recupera uma conta pelo identificador do banco. */
    public Optional<Conta> buscarPorId(long id) {
        try (Connection conexao = conexoes.obterConexao();
             PreparedStatement ps = conexao.prepareStatement(SELECT_POR_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao buscar a conta " + id + ": " + e.getMessage(), e);
        }
    }

    /** Recupera todas as contas, ordenadas pelo identificador. */
    public List<Conta> listarTodas() {
        List<Conta> contas = new ArrayList<>();
        try (Connection conexao = conexoes.obterConexao();
             PreparedStatement ps = conexao.prepareStatement(SELECT_TODAS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                contas.add(mapear(rs));
            }
            return contas;
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao listar as contas: " + e.getMessage(), e);
        }
    }

    private void inserir(Conta conta) {
        BigDecimal saldo = conta.getSaldo();
        String titular = conta.getTitular().getNome();
        try (Connection conexao = conexoes.obterConexao();
             PreparedStatement ps = conexao.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, titular);
            ps.setBigDecimal(2, saldo);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new PersistenciaException("O banco não devolveu o id da conta inserida.");
                }
                conta.definirIdPersistencia(chaves.getLong(1));
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Erro ao inserir a conta: " + e.getMessage(), e);
        }
    }

    private void atualizarSaldo(long id, Conta conta) {
        BigDecimal saldo = conta.getSaldo();
        try (Connection conexao = conexoes.obterConexao();
             PreparedStatement ps = conexao.prepareStatement(UPDATE_SALDO)) {
            ps.setBigDecimal(1, saldo);
            ps.setLong(2, id);
            if (ps.executeUpdate() == 0) {
                throw new PersistenciaException("A conta " + id + " não existe no banco.");
            }
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Erro ao atualizar o saldo da conta " + id + ": " + e.getMessage(), e);
        }
    }

    private static Conta mapear(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        try {
            return Conta.restaurar(id, new Cliente(rs.getString("titular_nome")), rs.getBigDecimal("saldo"));
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException(
                    "A linha da conta " + id + " contém dados inválidos: " + e.getMessage(), e);
        }
    }
}
