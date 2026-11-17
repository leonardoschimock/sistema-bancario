package app.ui.tema;

import app.ContaResumo;
import app.ui.Moeda;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Fonte;
import app.ui.tema.Tema.Medida;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.NavigationFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Position;

/**
 * Componentes visuais do app (SKILL §4). Cada um desenha o próprio visual com Java2D e antialiasing,
 * sem depender do Look and Feel, e só usa valores de {@link Tema}. As telas montam a interface
 * exclusivamente com as fábricas desta classe.
 */
public final class Componentes {

    /** Texto exibido no lugar de um saldo quando o usuário o oculta (apenas visual). */
    public static final String SALDO_OCULTO = "R$ \u2022\u2022\u2022\u2022";

    /** Estilos de botão (SKILL §4). {@code PERIGO} está reservado e hoje não é usado. */
    public enum Estilo { PRIMARIO, SECUNDARIO, FANTASMA, PERIGO }

    /** Estados visuais de um {@link Badge}; sempre acompanhados de texto. */
    public enum EstadoBadge { SUCESSO, INFO, ATENCAO, PERIGO }

    private Componentes() {
    }

    // =============================================================== fábricas

    public static Botao botao(String texto, Icone icone, Estilo estilo) {
        return new Botao(texto, icone, estilo);
    }

    public static AcaoCircular acaoCircular(Icone icone, String rotulo) {
        return new AcaoCircular(icone, rotulo);
    }

    /** Brasão do Solid Bank com a altura pedida (recurso {@code /app/ui/solid-bank-brasao.png}). */
    public static Logo logo(int altura) {
        return new Logo(altura);
    }

    /** Ícone do aplicativo (janela/barra de tarefas) ou {@code null} se o recurso não existir. */
    public static java.awt.Image iconeDoAplicativo() {
        return carregarImagem("/app/ui/solid-bank-icone.png");
    }

    /**
     * Cartão clicável da conta usada numa operação: avatar, titular, número e (opcional) saldo atual,
     * no estilo "Pagando com". Sem conta, mostra o convite para escolher uma.
     */
    public static Cartao cartaoContaAtual(ContaResumo conta, boolean mostrarSaldo, boolean saldoOculto) {
        Cartao cartao = new Cartao(new BorderLayout(Medida.ESP_3, 0));
        cartao.setBorder(new EmptyBorder(Medida.ESP_3, Medida.ESP_4, Medida.ESP_3, Medida.ESP_4));
        Pilha dados = pilha();
        if (conta == null) {
            dados.adicionar(rotulo("Selecione uma conta", Fonte.SUBTITULO, Cor.TEXTO_SECUNDARIO));
            cartao.add(centroVertical(dados), BorderLayout.CENTER);
            cartao.getAccessibleContext().setAccessibleName("Selecione uma conta");
        } else {
            dados.adicionar(textoQuebrado(conta.titular(), Fonte.SUBTITULO, Cor.TEXTO));
            dados.adicionar(rotulo("Conta n\u00ba " + conta.id(), Fonte.ROTULO, Cor.TEXTO_SECUNDARIO));
            String atual = null;
            if (mostrarSaldo) {
                atual = "Atual: " + (saldoOculto ? SALDO_OCULTO : Moeda.formatar(conta.saldo()));
                dados.adicionar(rotulo(atual, Fonte.ROTULO, Cor.TEXTO));
            }
            cartao.add(centroVertical(new Avatar(conta.titular(), Medida.AVATAR)), BorderLayout.WEST);
            cartao.add(centroVertical(dados), BorderLayout.CENTER);
            cartao.getAccessibleContext().setAccessibleName("Conta " + conta.id() + ", " + conta.titular()
                    + (atual == null ? "" : ", " + atual));
        }
        cartao.add(centroVertical(new JLabel(Icones.de(Icone.CHEVRON_DIREITA, Medida.ICONE_PEQ + Medida.ESP_1,
                Cor.TEXTO_SECUNDARIO))), BorderLayout.EAST);
        return cartao;
    }

    /** Folha com a lista de contas; {@code aoEscolher} roda depois que a folha fecha. */
    public static void escolherConta(Component dono, ContaResumo atual, Supplier<List<ContaResumo>> contas,
                                     BooleanSupplier saldoOculto, Consumer<ContaResumo> aoEscolher) {
        Pilha corpo = pilha();
        corpo.adicionar(rotulo("Escolher conta", Fonte.SECAO, Cor.TEXTO)).espaco(Medida.ESP_3);
        Folha[] folha = new Folha[1];
        List<ContaResumo> lista = contas.get();
        if (lista.isEmpty()) {
            corpo.adicionar(textoQuebrado("Nenhuma conta cadastrada.", Fonte.CORPO, Cor.TEXTO_SECUNDARIO));
        }
        for (ContaResumo candidata : lista) {
            boolean ativa = atual != null && atual.id() == candidata.id();
            Cartao cartao = cartaoConta(candidata, ativa, saldoOculto.getAsBoolean());
            cartao.aoClicar(() -> {
                folha[0].fechar();
                aoEscolher.accept(candidata);
            });
            corpo.adicionar(cartao).espaco(Medida.ESP_2);
        }
        folha[0] = new Folha(dono, corpo);
        folha[0].mostrar();
    }

    /** Botão circular só com ícone; {@code descricao} é o nome acessível. */
    public static BotaoIcone botaoIcone(Icone icone, String descricao, boolean sobrePrimaria) {
        return new BotaoIcone(icone, descricao, sobrePrimaria);
    }

    /** Bloco de cor primária com cantos inferiores arredondados (só a tela Início usa). */
    public static Hero hero() {
        return new Hero();
    }

    /** Aplica a margem lateral padrão das telas ao componente. */
    public static JComponent comMargemDeTela(JComponent conteudo) {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setOpaque(false);
        painel.setBorder(new EmptyBorder(0, Medida.MARGEM_TELA, 0, Medida.MARGEM_TELA));
        painel.add(conteudo, BorderLayout.CENTER);
        return painel;
    }

    /** Texto de estado (carregando, vazio, erro) centralizado, com respiro vertical. */
    public static JComponent mensagem(String texto) {
        JPanel painel = new JPanel(new BorderLayout());
        painel.setOpaque(false);
        painel.setBorder(new EmptyBorder(Medida.ESP_6, Medida.ESP_4, Medida.ESP_6, Medida.ESP_4));
        painel.add(textoQuebradoCentral(texto, Fonte.CORPO, Cor.TEXTO_SECUNDARIO), BorderLayout.CENTER);
        return painel;
    }

    public static Cartao cartao() {
        return new Cartao(new BorderLayout());
    }

    public static Cartao cartao(LayoutManager layout) {
        return new Cartao(layout);
    }

    public static CampoTexto campoTexto(String rotulo, String placeholder) {
        return new CampoTexto(rotulo, placeholder);
    }

    public static CampoValor campoValor(String rotulo) {
        return new CampoValor(rotulo);
    }

    public static CaixaSelecao caixaSelecao(String texto) {
        return new CaixaSelecao(texto);
    }

    public static Segmentos segmentos(String... itens) {
        return new Segmentos(itens);
    }

    public static Chip chipFiltro(String texto) {
        return new Chip(texto, null, false);
    }

    public static Chip chipSobrePrimaria(String texto, Icone icone) {
        return new Chip(texto, icone, true);
    }

    public static Badge badge(EstadoBadge estado, String texto) {
        return new Badge(estado, texto);
    }

    public static ItemMovimentacao itemMovimentacao(Icone icone, String descricao, String detalhe,
                                                    String valor, boolean entrada) {
        return new ItemMovimentacao(icone, descricao, detalhe, valor, entrada);
    }

    public static Avatar avatar(String nome, int tamanho) {
        return new Avatar(nome, tamanho);
    }

    public static SeletorConta seletorConta(Supplier<List<ContaResumo>> contas, BooleanSupplier saldoOculto,
                                            Consumer<ContaResumo> aoEscolher) {
        return new SeletorConta(contas, saldoOculto, aoEscolher);
    }

    public static BarraAbas barraAbas(String[] rotulos, Icone[] icones, IntConsumer aoEscolher) {
        return new BarraAbas(rotulos, icones, aoEscolher);
    }

    public static JComponent barraSuperior(String titulo, JComponent... acoes) {
        return new BarraSuperior(titulo, acoes);
    }

    public static Folha folha(Component dono, JComponent corpo) {
        return new Folha(dono, corpo);
    }

    public static JScrollPane rolagem(JComponent conteudo) {
        return montarRolagem(conteudo);
    }

    public static JLabel rotulo(String texto, Font fonte, Color cor) {
        JLabel rotulo = new JLabel(texto);
        rotulo.setFont(fonte);
        rotulo.setForeground(cor);
        return rotulo;
    }

    public static TextoQuebrado textoQuebrado(String texto, Font fonte, Color cor) {
        return new TextoQuebrado(texto, fonte, cor, SwingConstants.LEFT);
    }

    public static TextoQuebrado textoQuebradoCentral(String texto, Font fonte, Color cor) {
        return new TextoQuebrado(texto, fonte, cor, SwingConstants.CENTER);
    }

