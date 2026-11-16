package app.ui.tema;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * Ícones vetoriais em Java2D (SKILL §5): grade de 24 px, traço de 1,75 px, pontas arredondadas,
 * monocromáticos. A cor vem do contexto. Só existem os ícones que aparecem em alguma tela.
 */
public final class Icones {

    /** Ícones disponíveis. */
    public enum Icone {
        BANCO, INICIO, CONTA, ADICIONAR, DEPOSITO, SAQUE, TRANSFERENCIA, HISTORICO, EXPORTAR, ARQUIVO,
        ATUALIZAR, OLHO, OLHO_FECHADO, CHEVRON_DIREITA, CHEVRON_BAIXO, OK, INFO, ALERTA, ERRO,
        CONCORRENCIA, BANCO_DADOS
    }

    private static final double GRADE = 24.0;
    private static final double RAIO_PONTO = 1.1;

    private Icones() {
    }

    /** Ícone quadrado de {@code tamanho} px na cor informada. */
    public static Icon de(Icone icone, int tamanho, Color cor) {
        return new IconeVetorial(icone, tamanho, cor);
    }

    private static final class IconeVetorial implements Icon {
        private final Icone icone;
        private final int tamanho;
        private final Color cor;

        IconeVetorial(Icone icone, int tamanho, Color cor) {
            this.icone = icone;
            this.tamanho = tamanho;
            this.cor = cor;
        }

        @Override
        public int getIconWidth() {
            return tamanho;
        }

