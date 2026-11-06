package persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes de ConexaoFactory que NÃO precisam de servidor de banco (rodam no "mvn test" comum). */
class ConexaoFactoryTest {

    @Test
    @DisplayName("Recusa URL e usuário em branco")
    void recusaConfiguracaoInvalida() {
        assertThrows(IllegalArgumentException.class, () -> new ConexaoFactory(" ", "root", ""));
        assertThrows(IllegalArgumentException.class, () -> new ConexaoFactory(null, "root", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new ConexaoFactory("jdbc:mysql://localhost/banco", " ", ""));
    }

    @Test
    @DisplayName("toString não revela a senha")
    void toStringNaoRevelaSenha() {
        ConexaoFactory fabrica = new ConexaoFactory("jdbc:mysql://localhost/banco", "app", "segredo123");

        assertFalse(fabrica.toString().contains("segredo123"));
    }

    @Test
    @DisplayName("Falha de conexão vira PersistenciaException com a causa original")
    void falhaDeConexaoViraPersistenciaException() {
        ConexaoFactory fabrica = new ConexaoFactory("jdbc:protocolo-inexistente://x", "app", "");

        PersistenciaException erro = assertThrows(PersistenciaException.class, fabrica::obterConexao);
        assertFalse(erro.getCause() == null);
    }

    @Test
    @DisplayName("Repositories recusam fábrica de conexões nula")
    void repositoriesRecusamFabricaNula() {
        assertThrows(IllegalArgumentException.class, () -> new ContaRepository(null));
        assertThrows(IllegalArgumentException.class, () -> new TransacaoRepository(null));
    }
}