    public static Pilha pilha() {
        return new Pilha();
    }

    public static Component espaco(int altura) {
        return Box.createVerticalStrut(altura);
    }

    public static BarraProgresso progressoIndeterminado() {
        return new BarraProgresso();
    }

    /** Cartão clicável de uma conta (lista de Contas e folha de seleção). */
    public static Cartao cartaoConta(ContaResumo conta, boolean selecionada, boolean saldoOculto) {
        Cartao cartao = new Cartao(new BorderLayout(Medida.ESP_3, 0));
        cartao.setBorder(new EmptyBorder(Medida.ESP_3, Medida.ESP_4, Medida.ESP_3, Medida.ESP_4));
        cartao.setDestaque(selecionada);
        Pilha dados = pilha();
        dados.adicionar(textoQuebrado(conta.titular(), Fonte.SUBTITULO, Cor.TEXTO));
        String detalhe = "Conta n\u00ba " + conta.id() + (selecionada ? "  \u00b7  Selecionada" : "");
        dados.adicionar(textoQuebrado(detalhe, Fonte.ROTULO, selecionada ? Cor.PRIMARIA : Cor.TEXTO_SECUNDARIO));
        JLabel saldo = rotulo(saldoOculto ? SALDO_OCULTO : Moeda.formatar(conta.saldo()), Fonte.VALOR, Cor.TEXTO);
        cartao.add(centroVertical(new Avatar(conta.titular(), Medida.AVATAR)), BorderLayout.WEST);
        cartao.add(centroVertical(dados), BorderLayout.CENTER);
        cartao.add(centroVertical(saldo), BorderLayout.EAST);
        cartao.getAccessibleContext().setAccessibleName("Conta " + conta.id() + ", " + conta.titular()
                + (selecionada ? ", selecionada" : ""));
        return cartao;
    }

    /**
     * Folha de erro ou aviso: título, <b>O que aconteceu</b>, <b>O que fazer</b> e detalhe técnico recolhido.
     *
     * @param icone {@code ERRO} (falha técnica) ou {@code ALERTA} (recusa/aviso)
     */
    public static void mostrarErro(Component dono, Icone icone, String titulo, String oQueAconteceu,
                                   String oQueFazer, String detalheTecnico) {
        Color cor = icone == Icone.ALERTA ? Cor.ATENCAO : Cor.PERIGO;
        Pilha corpo = pilha();
        JLabel cabecalho = rotulo(titulo, Fonte.SECAO, Cor.TEXTO);
        cabecalho.setIcon(Icones.de(icone, Medida.ICONE_GRANDE, cor));
        cabecalho.setIconTextGap(Medida.ESP_3);
        corpo.adicionar(cabecalho).espaco(Medida.ESP_4);
        corpo.adicionar(rotulo("O que aconteceu", Fonte.ROTULO, Cor.TEXTO_SECUNDARIO)).espaco(Medida.ESP_1);
        corpo.adicionar(textoQuebrado(oQueAconteceu, Fonte.CORPO, Cor.TEXTO)).espaco(Medida.ESP_3);
        corpo.adicionar(rotulo("O que fazer", Fonte.ROTULO, Cor.TEXTO_SECUNDARIO)).espaco(Medida.ESP_1);
        corpo.adicionar(textoQuebrado(oQueFazer, Fonte.CORPO, Cor.TEXTO)).espaco(Medida.ESP_3);

        Folha folha = new Folha(dono, corpo);
        if (detalheTecnico != null && !detalheTecnico.isBlank()) {
            Botao alternar = botao("Detalhes t\u00e9cnicos", Icone.CHEVRON_BAIXO, Estilo.FANTASMA);
            TextoQuebrado detalhe = new TextoQuebrado(detalheTecnico, Fonte.DADO, Cor.TEXTO_SECUNDARIO,
                    SwingConstants.LEFT);
            Cartao caixa = new Cartao(new BorderLayout());
            caixa.setBorder(new EmptyBorder(Medida.ESP_3, Medida.ESP_3, Medida.ESP_3, Medida.ESP_3));
            caixa.add(detalhe, BorderLayout.CENTER);
            caixa.setVisible(false);
            alternar.addActionListener(e -> {
                caixa.setVisible(!caixa.isVisible());
                folha.reajustar();
            });
            corpo.adicionar(alternar).espaco(Medida.ESP_2);
            corpo.adicionar(caixa).espaco(Medida.ESP_3);
        }
        Botao fechar = botao("Fechar", null, Estilo.PRIMARIO);
        fechar.addActionListener(e -> folha.fechar());
        corpo.adicionar(fechar);
        folha.mostrar();
    }

    // =============================================================== utilitários de desenho

