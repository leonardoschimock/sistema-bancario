package persistence;

/**
 * Configuração do banco usado SOMENTE pelos testes de integração.
 *
 * Variáveis de ambiente (todas opcionais):
 * <ul>
 *   <li>{@code BANCO_TESTE_URL}     (padrão {@code jdbc:mysql://localhost:3306/banco_teste});</li>
 *   <li>{@code BANCO_TESTE_USUARIO} (padrão {@code root});</li>
 *   <li>{@code BANCO_TESTE_SENHA}   (padrão vazia).</li>
 * </ul>
 * Propositalmente diferente de {@code BANCO_URL}: os testes nunca devem usar o banco real.
 * Os testes não apagam dados; cada um cria as próprias contas e consulta só pelos ids delas.
 */
final class BancoDeTeste {

    private BancoDeTeste() {
    }

    static ConexaoFactory conexoes() {
        return new ConexaoFactory(
                variavel("BANCO_TESTE_URL", "jdbc:mysql://localhost:3306/banco_teste"),
                variavel("BANCO_TESTE_USUARIO", "root"),
                variavel("BANCO_TESTE_SENHA", ""));
    }

    private static String variavel(String nome, String padrao) {
        String valor = System.getenv(nome);
        return (valor == null || valor.isEmpty()) ? padrao : valor;
    }
}
