package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes unitários (sem banco) do identificador de persistência e da restauração de Conta. */
class ContaIdPersistenciaTest {

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("Conta nova ainda não tem identificador de persistência")
    void contaNovaNaoTemId() {
        Conta conta = new Conta(new Cliente("Ana"), dinheiro("10.00"));

        assertNull(conta.getIdPersistencia());
    }

    @Test
    @DisplayName("definirIdPersistencia registra o id e não altera o saldo")
    void definirIdRegistraId() {
        Conta conta = new Conta(new Cliente("Ana"), dinheiro("10.00"));

        conta.definirIdPersistencia(7L);

        assertEquals(7L, conta.getIdPersistencia());
        assertEquals(dinheiro("10.00"), conta.getSaldo());
    }

    @Test
    @DisplayName("O identificador de persistência só pode ser definido uma vez")
    void idSoPodeSerDefinidoUmaVez() {
        Conta conta = new Conta(new Cliente("Ana"), dinheiro("10.00"));
        conta.definirIdPersistencia(7L);

        assertThrows(IllegalStateException.class, () -> conta.definirIdPersistencia(8L));
        assertEquals(7L, conta.getIdPersistencia());
    }

    @Test
    @DisplayName("restaurar reconstrói a conta com id, titular e saldo informados")
    void restaurarReconstroiConta() {
        Conta conta = Conta.restaurar(42L, new Cliente("Bruno"), dinheiro("250.75"));

        assertEquals(42L, conta.getIdPersistencia());
        assertEquals("Bruno", conta.getTitular().getNome());
        assertEquals(dinheiro("250.75"), conta.getSaldo());
    }

    @Test
    @DisplayName("Conta restaurada continua obedecendo as regras de negócio")
    void contaRestauradaFuncionaNormalmente() {
        Conta conta = Conta.restaurar(1L, new Cliente("Bruno"), dinheiro("100.00"));

        conta.depositar(dinheiro("20.00"));
        conta.sacar(dinheiro("50.00"));

        assertEquals(dinheiro("70.00"), conta.getSaldo());
        assertThrows(SaldoInsuficienteException.class, () -> conta.sacar(dinheiro("500.00")));
    }

    @Test
    @DisplayName("restaurar recusa saldo negativo e titular ausente")
    void restaurarValidaDados() {
        assertThrows(IllegalArgumentException.class,
                () -> Conta.restaurar(1L, new Cliente("Bruno"), dinheiro("-1.00")));
        assertThrows(IllegalArgumentException.class,
                () -> Conta.restaurar(1L, null, dinheiro("1.00")));
    }
}
