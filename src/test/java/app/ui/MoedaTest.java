package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Leitura e formatação pt-BR de dinheiro (apresentação; sem banco e sem Swing). */
class MoedaTest {

    @Test
    void formataComMilharVirgulaEDuasCasas() {
        assertEquals("R$ 1.234,56", Moeda.formatar(new BigDecimal("1234.56")));
        assertEquals("R$ 1.234.567,80", Moeda.formatar(new BigDecimal("1234567.8")));
        assertEquals("R$ 0,00", Moeda.formatar(BigDecimal.ZERO));
        assertEquals("R$ 10,00", Moeda.formatar(BigDecimal.TEN));
    }

    @Test
    void formataNegativoComSinalAntesDoSimbolo() {
        assertEquals("-R$ 5,50", Moeda.formatar(new BigDecimal("-5.5")));
    }

    @Test
    void formatarComSinalMostraMaisOuMenosEDescricao() {
        assertEquals("+ R$ 12,00", Moeda.formatarComSinal(new BigDecimal("12"), true));
        assertEquals("\u2212 R$ 1.000,00", Moeda.formatarComSinal(new BigDecimal("1000"), false));
    }

    @Test
    void leValoresNosFormatosBrasileiros() {
        assertEquals(new BigDecimal("1234.56"), Moeda.parse("1.234,56"));
        assertEquals(new BigDecimal("1234.56"), Moeda.parse("R$ 1.234,56"));
        assertEquals(new BigDecimal("10.50"), Moeda.parse("10,5"));
        assertEquals(new BigDecimal("1234.00"), Moeda.parse("1.234"));
        assertEquals(new BigDecimal("-3.00"), Moeda.parse("-3,00"));
        assertEquals(new BigDecimal("7.00"), Moeda.parse("  7  "));
    }

    @Test
    void parseSempreDevolveEscalaDois() {
        assertEquals(2, Moeda.parse("10").scale());
        assertEquals(2, Moeda.parse("10,1").scale());
    }

    @Test
    void recusaTextoInvalidoEMaisDeDuasCasas() {
        for (String ruim : new String[] {"", "   ", "abc", "1,234", "1.2.3", "12,3,4", "1234.56", ",50", "1.", "R$"}) {
            assertThrows(NumberFormatException.class, () -> Moeda.parse(ruim), "deveria recusar: '" + ruim + "'");
        }
        assertThrows(NumberFormatException.class, () -> Moeda.parse(null));
    }

    @Test
    void tentarParseNaoLancaExcecao() {
        assertTrue(Moeda.tentarParse("abc").isEmpty());
        assertEquals(new BigDecimal("2.00"), Moeda.tentarParse("2").orElseThrow());
    }

    @Test
    void formatarEInterpretarSaoInversos() {
        BigDecimal original = new BigDecimal("98765.43");
        assertEquals(original, Moeda.parse(Moeda.formatar(original)));
    }

    @Test
    void conversaoDeCentavosEExata() {
        assertEquals(new BigDecimal("123.45"), Moeda.deCentavos(12345));
        assertEquals(12345L, Moeda.paraCentavos(new BigDecimal("123.45")));
        assertThrows(ArithmeticException.class, () -> Moeda.paraCentavos(new BigDecimal("1.234")));
    }
}
