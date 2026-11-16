package app.ui.telas;

import app.ui.tema.Componentes;
import app.ui.tema.Componentes.BarraAbas;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Medida;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Moldura em formato de celular.
 * <ul>
 *   <li><b>Propósito:</b> centralizar uma coluna de largura máxima {@code LARGURA_APP} sobre {@code FUNDO} e trocar de tela.</li>
 *   <li><b>Áreas:</b> conteúdo da tela atual (CardLayout) e, sempre visível, a barra de navegação inferior.</li>
 *   <li><b>Componentes:</b> {@code barraAbas} com Início · Contas · Operações · Extrato · Simulação.</li>
 *   <li><b>Dados e thread:</b> não carrega dados; só alterna telas, na EDT.</li>
 *   <li><b>Estados:</b> uma aba ativa; em janela estreita a coluna ocupa tudo, em larga ficam laterais vazias.</li>
 *   <li><b>Erros:</b> não se aplica.</li>
 * </ul>
 */
public final class PainelCelular extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Abas da navegação, na ordem da barra inferior. */
    public enum Aba { INICIO, CONTAS, OPERACOES, EXTRATO, SIMULACAO }

    private final CardLayout cartas = new CardLayout();
    private final JPanel conteudo = new JPanel(cartas);
    private final BarraAbas barra;

    public PainelCelular(Map<Aba, JComponent> telas) {
        super(new GridBagLayout());
        setBackground(Cor.FUNDO);
        conteudo.setOpaque(false);
        for (Aba aba : Aba.values()) {
            conteudo.add(telas.get(aba), aba.name());
        }
        barra = Componentes.barraAbas(
                new String[] {"In\u00edcio", "Contas", "Opera\u00e7\u00f5es", "Extrato", "Simula\u00e7\u00e3o"},
                new Icone[] {Icone.INICIO, Icone.CONTA, Icone.TRANSFERENCIA, Icone.HISTORICO, Icone.CONCORRENCIA},
                indice -> mostrar(Aba.values()[indice]));

        JPanel coluna = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                // Altura 0 de propósito: se a altura preferida excedesse a janela, o GridBagLayout
                // usaria o tamanho MÍNIMO também na largura. A altura real vem de fill = VERTICAL.
                return new Dimension(Medida.LARGURA_APP, 0);
            }

            @Override
            public Dimension getMinimumSize() {
                // Quando a janela é mais estreita que LARGURA_APP o GridBagLayout usa o tamanho mínimo:
                // ele acompanha a largura disponível (nunca menos que LARGURA_COLUNA_MIN).
                int disponivel = getParent() == null ? Medida.LARGURA_COLUNA_MIN : getParent().getWidth();
                return new Dimension(Math.max(Medida.LARGURA_COLUNA_MIN, Math.min(Medida.LARGURA_APP, disponivel)), 0);
            }
        };
        coluna.setBackground(Cor.FUNDO);
        coluna.add(conteudo, BorderLayout.CENTER);
        coluna.add(barra, BorderLayout.SOUTH);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.VERTICAL;
        c.weighty = 1;
        add(coluna, c);
        mostrar(Aba.INICIO);
    }

    public void mostrar(Aba aba) {
        cartas.show(conteudo, aba.name());
        barra.selecionar(aba.ordinal());
    }

    /** Mapa vazio para quem monta as telas na ordem das abas. */
    public static Map<Aba, JComponent> mapa() {
        return new EnumMap<>(Aba.class);
    }
}
