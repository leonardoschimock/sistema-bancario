package app.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formatação e leitura de dinheiro no padrão brasileiro ({@code R$ 1.234,56}).
 *
 * <p>Só apresentação: formatar nunca altera o valor que o domínio recebe, e {@link #parse} devolve
 * o valor exato digitado (no máximo 2 casas, sem arredondar em silêncio).
 */
public final class Moeda {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final String PREFIXO = "R$ ";
    private static final char MENOS = '\u2212';
    private static final Pattern FORMATO = Pattern.compile("-?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d{1,2})?");

    private Moeda() {
    }

    /** {@code R$ 1.234,56}; negativos como {@code -R$ 1.234,56}. Arredonda só a exibição (HALF_UP). */
    public static String formatar(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("O valor é obrigatório.");
        }
        BigDecimal exibido = valor.setScale(2, RoundingMode.HALF_UP);
        String sinal = exibido.signum() < 0 ? "-" : "";
        return sinal + PREFIXO + formatarNumero(exibido.abs());
    }

    /** Só o número, sem símbolo: {@code 1.234,56}. */
    public static String formatarNumero(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
        formato.setRoundingMode(RoundingMode.HALF_UP);
        return formato.format(valor);
    }

    /** Valor com sinal e descrição para listas: {@code + R$ 12,00} (entrada) ou {@code − R$ 12,00} (saída). */
    public static String formatarComSinal(BigDecimal valor, boolean entrada) {
        return (entrada ? "+ " : MENOS + " ") + formatar(valor.abs());
    }

    /**
     * Lê um valor em pt-BR: {@code 1.234,56}, {@code R$ 10}, {@code 10,5}, {@code -3,00}.
     * O resultado sempre tem escala 2. Pontos só são aceitos como separador de milhar.
     *
     * @throws NumberFormatException texto vazio, em outro formato ou com mais de 2 casas decimais
     */
    public static BigDecimal parse(String texto) {
        if (texto == null) {
            throw new NumberFormatException("Valor ausente.");
        }
        String limpo = texto.replace("R$", "").replace('\u00A0', ' ').replace(MENOS, '-')
                .replaceAll("\\s+", "");
        if (limpo.isEmpty()) {
            throw new NumberFormatException("Valor vazio.");
        }
        if (!FORMATO.matcher(limpo).matches()) {
            throw new NumberFormatException("Valor em formato inválido: " + texto);
        }
        return new BigDecimal(limpo.replace(".", "").replace(',', '.')).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Como {@link #parse}, mas devolve vazio em vez de lançar exceção. */
    public static Optional<BigDecimal> tentarParse(String texto) {
        try {
            return Optional.of(parse(texto));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 12345 centavos viram 123,45. */
    public static BigDecimal deCentavos(long centavos) {
        return BigDecimal.valueOf(centavos, 2);
    }

    /** 123,45 vira 12345 centavos; o valor precisa ter no máximo 2 casas. */
    public static long paraCentavos(BigDecimal valor) {
        return valor.movePointRight(2).longValueExact();
    }
}
