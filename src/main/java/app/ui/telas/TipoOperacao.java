package app.ui.telas;

/** Operações bancárias que a interface oferece e os textos de cada uma (só apresentação). */
public enum TipoOperacao {
    DEPOSITO("Dep\u00f3sito", "Depositar", "Dep\u00f3sito realizado", "Dep\u00f3sito n\u00e3o realizado",
            "Dep\u00f3sito realizado com sucesso."),
    SAQUE("Saque", "Sacar", "Saque realizado", "Saque n\u00e3o realizado", "Saque realizado com sucesso."),
    TRANSFERENCIA("Transfer\u00eancia", "Transferir", "Transfer\u00eancia conclu\u00edda",
            "Transfer\u00eancia n\u00e3o realizada", "Transfer\u00eancia conclu\u00edda.");

    private final String segmento;
    private final String botao;
    private final String tituloSucesso;
    private final String tituloRecusa;
    private final String mensagemSucesso;

    TipoOperacao(String segmento, String botao, String tituloSucesso, String tituloRecusa, String mensagemSucesso) {
        this.segmento = segmento;
        this.botao = botao;
        this.tituloSucesso = tituloSucesso;
        this.tituloRecusa = tituloRecusa;
        this.mensagemSucesso = mensagemSucesso;
    }

    public String segmento() {
        return segmento;
    }

    public String botao() {
        return botao;
    }

    public String tituloSucesso() {
        return tituloSucesso;
    }

    public String tituloRecusa() {
        return tituloRecusa;
    }

    public String mensagemSucesso() {
        return mensagemSucesso;
    }
}