    static Graphics2D suave(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    /** Retângulo arredondado; {@code raio} 999 vira pílula. */
    static RoundRectangle2D forma(float x, float y, float largura, float altura, int raio) {
        float arco = Math.min(raio * 2f, Math.min(largura, altura));
        return new RoundRectangle2D.Float(x, y, largura, altura, arco, arco);
    }

    /** Contorno de {@code espessura} px desenhado para dentro da área. */
    static void contorno(Graphics2D g2, Color cor, int largura, int altura, int raio, float espessura) {
        float meio = espessura / 2f;
        g2.setColor(cor);
        g2.setStroke(new BasicStroke(espessura));
        g2.draw(forma(meio, meio, largura - espessura, altura - espessura, raio));
    }

    static String elipse(String texto, FontMetrics fm, int maximo) {
        if (fm.stringWidth(texto) <= maximo) {
            return texto;
        }
        String reticencias = "\u2026";
        int largura = fm.stringWidth(reticencias);
        int n = texto.length();
        while (n > 0 && fm.stringWidth(texto.substring(0, n)) + largura > maximo) {
            n--;
        }
        return texto.substring(0, n).stripTrailing() + reticencias;
    }

    /** Quebra por palavras (e por caractere, se uma palavra sozinha não couber). */
    static List<String> quebrar(String texto, FontMetrics fm, int largura) {
        int limite = Math.max(largura, 1);
        List<String> linhas = new ArrayList<>();
        for (String paragrafo : texto.split("\n", -1)) {
            StringBuilder atual = new StringBuilder();
            for (String palavra : paragrafo.split(" ", -1)) {
                String candidata = atual.length() == 0 ? palavra : atual + " " + palavra;
                if (fm.stringWidth(candidata) <= limite) {
                    atual = new StringBuilder(candidata);
                    continue;
                }
                if (atual.length() > 0) {
                    linhas.add(atual.toString());
                    atual = new StringBuilder();
                }
                String resto = palavra;
                while (fm.stringWidth(resto) > limite && resto.length() > 1) {
                    int n = resto.length();
                    while (n > 1 && fm.stringWidth(resto.substring(0, n)) > limite) {
                        n--;
                    }
                    linhas.add(resto.substring(0, n));
                    resto = resto.substring(n);
                }
                atual.append(resto);
            }
            linhas.add(atual.toString());
        }
        return linhas;
    }

    private static JPanel centroVertical(JComponent componente) {
        JPanel painel = new JPanel(new GridBagLayout());
        painel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        painel.add(componente, c);
        return painel;
    }

    private static void ativarComEnter(JComponent componente) {
        componente.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "ativar");
        componente.getActionMap().put("ativar", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (componente instanceof javax.swing.AbstractButton botao) {
                    botao.doClick();
                }
            }
        });
    }

    private static void configurarBotao(javax.swing.AbstractButton botao) {
        botao.setContentAreaFilled(false);
        botao.setBorderPainted(false);
        botao.setFocusPainted(false);
        botao.setOpaque(false);
        botao.setRolloverEnabled(true);
        botao.setMargin(new Insets(0, 0, 0, 0));
        botao.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ativarComEnter(botao);
    }

    /** JComponent puro não tem contexto de acessibilidade (devolve null); esta base o fornece. */
    private abstract static class ComponenteAcessivel extends JComponent {
        private static final long serialVersionUID = 1L;

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() { };
            }
            return accessibleContext;
        }
    }

    // =============================================================== layout auxiliar

    /** Coluna vertical transparente (BoxLayout) com filhos alinhados à esquerda. */
    public static final class Pilha extends JPanel {
        private static final long serialVersionUID = 1L;

        Pilha() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        public Pilha adicionar(JComponent componente) {
            componente.setAlignmentX(LEFT_ALIGNMENT);
            add(componente);
            return this;
        }

        public Pilha espaco(int altura) {
            add(Box.createVerticalStrut(altura));
            return this;
        }
    }

    /** Conteúdo de rolagem: acompanha a largura da janela e empurra o conteúdo para o topo. */
    private static final class ConteudoRolavel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        ConteudoRolavel(JComponent conteudo) {
            super(new BorderLayout());
            setOpaque(false);
            add(conteudo, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visivel, int orientacao, int direcao) {
            return Medida.PASSO_ROLAGEM;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visivel, int orientacao, int direcao) {
            return Math.max(visivel.height - Medida.ESP_8, Medida.PASSO_ROLAGEM);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JScrollPane montarRolagem(JComponent conteudo) {
        JScrollPane rolagem = new JScrollPane(new ConteudoRolavel(conteudo),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        rolagem.setBorder(null);
        rolagem.setOpaque(false);
        rolagem.getViewport().setOpaque(false);
        rolagem.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        rolagem.setWheelScrollingEnabled(true);
        JScrollBar barra = rolagem.getVerticalScrollBar();
        barra.setUI(new BarraFinaUI());
        barra.setUnitIncrement(Medida.PASSO_ROLAGEM);
        barra.setPreferredSize(new Dimension(Medida.LARGURA_AREA_ROLAGEM, 0));
        barra.setOpaque(false);
        return rolagem;
    }

    private static final class BarraFinaUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return botaoVazio();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return botaoVazio();
        }

        private static JButton botaoVazio() {
            JButton botao = new JButton();
            botao.setPreferredSize(new Dimension(0, 0));
            botao.setFocusable(false);
            return botao;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle area) {
            // sem trilho: barra discreta
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle area) {
            if (area.isEmpty() || !c.isEnabled()) {
                return;
            }
            Graphics2D g2 = suave(g);
            try {
                g2.setColor(isThumbRollover() || isDragging ? Cor.TEXTO_DESATIVADO : Cor.BORDA);
                int x = area.x + (area.width - Medida.LARGURA_ROLAGEM) / 2;
                g2.fill(forma(x, area.y, Medida.LARGURA_ROLAGEM, area.height, Medida.RAIO_PILULA));
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(Medida.LARGURA_ROLAGEM, Medida.ESP_8);
        }
    }

    // =============================================================== textos

    /** Texto que quebra linha conforme a largura recebida (altura preferida depende da largura). */
    public static final class TextoQuebrado extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private String texto;
        private final int alinhamento;
        private int larguraAnterior = -1;

        TextoQuebrado(String texto, Font fonte, Color cor, int alinhamento) {
            this.texto = texto;
            this.alinhamento = alinhamento;
            setFont(fonte);
            setForeground(cor);
            setOpaque(false);
            getAccessibleContext().setAccessibleName(texto);
        }

        public void setTexto(String novo) {
            this.texto = novo;
            getAccessibleContext().setAccessibleName(novo);
            revalidate();
            repaint();
        }

        private int larguraDisponivel() {
            return getWidth() > 0 ? getWidth() : Medida.LARGURA_APP - 2 * Medida.MARGEM_TELA;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            List<String> linhas = quebrar(texto, fm, larguraDisponivel());
            int natural = 0;
            for (String linha : linhas) {
                natural = Math.max(natural, fm.stringWidth(linha));
            }
            return new Dimension(Math.min(natural, Medida.LARGURA_APP), linhas.size() * fm.getHeight());
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(1, getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int largura, int altura) {
            super.setBounds(x, y, largura, altura);
            if (largura != larguraAnterior) {
                larguraAnterior = largura;
                // adiado: um revalidate() durante o próprio layout é anulado quando ele termina
                SwingUtilities.invokeLater(this::revalidate);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                g2.setFont(getFont());
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int y = fm.getAscent();
                for (String linha : quebrar(texto, fm, getWidth())) {
                    int x = alinhamento == SwingConstants.CENTER ? (getWidth() - fm.stringWidth(linha)) / 2 : 0;
                    g2.drawString(linha, x, y);
                    y += fm.getHeight();
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // =============================================================== botões

    /** Botão do app: primário, secundário, fantasma ou perigo (reservado). */
    public static final class Botao extends JButton {
        private static final long serialVersionUID = 1L;
        private final Estilo estilo;
        private final Icone icone;
        private int altura = Medida.ALTURA_BOTAO;

        /** Recuo horizontal interno: botões fantasma compactos ficam rentes à margem do conteúdo. */
        private int paddingX() {
            if (altura == Medida.ALTURA_BOTAO) {
                return Medida.ESP_6;
            }
            return estilo == Estilo.FANTASMA ? Medida.ESP_1 : Medida.ESP_4;
        }

        /** Quanto do botão é só respiro lateral e deve "invadir" a margem para alinhar o texto. */
        public int recuoVisual() {
            return estilo == Estilo.FANTASMA && altura != Medida.ALTURA_BOTAO ? paddingX() : 0;
        }

        /** Versão mais baixa, para barras superiores. */
        public Botao compacto() {
            this.altura = Medida.ALTURA_BOTAO_COMPACTO;
            revalidate();
            return this;
        }

        Botao(String texto, Icone icone, Estilo estilo) {
            super(texto);
            this.estilo = estilo;
            this.icone = icone;
            setFont(Fonte.CORPO);
            configurarBotao(this);
        }

        private Color corTexto() {
            if (!isEnabled()) {
                return Cor.TEXTO_DESATIVADO;
            }
            return switch (estilo) {
                case PRIMARIO, PERIGO -> Cor.SOBRE_PRIMARIA;
                case SECUNDARIO -> Cor.TEXTO;
                case FANTASMA -> Cor.PRIMARIA;
            };
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int largura = fm.stringWidth(getText()) + 2 * paddingX();
            if (icone != null) {
                largura += Medida.ICONE + Medida.ESP_2;
            }
            return new Dimension(largura, altura);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(altura, altura);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, altura);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                boolean ativo = isEnabled();
                boolean destaque = ativo && (getModel().isPressed() || getModel().isRollover());
                Color fundo = null;
                Color borda = null;
                switch (estilo) {
                    case PRIMARIO -> fundo = !ativo ? Cor.SUPERFICIE_ELEVADA
                            : destaque ? Cor.PRIMARIA_HOVER : Cor.PRIMARIA;
                    case PERIGO -> fundo = !ativo ? Cor.SUPERFICIE_ELEVADA : Cor.PERIGO;
                    case SECUNDARIO -> {
                        fundo = destaque ? Cor.SUPERFICIE_ELEVADA : Cor.SUPERFICIE;
                        borda = Cor.BORDA;
                    }
                    case FANTASMA -> fundo = destaque ? Cor.PRIMARIA_FUNDO : null;
                }
                if (fundo != null) {
                    g2.setColor(fundo);
                    g2.fill(forma(0, 0, w, h, Medida.RAIO_CONTROLE));
                }
                if (borda != null) {
                    contorno(g2, borda, w, h, Medida.RAIO_CONTROLE, Medida.BORDA);
                }
                Color corTexto = corTexto();
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int reservaIcone = icone != null ? Medida.ICONE + Medida.ESP_2 : 0;
                String texto = elipse(getText(), fm, w - 2 * paddingX() - reservaIcone);
                int conteudo = fm.stringWidth(texto) + reservaIcone;
                int x = (w - conteudo) / 2;
                if (icone != null) {
                    Icones.de(icone, Medida.ICONE, corTexto).paintIcon(this, g2, x, (h - Medida.ICONE) / 2);
                    x += reservaIcone;
                }
                g2.setColor(corTexto);
                g2.drawString(texto, x, (h - fm.getHeight()) / 2 + fm.getAscent());
                if (isFocusOwner() && ativo) {
                    boolean cheio = estilo == Estilo.PRIMARIO || estilo == Estilo.PERIGO;
                    int recuo = cheio ? Medida.ESP_1 : 0;
                    g2.setColor(cheio ? Cor.SOBRE_PRIMARIA_SUAVE : Cor.PRIMARIA);
                    g2.setStroke(new BasicStroke(Medida.BORDA_FOCO));
                    g2.draw(forma(recuo + 1, recuo + 1, w - 2 * recuo - 2, h - 2 * recuo - 2, Medida.RAIO_CONTROLE));
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Ação rápida: círculo com ícone e rótulo abaixo. */
    public static final class AcaoCircular extends JButton {
        private static final long serialVersionUID = 1L;
        private final Icone icone;
        private final String rotulo;

        AcaoCircular(Icone icone, String rotulo) {
            super(rotulo);
            this.icone = icone;
            this.rotulo = rotulo;
            setFont(Fonte.ROTULO);
            configurarBotao(this);
            getAccessibleContext().setAccessibleName(rotulo);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Medida.LARGURA_ACAO, Medida.ACAO_CIRCULO + Medida.ESP_2 + fm.getHeight());
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int x = (w - Medida.ACAO_CIRCULO) / 2;
                boolean destaque = getModel().isPressed() || getModel().isRollover();
                g2.setColor(Cor.PRIMARIA_FUNDO);
                g2.fill(new Ellipse2D.Float(x, 0, Medida.ACAO_CIRCULO, Medida.ACAO_CIRCULO));
                if (destaque || isFocusOwner()) {
                    g2.setColor(Cor.PRIMARIA);
                    g2.setStroke(new BasicStroke(Medida.BORDA_FOCO));
                    g2.draw(new Ellipse2D.Float(x + 1, 1, Medida.ACAO_CIRCULO - 2, Medida.ACAO_CIRCULO - 2));
                }
                Icones.de(icone, Medida.ICONE_ACAO, Cor.PRIMARIA).paintIcon(this, g2,
                        x + (Medida.ACAO_CIRCULO - Medida.ICONE_ACAO) / 2, (Medida.ACAO_CIRCULO - Medida.ICONE_ACAO) / 2);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                String texto = elipse(rotulo, fm, w);
                g2.setColor(Cor.TEXTO);
                g2.drawString(texto, (w - fm.stringWidth(texto)) / 2,
                        Medida.ACAO_CIRCULO + Medida.ESP_2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    // =============================================================== logo

    private static final java.util.Map<String, java.awt.image.BufferedImage> IMAGENS = new java.util.HashMap<>();

    private static java.awt.image.BufferedImage carregarImagem(String recurso) {
        return IMAGENS.computeIfAbsent(recurso, r -> {
            try (java.io.InputStream in = Componentes.class.getResourceAsStream(r)) {
                return in == null ? null : javax.imageio.ImageIO.read(in);
            } catch (java.io.IOException e) {
                return null;
            }
        });
    }

    /** Reduz em passos de metade (qualidade melhor que um único salto) até a altura pedida. */
    private static java.awt.image.BufferedImage reduzir(java.awt.image.BufferedImage origem, int altura) {
        java.awt.image.BufferedImage atual = origem;
        while (atual.getHeight() / 2 >= altura) {
            atual = passo(atual, atual.getWidth() / 2, atual.getHeight() / 2);
        }
        int largura = Math.max(1, Math.round(atual.getWidth() * (altura / (float) atual.getHeight())));
        return passo(atual, largura, altura);
    }

    private static java.awt.image.BufferedImage passo(java.awt.image.BufferedImage origem, int largura, int altura) {
        java.awt.image.BufferedImage destino = new java.awt.image.BufferedImage(largura, altura,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = destino.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(origem, 0, 0, largura, altura, null);
        } finally {
            g2.dispose();
        }
        return destino;
    }

    /** Brasão do banco. Sem o recurso, cai para o ícone vetorial de banco. */
    public static final class Logo extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private final int altura;
        private transient java.awt.image.BufferedImage imagem;

        Logo(int altura) {
            this.altura = altura;
            java.awt.image.BufferedImage original = carregarImagem("/app/ui/solid-bank-brasao.png");
            this.imagem = original == null ? null : reduzir(original, altura);
            getAccessibleContext().setAccessibleName("Solid Bank");
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(imagem == null ? altura : imagem.getWidth(), altura);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                if (imagem != null) {
                    g2.drawImage(imagem, 0, 0, null);
                } else {
                    Icones.de(Icone.BANCO, altura, Cor.SOBRE_PRIMARIA).paintIcon(this, g2, 0, 0);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // =============================================================== hero e botão de ícone

    /** Bloco chapado em {@code PRIMARIA} (sem gradiente) com cantos inferiores arredondados. */
    public static final class Hero extends JPanel {
        private static final long serialVersionUID = 1L;

        Hero() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(Medida.ESP_5, Medida.MARGEM_TELA, Medida.HERO_ESPACO_BAIXO, Medida.MARGEM_TELA));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                g2.setColor(Cor.PRIMARIA);
                // a forma excede o topo: só os cantos de baixo ficam visíveis e arredondados
                g2.fill(forma(0, -Medida.RAIO_CARTAO * 2f, getWidth(), getHeight() + Medida.RAIO_CARTAO * 2f,
                        Medida.RAIO_CARTAO));
            } finally {
                g2.dispose();
            }
        }
    }

    /** Botão circular só com ícone. */
    public static final class BotaoIcone extends JButton {
        private static final long serialVersionUID = 1L;
        private final Icone icone;
        private final boolean sobrePrimaria;

        BotaoIcone(Icone icone, String descricao, boolean sobrePrimaria) {
            this.icone = icone;
            this.sobrePrimaria = sobrePrimaria;
            configurarBotao(this);
            getAccessibleContext().setAccessibleName(descricao);
            setToolTipText(descricao);
        }

        public void setIcone(Icone novo, String descricao) {
            getAccessibleContext().setAccessibleName(descricao);
            setToolTipText(descricao);
            this.iconeAtual = novo;
            repaint();
        }

        private Icone iconeAtual;

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Medida.BOTAO_ICONE, Medida.BOTAO_ICONE);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                boolean destaque = getModel().isRollover() || getModel().isPressed();
                if (destaque) {
                    g2.setColor(sobrePrimaria ? Cor.PRIMARIA_HOVER : Cor.SUPERFICIE_ELEVADA);
                    g2.fill(new Ellipse2D.Float(0, 0, w, h));
                }
                if (isFocusOwner()) {
                    g2.setColor(sobrePrimaria ? Cor.SOBRE_PRIMARIA_SUAVE : Cor.PRIMARIA);
                    g2.setStroke(new BasicStroke(Medida.BORDA_FOCO));
                    g2.draw(new Ellipse2D.Float(1, 1, w - 2, h - 2));
                }
                Icones.de(iconeAtual == null ? icone : iconeAtual, Medida.ICONE,
                                sobrePrimaria ? Cor.SOBRE_PRIMARIA : Cor.TEXTO_SECUNDARIO)
                        .paintIcon(this, g2, (w - Medida.ICONE) / 2, (h - Medida.ICONE) / 2);
            } finally {
                g2.dispose();
            }
        }
    }

    // =============================================================== cartão

    /** Superfície arredondada com borda sutil. Opcionalmente clicável e com destaque. */
    public static class Cartao extends JPanel {
        private static final long serialVersionUID = 1L;
        private boolean destaque;
        private boolean sobre;
        private Runnable aoClicar;

        Cartao(LayoutManager layout) {
            super(layout);
            setOpaque(false);
            setBorder(new EmptyBorder(Medida.ESP_4, Medida.ESP_4, Medida.ESP_4, Medida.ESP_4));
        }

        public void setDestaque(boolean destaque) {
            this.destaque = destaque;
            repaint();
        }

        /** Torna o cartão clicável (mouse, Enter e Espaço). */
        public Cartao aoClicar(Runnable acao) {
            this.aoClicar = acao;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    sobre = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    sobre = false;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (contains(e.getPoint()) && SwingUtilities.isLeftMouseButton(e) && isEnabled()) {
                        aoClicar.run();
                    }
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
            for (int tecla : new int[] {KeyEvent.VK_ENTER, KeyEvent.VK_SPACE}) {
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(tecla, 0), "clicar");
            }
            getActionMap().put("clicar", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    aoClicar.run();
                }
            });
            return this;
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                g2.setColor(aoClicar != null && sobre ? Cor.SUPERFICIE_ELEVADA : Cor.SUPERFICIE);
                g2.fill(forma(0, 0, w, h, Medida.RAIO_CARTAO));
                boolean foco = aoClicar != null && isFocusOwner();
                if (destaque || foco) {
                    contorno(g2, Cor.PRIMARIA, w, h, Medida.RAIO_CARTAO, Medida.BORDA_FOCO);
                } else {
                    contorno(g2, Cor.BORDA, w, h, Medida.RAIO_CARTAO, Medida.BORDA);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // =============================================================== campos

    /** Caixa arredondada que envolve um campo de texto e mostra foco e erro. */
    private static final class Caixa extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JTextField campo;
        private final int altura;
        private boolean comErro;

        Caixa(JTextField campo, int altura) {
            super(new BorderLayout());
            this.campo = campo;
            this.altura = altura;
            setOpaque(false);
            setBorder(new EmptyBorder(0, Medida.ESP_4, 0, Medida.ESP_4));
            add(campo, BorderLayout.CENTER);
            campo.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        void setComErro(boolean comErro) {
            this.comErro = comErro;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, altura);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, altura);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                g2.setColor(Cor.SUPERFICIE_ELEVADA);
                g2.fill(forma(0, 0, getWidth(), getHeight(), Medida.RAIO_CONTROLE));
                if (comErro) {
                    contorno(g2, Cor.PERIGO, getWidth(), getHeight(), Medida.RAIO_CONTROLE, Medida.BORDA_FOCO);
                } else if (campo.isFocusOwner()) {
                    contorno(g2, Cor.PRIMARIA, getWidth(), getHeight(), Medida.RAIO_CONTROLE, Medida.BORDA_FOCO);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    private static void estilizarCampo(JTextField campo, Font fonte) {
        campo.setBorder(null);
        campo.setOpaque(false);
        campo.setFont(fonte);
        campo.setForeground(Cor.TEXTO);
        campo.setDisabledTextColor(Cor.TEXTO_DESATIVADO);
        campo.setCaretColor(Cor.PRIMARIA);
        campo.setSelectionColor(Cor.PRIMARIA_FUNDO);
        campo.setSelectedTextColor(Cor.TEXTO);
    }

    private static final class CampoComDica extends JTextField {
        private static final long serialVersionUID = 1L;
        private final String dica;

        CampoComDica(String dica) {
            this.dica = dica;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (dica != null && !dica.isEmpty() && getText().isEmpty()) {
                Graphics2D g2 = suave(g);
                try {
                    g2.setFont(getFont());
                    g2.setColor(Cor.TEXTO_DESATIVADO);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(elipse(dica, fm, getWidth()), getInsets().left,
                            (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                } finally {
                    g2.dispose();
                }
            }
        }
    }

    private static final class SomenteDigitos extends DocumentFilter {
        private final int maximo;

        SomenteDigitos(int maximo) {
            this.maximo = maximo;
        }

        @Override
        public void insertString(FilterBypass fb, int deslocamento, String texto, AttributeSet atributos)
                throws BadLocationException {
            replace(fb, deslocamento, 0, texto, atributos);
        }

        @Override
        public void replace(FilterBypass fb, int deslocamento, int tamanho, String texto, AttributeSet atributos)
                throws BadLocationException {
            String digitos = texto == null ? "" : texto.replaceAll("\\D", "");
            if (fb.getDocument().getLength() - tamanho + digitos.length() > maximo) {
                return;
            }
            super.replace(fb, deslocamento, tamanho, digitos, atributos);
        }
    }

    /** Campo de texto com rótulo acima, placeholder de exemplo e mensagem de erro abaixo. */
    public static final class CampoTexto extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JTextField campo;
        private final Caixa caixa;
        private final TextoQuebrado erro;
        private final Component folgaErro = Box.createVerticalStrut(Medida.ESP_1);

        CampoTexto(String rotulo, String placeholder) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JLabel legenda = rotulo(rotulo, Fonte.ROTULO, Cor.TEXTO_SECUNDARIO);
            legenda.setAlignmentX(LEFT_ALIGNMENT);
            campo = new CampoComDica(placeholder);
            estilizarCampo(campo, Fonte.CORPO);
            campo.putClientProperty("AccessibleName", rotulo);
            legenda.setLabelFor(campo);
            caixa = new Caixa(campo, Medida.ALTURA_CAMPO);
            caixa.setAlignmentX(LEFT_ALIGNMENT);
            erro = textoQuebrado(" ", Fonte.ROTULO, Cor.PERIGO);
            erro.setAlignmentX(LEFT_ALIGNMENT);
            erro.setVisible(false);
            folgaErro.setVisible(false);
            add(legenda);
            add(Box.createVerticalStrut(Medida.ESP_1));
            add(caixa);
            add(folgaErro);
            add(erro);
        }

        /** Aceita só dígitos, até {@code maximo} caracteres. */
        public CampoTexto somenteDigitos(int maximo) {
            ((AbstractDocument) campo.getDocument()).setDocumentFilter(new SomenteDigitos(maximo));
            return this;
        }

        public String texto() {
            return campo.getText();
        }

        public void setTexto(String texto) {
            campo.setText(texto);
        }

        public void mostrarErro(String mensagem) {
            erro.setTexto(mensagem);
            erro.setVisible(true);
            folgaErro.setVisible(true);
            caixa.setComErro(true);
            revalidate();
        }

        public void limparErro() {
            erro.setVisible(false);
            folgaErro.setVisible(false);
            caixa.setComErro(false);
            revalidate();
        }

        /** Executado quando o usuário aperta Enter no campo. */
        public void aoConfirmar(Runnable acao) {
            campo.addActionListener(e -> acao.run());
        }

        @Override
        public void setEnabled(boolean ativo) {
            super.setEnabled(ativo);
            campo.setEnabled(ativo);
            caixa.repaint();
        }

        @Override
        public boolean requestFocusInWindow() {
            return campo.requestFocusInWindow();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * Campo de valor grande e centralizado com máscara pt-BR: os dígitos entram da direita para a
     * esquerda (centavos), então só existe valor válido, com no máximo 2 casas e nunca negativo.
     */
    public static final class CampoValor extends JPanel {
        private static final long serialVersionUID = 1L;
        private final JTextField campo = new JTextField();
        private final Caixa caixa;
        private final TextoQuebrado erro;
        private final Component folgaErro = Box.createVerticalStrut(Medida.ESP_1);
        private long centavos;

        CampoValor(String rotulo) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JLabel legenda = rotulo(rotulo, Fonte.ROTULO, Cor.TEXTO_SECUNDARIO);
            legenda.setAlignmentX(LEFT_ALIGNMENT);
            estilizarCampo(campo, Fonte.VALOR_GRANDE);
            campo.setHorizontalAlignment(SwingConstants.CENTER);
            campo.setText(Moeda.formatar(BigDecimal.ZERO));
            campo.setForeground(Cor.TEXTO_DESATIVADO);
            campo.putClientProperty("AccessibleName", rotulo);
            legenda.setLabelFor(campo);
            ((AbstractDocument) campo.getDocument()).setDocumentFilter(new FiltroValor());
            campo.setNavigationFilter(new NavigationFilter() {
                @Override
                public void setDot(FilterBypass fb, int ponto, Position.Bias lado) {
                    fb.setDot(campo.getDocument().getLength(), lado);
                }

                @Override
                public void moveDot(FilterBypass fb, int ponto, Position.Bias lado) {
                    fb.moveDot(campo.getDocument().getLength(), lado);
                }
            });
            campo.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    ajustarFonte();
                }
            });
            caixa = new Caixa(campo, Medida.ALTURA_CAMPO_VALOR);
            caixa.setAlignmentX(LEFT_ALIGNMENT);
            erro = textoQuebrado(" ", Fonte.ROTULO, Cor.PERIGO);
            erro.setAlignmentX(LEFT_ALIGNMENT);
            erro.setVisible(false);
            folgaErro.setVisible(false);
            add(legenda);
            add(Box.createVerticalStrut(Medida.ESP_1));
            add(caixa);
            add(folgaErro);
            add(erro);
        }

        /** O valor digitado (0,00 se nada foi digitado). */
        public BigDecimal valor() {
            return Moeda.deCentavos(centavos);
        }

        /** Define o valor (no máximo 2 casas decimais). */
        public void setValor(BigDecimal valor) {
            campo.setText(Moeda.formatar(valor));
        }

        public void limpar() {
            setValor(BigDecimal.ZERO);
            limparErro();
        }

        public void mostrarErro(String mensagem) {
            erro.setTexto(mensagem);
            erro.setVisible(true);
            folgaErro.setVisible(true);
            caixa.setComErro(true);
            revalidate();
        }

        public void limparErro() {
            erro.setVisible(false);
            folgaErro.setVisible(false);
            caixa.setComErro(false);
            revalidate();
        }

        public void aoConfirmar(Runnable acao) {
            campo.addActionListener(e -> acao.run());
        }

        @Override
        public void setEnabled(boolean ativo) {
            super.setEnabled(ativo);
            campo.setEnabled(ativo);
            caixa.repaint();
        }

        @Override
        public boolean requestFocusInWindow() {
            return campo.requestFocusInWindow();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }

        private void aplicar(DocumentFilter.FilterBypass fb, long novosCentavos) throws BadLocationException {
            centavos = novosCentavos;
            fb.replace(0, fb.getDocument().getLength(), Moeda.formatar(Moeda.deCentavos(novosCentavos)), null);
            campo.setForeground(novosCentavos == 0 ? Cor.TEXTO_DESATIVADO : Cor.TEXTO);
            ajustarFonte();
        }

        /** Encolhe a fonte (até um mínimo) para o valor nunca ficar cortado. */
        private void ajustarFonte() {
            int disponivel = campo.getWidth();
            if (disponivel <= 0) {
                return;
            }
            float tamanho = Fonte.VALOR_GRANDE.getSize2D();
            Font fonte = Fonte.VALOR_GRANDE;
            while (tamanho > Fonte.VALOR_GRANDE_MINIMO) {
                fonte = Fonte.VALOR_GRANDE.deriveFont(tamanho);
                if (campo.getFontMetrics(fonte).stringWidth(campo.getText()) <= disponivel) {
                    break;
                }
                tamanho -= Fonte.PASSO_ENCOLHER;
            }
            campo.setFont(fonte);
        }

        private final class FiltroValor extends DocumentFilter {
            @Override
            public void insertString(FilterBypass fb, int deslocamento, String texto, AttributeSet atributos)
                    throws BadLocationException {
                replace(fb, deslocamento, 0, texto, atributos);
            }

            @Override
            public void remove(FilterBypass fb, int deslocamento, int tamanho) throws BadLocationException {
                replace(fb, deslocamento, tamanho, "", null);
            }

            @Override
            public void replace(FilterBypass fb, int deslocamento, int tamanho, String texto,
                                AttributeSet atributos) throws BadLocationException {
                Document documento = fb.getDocument();
                String atual = documento.getText(0, documento.getLength());
                String inserido = texto == null ? "" : texto;
                long novos;
                java.util.Optional<BigDecimal> colado = inserido.length() > 1
                        ? Moeda.tentarParse(inserido) : java.util.Optional.empty();
                if (colado.isPresent() && colado.get().signum() >= 0) {
                    novos = Moeda.paraCentavos(colado.get());
                } else {
                    String novo = atual.substring(0, deslocamento) + inserido + atual.substring(deslocamento + tamanho);
                    String digitos = novo.replaceAll("\\D", "").replaceFirst("^0+(?=\\d)", "");
                    if (digitos.length() > Medida.DIGITOS_VALOR) {
                        return;
                    }
                    novos = digitos.isEmpty() ? 0 : Long.parseLong(digitos);
                }
                if (Long.toString(novos).length() > Medida.DIGITOS_VALOR) {
                    return;
                }
                aplicar(fb, novos);
            }
        }
    }

    /** Caixa de seleção com texto que quebra linha. */
    public static final class CaixaSelecao extends JCheckBox {
        private static final long serialVersionUID = 1L;
        private int larguraAnterior = -1;

        CaixaSelecao(String texto) {
            super(texto);
            setFont(Fonte.CORPO);
            configurarBotao(this);
        }

        private int larguraTexto(int total) {
            return Math.max(total - Medida.CAIXA - Medida.ESP_3, 1);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int total = getWidth() > 0 ? getWidth() : Medida.LARGURA_APP - 2 * Medida.MARGEM_TELA;
            int linhas = quebrar(getText(), fm, larguraTexto(total)).size();
            return new Dimension(total, Math.max(Medida.CAIXA, linhas * fm.getHeight()) + Medida.ESP_2);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(Medida.CAIXA, getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        public void setBounds(int x, int y, int largura, int altura) {
            super.setBounds(x, y, largura, altura);
            if (largura != larguraAnterior) {
                larguraAnterior = largura;
                // adiado: um revalidate() durante o próprio layout é anulado quando ele termina
                SwingUtilities.invokeLater(this::revalidate);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                boolean ativo = isEnabled();
                int y = Medida.ESP_1;
                if (isSelected()) {
                    g2.setColor(ativo ? Cor.PRIMARIA : Cor.TEXTO_DESATIVADO);
                    g2.fill(forma(0, y, Medida.CAIXA, Medida.CAIXA, Medida.ESP_2));
                    Icones.de(Icone.OK, Medida.ICONE_PEQ, Cor.SOBRE_PRIMARIA).paintIcon(this, g2,
                            (Medida.CAIXA - Medida.ICONE_PEQ) / 2, y + (Medida.CAIXA - Medida.ICONE_PEQ) / 2);
                } else {
                    g2.setColor(Cor.SUPERFICIE);
                    g2.fill(forma(0, y, Medida.CAIXA, Medida.CAIXA, Medida.ESP_2));
                    g2.translate(0, y);
                    contorno(g2, ativo ? Cor.TEXTO_SECUNDARIO : Cor.BORDA, Medida.CAIXA, Medida.CAIXA,
                            Medida.ESP_2, Medida.BORDA_FOCO);
                    g2.translate(0, -y);
                }
                if (isFocusOwner()) {
                    g2.setColor(Cor.PRIMARIA);
                    g2.setStroke(new BasicStroke(Medida.BORDA_FOCO));
                    g2.draw(forma(-Medida.ESP_1 / 2f, y - Medida.ESP_1 / 2f, Medida.CAIXA + Medida.ESP_1,
                            Medida.CAIXA + Medida.ESP_1, Medida.ESP_2));
                }
                g2.setFont(getFont());
                g2.setColor(ativo ? Cor.TEXTO : Cor.TEXTO_DESATIVADO);
                FontMetrics fm = g2.getFontMetrics();
                int ty = y + fm.getAscent();
                for (String linha : quebrar(getText(), fm, larguraTexto(getWidth()))) {
                    g2.drawString(linha, Medida.CAIXA + Medida.ESP_3, ty);
                    ty += fm.getHeight();
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // =============================================================== seleção e rótulos

    /** Controle segmentado: um item ativo por vez. */
    public static final class Segmentos extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private final String[] itens;
        private int selecionado;
        private IntConsumer aoMudar = i -> { };

        Segmentos(String... itens) {
            this.itens = itens.clone();
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName(String.join(", ", itens));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled() && getWidth() > 0) {
                        selecionarPorUsuario(Math.min(Segmentos.this.itens.length - 1,
                                e.getX() * Segmentos.this.itens.length / getWidth()));
                        requestFocusInWindow();
                    }
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "anterior");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "proximo");
            getActionMap().put("anterior", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    selecionarPorUsuario(Math.max(0, selecionado - 1));
                }
            });
            getActionMap().put("proximo", new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    selecionarPorUsuario(Math.min(Segmentos.this.itens.length - 1, selecionado + 1));
                }
            });
        }

        public int selecionado() {
            return selecionado;
        }

        /** Seleciona sem avisar o ouvinte (uso programático). */
        public void selecionar(int indice) {
            this.selecionado = indice;
            repaint();
        }

        public void aoMudar(IntConsumer ouvinte) {
            this.aoMudar = ouvinte;
        }

        private void selecionarPorUsuario(int indice) {
            if (indice != selecionado && isEnabled()) {
                selecionado = indice;
                repaint();
                aoMudar.accept(indice);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Medida.LARGURA_APP, Medida.ALTURA_SEGMENTOS);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(Medida.LARGURA_COLUNA_MIN / 2, Medida.ALTURA_SEGMENTOS);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, Medida.ALTURA_SEGMENTOS);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                int margem = Medida.ESP_1;
                g2.setColor(Cor.SUPERFICIE_ELEVADA);
                g2.fill(forma(0, 0, w, h, Medida.RAIO_CONTROLE));
                float largura = (w - 2f * margem) / itens.length;
                float x = margem + selecionado * largura;
                g2.setColor(Cor.SUPERFICIE);
                g2.fill(forma(x, margem, largura, h - 2f * margem, Medida.RAIO_CONTROLE - Medida.ESP_1));
                for (int i = 0; i < itens.length; i++) {
                    boolean ativo = i == selecionado;
                    g2.setFont(ativo ? Fonte.SUBTITULO : Fonte.CORPO);
                    g2.setColor(!isEnabled() ? Cor.TEXTO_DESATIVADO : ativo ? Cor.PRIMARIA : Cor.TEXTO_SECUNDARIO);
                    FontMetrics fm = g2.getFontMetrics();
                    String texto = elipse(itens[i], fm, (int) largura - Medida.ESP_2);
                    int tx = (int) (margem + i * largura + (largura - fm.stringWidth(texto)) / 2);
                    g2.drawString(texto, tx, (h - fm.getHeight()) / 2 + fm.getAscent());
                }
                if (isFocusOwner()) {
                    contorno(g2, Cor.PRIMARIA, w, h, Medida.RAIO_CONTROLE, Medida.BORDA_FOCO);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Pílula de filtro (selecionável) ou, sobre a cor primária, uma etiqueta informativa. */
    public static final class Chip extends JToggleButton {
        private static final long serialVersionUID = 1L;
        private final Icone icone;
        private final boolean sobrePrimaria;

        Chip(String texto, Icone icone, boolean sobrePrimaria) {
            super(texto);
            this.icone = icone;
            this.sobrePrimaria = sobrePrimaria;
            setFont(sobrePrimaria ? Fonte.ROTULO : Fonte.CORPO);
            configurarBotao(this);
            if (sobrePrimaria) {
                setFocusable(false);
                setCursor(Cursor.getDefaultCursor());
                setRolloverEnabled(false);
            }
        }

        public void setTexto(String texto) {
            setText(texto);
            revalidate();
            repaint();
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            if (!sobrePrimaria) {
                super.processMouseEvent(e);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int largura = fm.stringWidth(getText()) + 2 * Medida.ESP_4 + (icone != null ? Medida.ICONE_PEQ + Medida.ESP_1 : 0);
            return new Dimension(largura, sobrePrimaria ? Medida.ALTURA_BADGE + Medida.ESP_1 : Medida.ALTURA_CHIP);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                Color corTexto;
                if (sobrePrimaria) {
                    contorno(g2, Cor.SOBRE_PRIMARIA_SUAVE, w, h, Medida.RAIO_PILULA, Medida.BORDA);
                    corTexto = Cor.SOBRE_PRIMARIA;
                } else if (isSelected()) {
                    g2.setColor(Cor.PRIMARIA_FUNDO);
                    g2.fill(forma(0, 0, w, h, Medida.RAIO_PILULA));
                    contorno(g2, Cor.PRIMARIA, w, h, Medida.RAIO_PILULA, Medida.BORDA);
                    corTexto = Cor.PRIMARIA;
                } else {
                    g2.setColor(getModel().isRollover() ? Cor.BORDA : Cor.SUPERFICIE_ELEVADA);
                    g2.fill(forma(0, 0, w, h, Medida.RAIO_PILULA));
                    corTexto = Cor.TEXTO_SECUNDARIO;
                }
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int reserva = icone != null ? Medida.ICONE_PEQ + Medida.ESP_1 : 0;
                String texto = elipse(getText(), fm, w - 2 * Medida.ESP_3 - reserva);
                int x = (w - fm.stringWidth(texto) - reserva) / 2;
                if (icone != null) {
                    Icones.de(icone, Medida.ICONE_PEQ, corTexto).paintIcon(this, g2, x, (h - Medida.ICONE_PEQ) / 2);
                    x += reserva;
                }
                g2.setColor(corTexto);
                g2.drawString(texto, x, (h - fm.getHeight()) / 2 + fm.getAscent());
                if (isFocusOwner() && !sobrePrimaria) {
                    contorno(g2, Cor.PRIMARIA, w, h, Medida.RAIO_PILULA, Medida.BORDA_FOCO);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Selo de estado: cor + texto (nunca só cor). */
    public static final class Badge extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private EstadoBadge estado;
        private String texto;

        Badge(EstadoBadge estado, String texto) {
            this.estado = estado;
            this.texto = texto;
            setFont(Fonte.ROTULO.deriveFont(Font.BOLD));
            getAccessibleContext().setAccessibleName(texto);
        }

        public void definir(EstadoBadge novoEstado, String novoTexto) {
            this.estado = novoEstado;
            this.texto = novoTexto;
            getAccessibleContext().setAccessibleName(novoTexto);
            revalidate();
            repaint();
        }

        private Color fundo() {
            return switch (estado) {
                case SUCESSO -> Cor.SUCESSO_FUNDO;
                case INFO -> Cor.INFO_FUNDO;
                case ATENCAO -> Cor.ATENCAO_FUNDO;
                case PERIGO -> Cor.PERIGO_FUNDO;
            };
        }

        private Color frente() {
            return switch (estado) {
                case SUCESSO -> Cor.SUCESSO;
                case INFO -> Cor.INFO;
                case ATENCAO -> Cor.ATENCAO;
                case PERIGO -> Cor.PERIGO;
            };
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth(texto) + 2 * Medida.ESP_3, Medida.ALTURA_BADGE);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                g2.setColor(fundo());
                g2.fill(forma(0, 0, getWidth(), getHeight(), Medida.RAIO_PILULA));
                g2.setFont(getFont());
                g2.setColor(frente());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(texto, (getWidth() - fm.stringWidth(texto)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Círculo com a inicial do titular. */
    public static final class Avatar extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private final String inicial;
        private final int tamanho;

        Avatar(String nome, int tamanho) {
            this.tamanho = tamanho;
            String limpo = nome == null ? "" : nome.strip();
            this.inicial = limpo.isEmpty() ? "?" : limpo.substring(0, limpo.offsetByCodePoints(0, 1)).toUpperCase();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(tamanho, tamanho);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                g2.setColor(Cor.PRIMARIA_FUNDO);
                g2.fill(new Ellipse2D.Float(0, 0, tamanho, tamanho));
                g2.setFont(Fonte.SUBTITULO.deriveFont(tamanho * 0.42f));
                g2.setColor(Cor.PRIMARIA);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(inicial, (tamanho - fm.stringWidth(inicial)) / 2,
                        (tamanho - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }

    /** Linha de movimentação: ícone do tipo, descrição + hora e valor com sinal à direita. */
    public static final class ItemMovimentacao extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private static final int LINHAS_DESCRICAO = 2;
        private final Icone icone;
        private final String descricao;
        private final String detalhe;
        private final String valor;
        private final boolean entrada;
        private boolean divisor;

        ItemMovimentacao(Icone icone, String descricao, String detalhe, String valor, boolean entrada) {
            this.icone = icone;
            this.descricao = descricao;
            this.detalhe = detalhe;
            this.valor = valor;
            this.entrada = entrada;
            setToolTipText(descricao);
            getAccessibleContext().setAccessibleName(descricao + ", " + detalhe + ", " + valor);
        }

        /** Linha fina na base (separa itens consecutivos). */
        public ItemMovimentacao comDivisor(boolean divisor) {
            this.divisor = divisor;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Medida.LARGURA_COLUNA_MIN, Medida.ALTURA_ITEM_LISTA);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, Medida.ALTURA_ITEM_LISTA);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                int cy = (h - Medida.CIRCULO_ITEM) / 2;
                g2.setColor(Cor.SUPERFICIE_ELEVADA);
                g2.fill(new Ellipse2D.Float(0, cy, Medida.CIRCULO_ITEM, Medida.CIRCULO_ITEM));
                Icones.de(icone, Medida.ICONE, Cor.TEXTO).paintIcon(this, g2,
                        (Medida.CIRCULO_ITEM - Medida.ICONE) / 2, cy + (Medida.CIRCULO_ITEM - Medida.ICONE) / 2);

                g2.setFont(Fonte.VALOR);
                FontMetrics fv = g2.getFontMetrics();
                int largValor = fv.stringWidth(valor);
                g2.setColor(entrada ? Cor.CREDITO : Cor.DEBITO);
                g2.drawString(valor, w - largValor, (h - fv.getHeight()) / 2 + fv.getAscent());

                int xTexto = Medida.CIRCULO_ITEM + Medida.ESP_3;
                int maxTexto = Math.max(w - xTexto - largValor - Medida.ESP_3, 1);
                g2.setFont(Fonte.SUBTITULO);
                FontMetrics fd = g2.getFontMetrics();
                g2.setFont(Fonte.ROTULO);
                FontMetrics fr = g2.getFontMetrics();
                // descrição em até 2 linhas (o nome do destinatário não pode sumir); reticências só além disso
                List<String> linhas = quebrar(descricao, fd, maxTexto);
                if (linhas.size() > LINHAS_DESCRICAO) {
                    linhas = new ArrayList<>(linhas.subList(0, LINHAS_DESCRICAO));
                    String ultima = linhas.get(LINHAS_DESCRICAO - 1);
                    linhas.set(LINHAS_DESCRICAO - 1, elipse(ultima + "\u2026", fd, maxTexto));
                }
                int bloco = linhas.size() * fd.getHeight() + fr.getHeight();
                int topo = (h - bloco) / 2;
                g2.setFont(Fonte.SUBTITULO);
                g2.setColor(Cor.TEXTO);
                int y = topo + fd.getAscent();
                for (String linha : linhas) {
                    g2.drawString(linha, xTexto, y);
                    y += fd.getHeight();
                }
                g2.setFont(Fonte.ROTULO);
                g2.setColor(Cor.TEXTO_SECUNDARIO);
                g2.drawString(elipse(detalhe, fr, maxTexto), xTexto, topo + linhas.size() * fd.getHeight() + fr.getAscent());

                if (divisor) {
                    g2.setColor(Cor.BORDA);
                    g2.fillRect(xTexto, h - Medida.BORDA, w - xTexto, Medida.BORDA);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Pílula com a conta atual; ao clicar abre uma folha com a lista de contas. */
    public static final class SeletorConta extends JButton {
        private static final long serialVersionUID = 1L;
        private final transient Supplier<List<ContaResumo>> fonte;
        private final transient BooleanSupplier saldoOculto;
        private final transient Consumer<ContaResumo> aoEscolher;
        private transient ContaResumo conta;

        SeletorConta(Supplier<List<ContaResumo>> fonte, BooleanSupplier saldoOculto,
                     Consumer<ContaResumo> aoEscolher) {
            this.fonte = fonte;
            this.saldoOculto = saldoOculto;
            this.aoEscolher = aoEscolher;
            setFont(Fonte.SUBTITULO);
            configurarBotao(this);
            addActionListener(e -> abrirFolha());
            atualizarTextoAcessivel();
        }

        public void setConta(ContaResumo nova) {
            this.conta = nova;
            setToolTipText(nova == null ? null : nova.titular());
            atualizarTextoAcessivel();
            revalidate();
            repaint();
        }

        private String texto() {
            return conta == null ? "Selecione uma conta" : conta.titular();
        }

        private void atualizarTextoAcessivel() {
            getAccessibleContext().setAccessibleName("Conta: " + texto() + ". Alterar conta");
        }

        @Override
        public Dimension getPreferredSize() {
            int altura = Medida.ESP_8 + Medida.ESP_4;
            int largura = Medida.ESP_2 + Medida.ESP_8 + Medida.ESP_2
                    + getFontMetrics(getFont()).stringWidth(texto()) + Medida.ESP_3 + Medida.ICONE_PEQ + Medida.ESP_4;
            return new Dimension(largura, altura);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(Medida.LARGURA_COLUNA_MIN / 2, getPreferredSize().height);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                g2.setColor(getModel().isRollover() || getModel().isPressed() ? Cor.SUPERFICIE_ELEVADA : Cor.SUPERFICIE);
                g2.fill(forma(0, 0, w, h, Medida.RAIO_PILULA));
                contorno(g2, isFocusOwner() ? Cor.PRIMARIA : Cor.BORDA, w, h, Medida.RAIO_PILULA,
                        isFocusOwner() ? Medida.BORDA_FOCO : Medida.BORDA);
                int av = Medida.ESP_8;
                int x = Medida.ESP_2;
                int y = (h - av) / 2;
                if (conta == null) {
                    g2.setColor(Cor.PRIMARIA_FUNDO);
                    g2.fill(new Ellipse2D.Float(x, y, av, av));
                    Icones.de(Icone.CONTA, Medida.ICONE_PEQ + Medida.ESP_1, Cor.PRIMARIA).paintIcon(this, g2,
                            x + (av - Medida.ICONE_PEQ - Medida.ESP_1) / 2, y + (av - Medida.ICONE_PEQ - Medida.ESP_1) / 2);
                } else {
                    Avatar avatar = new Avatar(conta.titular(), av);
                    avatar.setSize(av, av);
                    g2.translate(x, y);
                    avatar.paint(g2);
                    g2.translate(-x, -y);
                }
                g2.setFont(getFont());
                g2.setColor(conta == null ? Cor.TEXTO_SECUNDARIO : Cor.TEXTO);
                FontMetrics fm = g2.getFontMetrics();
                int xTexto = x + av + Medida.ESP_2;
                int max = w - xTexto - Medida.ESP_3 - Medida.ICONE_PEQ - Medida.ESP_3;
                g2.drawString(elipse(texto(), fm, max), xTexto, (h - fm.getHeight()) / 2 + fm.getAscent());
                Icones.de(Icone.CHEVRON_BAIXO, Medida.ICONE_PEQ, Cor.TEXTO_SECUNDARIO).paintIcon(this, g2,
                        w - Medida.ICONE_PEQ - Medida.ESP_3, (h - Medida.ICONE_PEQ) / 2);
            } finally {
                g2.dispose();
            }
        }

        private void abrirFolha() {
            escolherConta(this, conta, fonte, saldoOculto, aoEscolher);
        }
    }

    // =============================================================== navegação

    private static final class AbaInferior extends JToggleButton {
        private static final long serialVersionUID = 1L;
        private final Icone icone;

        AbaInferior(String rotulo, Icone icone) {
            super(rotulo);
            this.icone = icone;
            setFont(Fonte.ROTULO);
            configurarBotao(this);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                Color cor = isSelected() ? Cor.PRIMARIA : Cor.TEXTO_SECUNDARIO;
                g2.setFont(isSelected() ? Fonte.ROTULO.deriveFont(Font.BOLD) : getFont());
                FontMetrics fm = g2.getFontMetrics();
                int bloco = Medida.ICONE + Medida.ESP_1 + fm.getHeight();
                int topo = (h - bloco) / 2;
                Icones.de(icone, Medida.ICONE, cor).paintIcon(this, g2, (w - Medida.ICONE) / 2, topo);
                String texto = elipse(getText(), fm, w - Medida.ESP_1);
                g2.setColor(cor);
                g2.drawString(texto, (w - fm.stringWidth(texto)) / 2, topo + Medida.ICONE + Medida.ESP_1 + fm.getAscent());
                if (isFocusOwner()) {
                    contorno(g2, Cor.PRIMARIA, w, h, Medida.RAIO_CONTROLE, Medida.BORDA_FOCO);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Barra de navegação inferior com uma aba ativa. */
    public static final class BarraAbas extends JPanel {
        private static final long serialVersionUID = 1L;
        private final List<AbaInferior> abas = new ArrayList<>();

        BarraAbas(String[] rotulos, Icone[] icones, IntConsumer aoEscolher) {
            super(new java.awt.GridLayout(1, 0));
            setOpaque(false);
            ButtonGroup grupo = new ButtonGroup();
            for (int i = 0; i < rotulos.length; i++) {
                AbaInferior aba = new AbaInferior(rotulos[i], icones[i]);
                int indice = i;
                aba.addActionListener(e -> aoEscolher.accept(indice));
                grupo.add(aba);
                abas.add(aba);
                add(aba);
            }
        }

        /** Marca a aba ativa sem disparar o ouvinte. */
        public void selecionar(int indice) {
            abas.get(indice).setSelected(true);
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, Medida.ALTURA_ABAS);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Cor.SUPERFICIE);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Cor.BORDA);
            g.fillRect(0, 0, getWidth(), Medida.BORDA);
        }
    }

    private static final class BarraSuperior extends JPanel {
        private static final long serialVersionUID = 1L;

        BarraSuperior(String titulo, JComponent... acoes) {
            super(new BorderLayout(Medida.ESP_3, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(0, Medida.MARGEM_TELA, 0, Medida.MARGEM_TELA));
            JLabel rotulo = rotulo(titulo, Fonte.TITULO, Cor.TEXTO);
            add(rotulo, BorderLayout.CENTER);
            if (acoes.length > 0) {
                if (acoes[acoes.length - 1] instanceof Botao ultimo) {
                    setBorder(new EmptyBorder(0, Medida.MARGEM_TELA, 0, Medida.MARGEM_TELA - ultimo.recuoVisual()));
                }
                JPanel direita = new JPanel(new GridBagLayout());
                direita.setOpaque(false);
                GridBagConstraints c = new GridBagConstraints();
                c.insets = new Insets(0, Medida.ESP_2, 0, 0);
                for (JComponent acao : acoes) {
                    direita.add(acao, c);
                }
                add(direita, BorderLayout.EAST);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(super.getPreferredSize().width, Medida.ALTURA_BARRA_SUPERIOR);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, Medida.ALTURA_BARRA_SUPERIOR);
        }
    }

    // =============================================================== folha (bottom sheet)

    private static final class PainelFolha extends JPanel {
        private static final long serialVersionUID = 1L;
        private final boolean arredondar;

        PainelFolha(boolean arredondar) {
            super(new BorderLayout());
            this.arredondar = arredondar;
            setOpaque(false);
            int topo = Medida.ESP_3 + Medida.ALTURA_ALCA + Medida.ESP_3;
            setBorder(new EmptyBorder(topo, Medida.MARGEM_TELA, Medida.ESP_5, Medida.MARGEM_TELA));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                g2.setColor(Cor.SUPERFICIE);
                if (arredondar) {
                    // cantos superiores arredondados: o retângulo arredondado excede a base e é cortado
                    g2.fill(forma(0, 0, w, h + Medida.RAIO_CARTAO * 2, Medida.RAIO_CARTAO));
                } else {
                    g2.fillRect(0, 0, w, h);
                }
                g2.setColor(Cor.BORDA);
                g2.fill(forma((w - Medida.LARGURA_ALCA) / 2f, Medida.ESP_3, Medida.LARGURA_ALCA, Medida.ALTURA_ALCA,
                        Medida.RAIO_PILULA));
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Folha modal ancorada na base da janela do app, com a largura dela e cantos superiores
     * arredondados. {@link #mostrar()} bloqueia (laço modal) até {@link #fechar()} ou Esc.
     */
    public static final class Folha {
        private final JDialog dialogo;
        private final PainelFolha raiz;
        private final Window janela;

        Folha(Component dono, JComponent corpo) {
            Window ancestral = dono instanceof Window w ? w : SwingUtilities.getWindowAncestor(dono);
            this.janela = ancestral;
            dialogo = new JDialog(ancestral, Dialog.ModalityType.APPLICATION_MODAL);
            dialogo.setUndecorated(true);
            boolean translucido = false;
            GraphicsDevice tela = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (tela.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                dialogo.setBackground(Cor.TRANSPARENTE);
                translucido = true;
            }
            raiz = new PainelFolha(translucido);
            raiz.add(montarRolagem(corpo), BorderLayout.CENTER);
            dialogo.setContentPane(raiz);
            dialogo.getRootPane().registerKeyboardAction(e -> fechar(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }

        /** Exibe a folha e só retorna quando ela for fechada. */
        public void mostrar() {
            reajustar();
            dialogo.setVisible(true);
        }

        public void fechar() {
            dialogo.setVisible(false);
            dialogo.dispose();
        }

        /** Recalcula tamanho e posição (use quando o conteúdo mudar de altura). */
        public void reajustar() {
            Rectangle area = areaDaJanela();
            int maxima = (int) (area.height * Medida.FOLHA_FRACAO_MAXIMA);
            dialogo.setSize(area.width, maxima);
            dialogo.validate();
            dialogo.validate();
            int altura = Math.min(raiz.getPreferredSize().height, maxima);
            dialogo.setSize(area.width, altura);
            dialogo.setLocation(area.x, area.y + area.height - altura);
            dialogo.validate();
            dialogo.validate();
        }

        private Rectangle areaDaJanela() {
            if (janela instanceof javax.swing.RootPaneContainer container && janela.isShowing()) {
                Container conteudo = container.getRootPane();
                Point origem = conteudo.getLocationOnScreen();
                return new Rectangle(origem.x, origem.y, conteudo.getWidth(), conteudo.getHeight());
            }
            return new Rectangle(0, 0, Medida.JANELA_LARGURA, Medida.JANELA_ALTURA);
        }
    }

    // =============================================================== progresso

    /** Barra de progresso indeterminada (não indica percentual: só que algo está em execução). */
    public static final class BarraProgresso extends ComponenteAcessivel {
        private static final long serialVersionUID = 1L;
        private final Timer relogio;
        private float fase;

        BarraProgresso() {
            relogio = new Timer(Medida.ATRASO_ANIMACAO_MS, e -> {
                fase = (fase + 0.012f) % 1f;
                repaint();
            });
            getAccessibleContext().setAccessibleName("Em execu\u00e7\u00e3o");
        }

        public void iniciar() {
            relogio.start();
            repaint();
        }

        public void parar() {
            relogio.stop();
            repaint();
        }

        public boolean emExecucao() {
            return relogio.isRunning();
        }

        @Override
        public void removeNotify() {
            relogio.stop();
            super.removeNotify();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Medida.LARGURA_COLUNA_MIN, Medida.ALTURA_PROGRESSO);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Short.MAX_VALUE, Medida.ALTURA_PROGRESSO);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = suave(g);
            try {
                int w = getWidth();
                int h = getHeight();
                g2.setColor(Cor.SUPERFICIE_ELEVADA);
                g2.fill(forma(0, 0, w, h, Medida.RAIO_PILULA));
                if (relogio.isRunning()) {
                    float trecho = w * 0.35f;
                    float x = fase * (w + trecho) - trecho;
                    g2.setClip(forma(0, 0, w, h, Medida.RAIO_PILULA));
                    g2.setColor(Cor.INFO);
                    g2.fill(forma(x, 0, trecho, h, Medida.RAIO_PILULA));
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
