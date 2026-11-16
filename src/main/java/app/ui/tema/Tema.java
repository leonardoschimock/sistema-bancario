package app.ui.tema;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.Map;
import java.util.Set;

/**
 * Única fonte de valores visuais do app: cores, fontes e medidas (SKILL §2).
 * Trocar {@link Cor#PRIMARIA} (e derivadas) troca a identidade inteira sem mexer nas telas.
 */
public final class Tema {

    private Tema() {
    }

    /** Paleta (SKILL §2.1). */
    public static final class Cor {
        public static final Color FUNDO = new Color(0xF5F6FA);
        public static final Color SUPERFICIE = new Color(0xFFFFFF);
        public static final Color SUPERFICIE_ELEVADA = new Color(0xF0F2F7);
        public static final Color BORDA = new Color(0xE3E6EE);
        public static final Color TEXTO = new Color(0x202536);
        public static final Color TEXTO_SECUNDARIO = new Color(0x697386);
        public static final Color TEXTO_DESATIVADO = new Color(0xA5ACB9);

        public static final Color PRIMARIA = new Color(0x6C3CE9);
        public static final Color PRIMARIA_HOVER = new Color(0x572BC7);
        public static final Color SOBRE_PRIMARIA = new Color(0xFFFFFF);
        public static final Color SOBRE_PRIMARIA_SUAVE = new Color(0xDCD0FA);
        public static final Color PRIMARIA_FUNDO = new Color(0xEFE9FD);

        public static final Color SUCESSO = new Color(0x16865B);
        public static final Color SUCESSO_FUNDO = new Color(0xE8F6EF);
        public static final Color INFO = new Color(0x2878D0);
        public static final Color INFO_FUNDO = new Color(0xEAF2FC);
        public static final Color ATENCAO = new Color(0xB7791F);
        public static final Color ATENCAO_FUNDO = new Color(0xFFF5DF);
        public static final Color PERIGO = new Color(0xD64545);
        public static final Color PERIGO_FUNDO = new Color(0xFCECEC);

        public static final Color CREDITO = new Color(0x16865B);
        public static final Color DEBITO = new Color(0xD64545);

        /** Para janelas com cantos arredondados (folhas): área fora do desenho fica transparente. */
        public static final Color TRANSPARENTE = new Color(0, 0, 0, 0);

        private Cor() {
        }
    }

    /** Tipografia (SKILL §2.2). */
    public static final class Fonte {
        private static final Set<String> INSTALADAS = Set.of(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());

        /** Primeira família disponível: Inter, Segoe UI, SF Pro Text, SansSerif. */
        public static final String FAMILIA = escolher("Inter", "Segoe UI", "SF Pro Text", "SansSerif");
        /** Primeira família mono disponível. */
        public static final String FAMILIA_MONO = escolher("JetBrains Mono", "Cascadia Mono", "Consolas",
                "DejaVu Sans Mono", "Monospaced");

        public static final Font TITULO = ui(Font.BOLD, 20);
        public static final Font SECAO = ui(Font.BOLD, 16);
        public static final Font SUBTITULO = semibold(14);
        public static final Font CORPO = ui(Font.PLAIN, 14);
        public static final Font ROTULO = ui(Font.PLAIN, 12);
        public static final Font SALDO = ui(Font.BOLD, 34);
        public static final Font VALOR_GRANDE = ui(Font.BOLD, 40);
        public static final Font VALOR = semibold(16);
        public static final Font DADO = new Font(FAMILIA_MONO, Font.PLAIN, 12);

        /** Menor tamanho aceito quando o valor grande precisa encolher para caber. */
        public static final float VALOR_GRANDE_MINIMO = 20f;
        /** Quanto a fonte do valor encolhe a cada tentativa até caber. */
        public static final float PASSO_ENCOLHER = 2f;

        private Fonte() {
        }

        private static String escolher(String... candidatas) {
            for (String candidata : candidatas) {
                if (INSTALADAS.contains(candidata)) {
                    return candidata;
                }
            }
            return candidatas[candidatas.length - 1];
        }

        private static Font ui(int estilo, int tamanho) {
            return new Font(FAMILIA, estilo, tamanho);
        }

        private static Font semibold(int tamanho) {
            return ui(Font.PLAIN, tamanho).deriveFont(Map.of(TextAttribute.WEIGHT, TextAttribute.WEIGHT_SEMIBOLD));
        }
    }

    /** Medidas em grade de 4 px (SKILL §2.3) e demais constantes visuais. */
    public static final class Medida {
        public static final int ESP_1 = 4;
        public static final int ESP_2 = 8;
        public static final int ESP_3 = 12;
        public static final int ESP_4 = 16;
        public static final int ESP_5 = 20;
        public static final int ESP_6 = 24;
        public static final int ESP_7 = 28;
        public static final int ESP_8 = 32;

        public static final int MARGEM_TELA = 20;
        public static final int RAIO_CARTAO = 20;
        public static final int RAIO_CONTROLE = 14;
        public static final int RAIO_PILULA = 999;

        public static final int ALTURA_BOTAO = 52;
        public static final int ALTURA_CAMPO = 52;
        public static final int ALTURA_BOTAO_COMPACTO = 40;
        public static final int BOTAO_ICONE = 40;
        public static final int ALTURA_ITEM_LISTA = 64;
        public static final int ALTURA_BARRA_SUPERIOR = 56;
        public static final int ALTURA_ABAS = 64;
        public static final int ACAO_CIRCULO = 56;
        public static final int LARGURA_APP = 420;

        public static final int ICONE_PEQ = 16;
        public static final int ICONE = 24;
        public static final int ICONE_GRANDE = 32;

        // ---- derivadas dos componentes (todas na grade de 4 px, salvo traços) ----
        public static final int BORDA = 1;
        public static final int BORDA_FOCO = 2;
        public static final float TRACO_ICONE = 1.75f;
        public static final int AVATAR = 40;
        public static final int CIRCULO_ITEM = 44;
        public static final int ALTURA_BADGE = 24;
        public static final int ALTURA_CHIP = 36;
        public static final int ALTURA_SEGMENTOS = 48;
        public static final int ALTURA_CAMPO_VALOR = 88;
        public static final int LARGURA_ROLAGEM = 6;
        public static final int LARGURA_AREA_ROLAGEM = 10;
        public static final int PASSO_ROLAGEM = 24;
        public static final int ALTURA_PROGRESSO = 6;
        public static final int CAIXA = 22;
        public static final int LARGURA_ALCA = 40;
        public static final int ALTURA_ALCA = 4;
        public static final int LARGURA_COLUNA_MIN = 300;
        public static final int LARGURA_ACAO = 76;
        public static final int HERO_ESPACO_BAIXO = 28;
        public static final int DIGITOS_VALOR = 11;
        public static final int LIMITE_ITENS_INICIO = 5;
        public static final int LIMITE_ITENS_EXTRATO = 200;
        public static final int DIGITOS_THREADS = 3;
        public static final int DIGITOS_OPERACOES = 6;
        public static final int DIGITOS_CONTAS = 4;
        public static final int DIGITOS_SEED = 18;
        public static final int DIGITOS_PRAZO = 4;
        public static final int ATRASO_ANIMACAO_MS = 16;

        // ---- janela (SKILL §3) ----
        public static final int JANELA_LARGURA = 430;
        public static final int JANELA_ALTURA = 860;
        public static final int JANELA_LARGURA_MIN = 360;
        public static final int JANELA_ALTURA_MIN = 640;
        public static final float FOLHA_FRACAO_MAXIMA = 0.85f;

        private Medida() {
        }
    }
}