        @Override
        public int getIconHeight() {
            return tamanho;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.translate(x, y);
                double escala = tamanho / GRADE;
                g2.scale(escala, escala);
                g2.setColor(cor);
                g2.setStroke(new BasicStroke(Tema.Medida.TRACO_ICONE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                desenhar(g2, icone);
            } finally {
                g2.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- desenhos (grade 24 x 24)

    private static void desenhar(Graphics2D g, Icone icone) {
        switch (icone) {
            case BANCO -> {
                poli(g, false, 3, 9.5, 12, 4, 21, 9.5);
                linha(g, 4, 20, 20, 20);
                linha(g, 6.5, 12, 6.5, 17);
                linha(g, 10.2, 12, 10.2, 17);
                linha(g, 13.8, 12, 13.8, 17);
                linha(g, 17.5, 12, 17.5, 17);
            }
            case INICIO -> {
                poli(g, false, 3.5, 11, 12, 4, 20.5, 11);
                poli(g, false, 5.5, 9.5, 5.5, 20, 18.5, 20, 18.5, 9.5);
                poli(g, false, 10, 20, 10, 14.5, 14, 14.5, 14, 20);
            }
            case CONTA -> {
                g.draw(new Ellipse2D.Double(12 - 3.8, 8 - 3.8, 7.6, 7.6));
                Path2D ombros = new Path2D.Double();
                ombros.moveTo(4.5, 20);
                ombros.curveTo(4.5, 15.5, 8, 14, 12, 14);
                ombros.curveTo(16, 14, 19.5, 15.5, 19.5, 20);
                g.draw(ombros);
            }
            case ADICIONAR -> {
                linha(g, 12, 5, 12, 19);
                linha(g, 5, 12, 19, 12);
            }
            case DEPOSITO -> {
                linha(g, 12, 3.5, 12, 14.5);
                poli(g, false, 7.5, 10, 12, 14.5, 16.5, 10);
                poli(g, false, 4.5, 15, 4.5, 20, 19.5, 20, 19.5, 15);
            }
            case SAQUE -> {
                linha(g, 12, 15, 12, 4);
                poli(g, false, 7.5, 8.5, 12, 4, 16.5, 8.5);
                poli(g, false, 4.5, 15, 4.5, 20, 19.5, 20, 19.5, 15);
            }
            case TRANSFERENCIA -> {
                linha(g, 4, 8, 20, 8);
                poli(g, false, 16, 4, 20, 8, 16, 12);
                linha(g, 20, 16, 4, 16);
                poli(g, false, 8, 12, 4, 16, 8, 20);
            }
            case HISTORICO -> {
                g.draw(new Ellipse2D.Double(4, 4, 16, 16));
                poli(g, false, 12, 7.5, 12, 12, 15.5, 14);
            }
            case EXPORTAR -> {
                poli(g, false, 10, 5, 5, 5, 5, 19, 19, 19, 19, 14);
                linha(g, 11, 13, 20, 4);
                poli(g, false, 14.5, 4, 20, 4, 20, 9.5);
            }
            case ARQUIVO -> {
                poli(g, true, 6, 3, 14, 3, 19, 8, 19, 21, 6, 21);
                poli(g, false, 14, 3, 14, 8, 19, 8);
                linha(g, 9, 13, 16, 13);
                linha(g, 9, 17, 16, 17);
            }
            case ATUALIZAR -> {
                g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 30, 300, Arc2D.OPEN));
                poli(g, false, 19, 3.5, 19, 8.5, 14, 8.5);
            }
            case OLHO -> olho(g);
            case OLHO_FECHADO -> {
                olho(g);
                linha(g, 4, 4, 20, 20);
            }
            case CHEVRON_DIREITA -> poli(g, false, 9, 5, 16, 12, 9, 19);
            case CHEVRON_BAIXO -> poli(g, false, 5, 9, 12, 16, 19, 9);
            case OK -> poli(g, false, 5, 12.5, 10, 17.5, 19, 7.5);
            case INFO -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                linha(g, 12, 11, 12, 16.5);
                ponto(g, 12, 7.8);
            }
            case ALERTA -> {
                poli(g, true, 12, 3.5, 21.5, 20, 2.5, 20);
                linha(g, 12, 9.5, 12, 14);
                ponto(g, 12, 17);
            }
            case ERRO -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                linha(g, 8.8, 8.8, 15.2, 15.2);
                linha(g, 15.2, 8.8, 8.8, 15.2);
            }
            case CONCORRENCIA -> {
                Path2D cima = new Path2D.Double();
                cima.moveTo(5, 12);
                cima.curveTo(11, 12, 11, 5, 19, 5);
                g.draw(cima);
                Path2D baixo = new Path2D.Double();
                baixo.moveTo(5, 12);
                baixo.curveTo(11, 12, 11, 19, 19, 19);
                g.draw(baixo);
                linha(g, 5, 12, 19, 12);
                ponto(g, 4, 12);
                ponto(g, 20, 5);
                ponto(g, 20, 12);
                ponto(g, 20, 19);
            }
            case BANCO_DADOS -> {
                g.draw(new Ellipse2D.Double(5, 3.5, 14, 6));
                Path2D lados = new Path2D.Double();
                lados.moveTo(5, 6.5);
                lados.lineTo(5, 17.5);
                lados.curveTo(5, 19.5, 8.5, 20.5, 12, 20.5);
                lados.curveTo(15.5, 20.5, 19, 19.5, 19, 17.5);
                lados.lineTo(19, 6.5);
                g.draw(lados);
                Path2D meio = new Path2D.Double();
                meio.moveTo(5, 12);
                meio.curveTo(5, 14, 8.5, 15, 12, 15);
                meio.curveTo(15.5, 15, 19, 14, 19, 12);
                g.draw(meio);
            }
        }
    }

    private static void olho(Graphics2D g) {
        Path2D contorno = new Path2D.Double();
        contorno.moveTo(2, 12);
        contorno.quadTo(12, 3, 22, 12);
        contorno.quadTo(12, 21, 2, 12);
        g.draw(contorno);
        g.draw(new Ellipse2D.Double(9, 9, 6, 6));
    }

    private static void linha(Graphics2D g, double x1, double y1, double x2, double y2) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        g.draw(p);
    }

    /** Sequência de pontos x,y; fechada ou aberta. */
    private static void poli(Graphics2D g, boolean fechada, double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        if (fechada) {
            p.closePath();
        }
        g.draw(p);
    }

    private static void ponto(Graphics2D g, double cx, double cy) {
        g.fill(new Ellipse2D.Double(cx - RAIO_PONTO, cy - RAIO_PONTO, RAIO_PONTO * 2, RAIO_PONTO * 2));
    }
}
