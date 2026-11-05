package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransacaoTest {

    @Test
    @DisplayName("Transação registra tipo, valor e horário informados")
    void registraTipoValorEHorario() {
        LocalDateTime horario = LocalDateTime.of(2026, 11, 3, 14, 30, 0);
        BigDecimal valor = new BigDecimal("75.50");

        Transacao t = new Transacao(TipoTransacao.SAQUE, valor, horario);

        assertEquals(TipoTransacao.SAQUE, t.getTipo());
        assertEquals(valor, t.getValor());
        assertEquals(horario, t.getHorario());
    }

    @Test
    @DisplayName("Construtor sem horário usa o momento da criação")
    void construtorSemHorarioUsaMomentoAtual() {
        LocalDateTime antes = LocalDateTime.now();

        Transacao t = new Transacao(TipoTransacao.DEPOSITO, new BigDecimal("10.00"));

        LocalDateTime depois = LocalDateTime.now();
        assertFalse(t.getHorario().isBefore(antes));
        assertFalse(t.getHorario().isAfter(depois));
    }

    @Test
    @DisplayName("Tipo nulo é recusado")
    void recusaTipoNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(null, new BigDecimal("10.00")));
    }

    @Test
    @DisplayName("Valor nulo, zero ou negativo é recusado")
    void recusaValorInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(TipoTransacao.DEPOSITO, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(TipoTransacao.DEPOSITO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(TipoTransacao.DEPOSITO, new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Horário nulo é recusado")
    void recusaHorarioNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(TipoTransacao.DEPOSITO, new BigDecimal("10.00"), null));
    }

    @Test
    @DisplayName("Transação é imutável: classe final e atributos privados e finais")
    void ehImutavel() {
        assertTrue(Modifier.isFinal(Transacao.class.getModifiers()), "a classe deve ser final");

        for (Field campo : Transacao.class.getDeclaredFields()) {
            int mods = campo.getModifiers();
            assertTrue(Modifier.isPrivate(mods), "atributo deve ser privado: " + campo.getName());
            assertTrue(Modifier.isFinal(mods), "atributo deve ser final: " + campo.getName());
        }
    }
}
