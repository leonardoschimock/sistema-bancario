package persistence;

/**
 * Falha ao acessar o banco de dados (conexão, SQL ou dado inconsistente).
 *
 * É não verificada (unchecked), como {@code SaldoInsuficienteException}, para não poluir as
 * assinaturas com {@code throws SQLException}. Sempre carrega a causa original: nenhuma exceção
 * de banco é engolida em silêncio.
 */
public class PersistenciaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public PersistenciaException(String mensagem) {
        super(mensagem);
    }
}
