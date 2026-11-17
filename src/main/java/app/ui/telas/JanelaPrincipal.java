package app.ui.telas;

import app.ui.ControladorBanco;
import app.ui.EstadoApp;
import app.ui.SincronizadorContas;
import app.ui.telas.PainelCelular.Aba;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Medida;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * Janela do app: tamanho fixo 402 x 874 pt (iPhone 17 Pro), sem redimensionar. Monta as telas, liga o estado
 * compartilhado e dispara a leitura inicial das contas. Não contém regra bancária.
 */
public final class JanelaPrincipal extends JFrame {
    private static final long serialVersionUID = 1L;

    private final PainelCelular celular;
    private final transient SincronizadorContas sincronizador;

    public JanelaPrincipal(ControladorBanco controlador, EstadoApp estado) {
        super("Solid Bank");
        java.awt.Image icone = app.ui.tema.Componentes.iconeDoAplicativo();
        if (icone != null) {
            setIconImage(icone);
        }
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        Map<Aba, JComponent> telas = PainelCelular.mapa();
        Consumer<Aba> navegar = aba -> celular().mostrar(aba);
        sincronizador = new SincronizadorContas(controlador, estado);
        Runnable recarregar = sincronizador::carregarContas;
        PainelOperacoes operacoes = new PainelOperacoes(controlador, estado, navegar);
        telas.put(Aba.INICIO, new PainelVisaoGeral(controlador, estado, recarregar, navegar, tipo -> {
            operacoes.selecionarTipo(tipo);
            navegar.accept(Aba.OPERACOES);
        }));
        telas.put(Aba.CONTAS, new PainelContas(controlador, estado, recarregar, navegar));
        telas.put(Aba.OPERACOES, operacoes);
        telas.put(Aba.EXTRATO, new PainelExtrato(controlador, estado, recarregar));
        telas.put(Aba.SIMULACAO, new PainelSimulacao(controlador, recarregar));
        celular = new PainelCelular(telas);
        getContentPane().setBackground(Cor.FUNDO);
        setContentPane(celular);
        setResizable(false);
        aplicarTamanhoFixo();
        setLocationRelativeTo(null);
        sincronizador.carregarContas();
    }

    /**
     * Área útil fixa de 402 x 874 pt (iPhone 17 Pro). Só se a tela não comportar essa altura a janela
     * encolhe, mantendo a mesma proporção (nunca abaixo de {@code JANELA_LARGURA_MIN}).
     */
    private void aplicarTamanhoFixo() {
        getContentPane().setPreferredSize(new Dimension(Medida.JANELA_LARGURA, Medida.JANELA_ALTURA));
        pack();
        GraphicsConfiguration tela = getGraphicsConfiguration();
        Insets reservado = Toolkit.getDefaultToolkit().getScreenInsets(tela);
        int disponivel = tela.getBounds().height - reservado.top - reservado.bottom;
        if (getHeight() > disponivel) {
            Insets moldura = getInsets();
            double fator = (disponivel - moldura.top - moldura.bottom) / (double) Medida.JANELA_ALTURA;
            int largura = Math.max(Medida.JANELA_LARGURA_MIN, (int) Math.round(Medida.JANELA_LARGURA * fator));
            int altura = (int) Math.round(largura * (double) Medida.JANELA_ALTURA / Medida.JANELA_LARGURA);
            getContentPane().setPreferredSize(new Dimension(largura, altura));
            pack();
        }
    }

    private PainelCelular celular() {
        return celular;
    }
}
