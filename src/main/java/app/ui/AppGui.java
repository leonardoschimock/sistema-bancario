package app.ui;

import app.ui.telas.JanelaPrincipal;
import javax.swing.SwingUtilities;

/**
 * Ponto de entrada da interface gráfica (o {@code app.Main} de console continua intacto).
 * A conexão vem de BANCO_URL, BANCO_USUARIO e BANCO_SENHA.
 */
public final class AppGui {

    private AppGui() {
    }

    public static void main(String[] args) {
        // Precisam ser definidas antes de qualquer componente AWT/Swing ser criado.
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> {
            ControladorBanco controlador = ControladorBanco.doAmbiente();
            new JanelaPrincipal(controlador, new EstadoApp()).setVisible(true);
        });
    }
}
