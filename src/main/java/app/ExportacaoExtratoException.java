package app;

/**
 * Falha ao gravar o arquivo do extrato (caminho inválido, sem permissão, disco cheio etc.).
 *
 * É não verificada, como {@code PersistenciaException}, e sempre carrega a causa original
 * ({@code IOException}); nada é engolido em silêncio.
 */
public class ExportacaoExtratoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExportacaoExtratoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public ExportacaoExtratoException(String mensagem) {
        super(mensagem);
    }
}