package app.ui.telas;

import app.ui.ControladorBanco;
import app.ui.EstadoApp;
import app.ui.telas.PainelCelular.Aba;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Medida;
import java.awt.Dimension;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

/**
 * Janela do app: 430 x 860 (mínimo 360 x 640), redimensionável. Monta as telas, liga o estado
 * compartilhado e dispara a leitura inicial das contas. Não contém regra bancária.
 */
public final class JanelaPrincipal extends JFrame {
    private static final long serialVersionUID = 1L;

    private final transient ControladorBanco controlador;
    private final transient EstadoApp estado;
    private final PainelCelular celular;

    public JanelaPrincipal(ControladorBanco controlador, EstadoApp estado) {
        super("Sistema Banc\u00e1rio");
        this.controlador = controlador;
        this.estado = estado;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        Map<Aba, JComponent> telas = PainelCelular.mapa();
        Consumer<Aba> navegar = aba -> celular().mostrar(aba);
        Runnable recarregar = this::carregarContas;
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
        setMinimumSize(new Dimension(Medida.JANELA_LARGURA_MIN, Medida.JANELA_ALTURA_MIN));
        setSize(Medida.JANELA_LARGURA, Medida.JANELA_ALTURA);
        setLocationRelativeTo(null);
        carregarContas();
    }

    private PainelCelular celular() {
        return celular;
    }

    /** Lê as contas do banco (SwingWorker) e entrega o resultado, real, ao estado compartilhado. */
    private void carregarContas() {
        estado.iniciarCargaContas();
        controlador.listarContas(resultado -> {
            if (resultado.ok()) {
                estado.definirContas(resultado.valor());
            } else {
                estado.definirFalhaContas(resultado.falha());
            }
        });
    }
}
