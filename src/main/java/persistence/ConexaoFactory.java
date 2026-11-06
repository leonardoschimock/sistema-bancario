package persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica de conexões JDBC com o MariaDB.
 *
 * Cada chamada de {@link #obterConexao()} abre uma conexão NOVA; quem a recebe é responsável por
 * fechá-la (os repositories fazem isso com try-with-resources).
 *
 * <h2>Configuração</h2>
 * {@link #doAmbiente()} lê, nesta ordem de prioridade (propriedade de sistema, depois variável de
 * ambiente, depois padrão):
 * <ul>
 *   <li>{@code banco.url} / {@code BANCO_URL} (padrão {@value #URL_PADRAO});</li>
 *   <li>{@code banco.usuario} / {@code BANCO_USUARIO} (padrão {@value #USUARIO_PADRAO});</li>
 *   <li>{@code banco.senha} / {@code BANCO_SENHA} (padrão: vazia).</li>
 * </ul>
 * A senha nunca fica no código-fonte nem aparece em {@link #toString()} ou nas mensagens de erro.
 */
public class ConexaoFactory {

    public static final String URL_PADRAO = "jdbc:mariadb://localhost:3306/banco";
    public static final String USUARIO_PADRAO = "root";

    private final String url;
    private final String usuario;
    private final String senha;

    public ConexaoFactory(String url, String usuario, String senha) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A URL JDBC é obrigatória.");
        }
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalArgumentException("O usuário do banco é obrigatório.");
        }
        this.url = url.trim();
        this.usuario = usuario.trim();
        this.senha = (senha == null) ? "" : senha;
    }

    /** Cria a fábrica a partir de propriedades de sistema / variáveis de ambiente. */
    public static ConexaoFactory doAmbiente() {
        return new ConexaoFactory(
                configuracao("banco.url", "BANCO_URL", URL_PADRAO),
                configuracao("banco.usuario", "BANCO_USUARIO", USUARIO_PADRAO),
                configuracao("banco.senha", "BANCO_SENHA", ""));
    }

    /** Abre uma nova conexão. O chamador deve fechá-la. */
    public Connection obterConexao() {
        try {
            return DriverManager.getConnection(url, usuario, senha);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Não foi possível conectar ao banco (" + url + ", usuário " + usuario + "): "
                            + e.getMessage(), e);
        }
    }

    private static String configuracao(String propriedade, String variavel, String padrao) {
        String valor = System.getProperty(propriedade);
        if (valor == null || valor.isEmpty()) {
            valor = System.getenv(variavel);
        }
        return (valor == null || valor.isEmpty()) ? padrao : valor;
    }

    @Override
    public String toString() {
        return "ConexaoFactory{url='" + url + "', usuario='" + usuario + "'}";
    }
}
