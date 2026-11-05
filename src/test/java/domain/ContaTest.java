package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testes SEQUENCIAIS de Conta (uma thread só). Concorrência fica para etapas posteriores. */
class ContaTest {

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static Conta novaConta(String nome, String saldoInicial) {
        return new Conta(new Cliente(nome), dinheiro(saldoInicial));
    }

    // ---------- 1. Depósito ----------

    @Test
    @DisplayName("Depósito aumenta o saldo corretamente")
    void depositoAumentaSaldo() {
        Conta conta = novaConta("Ana", "100.00");

        conta.depositar(dinheiro("50.25"));

        assertEquals(dinheiro("150.25"), conta.getSaldo());
    }

    @Test
    @DisplayName("Depósitos sucessivos se acumulam")
    void depositosSucessivosSeAcumulam() {
        Conta conta = novaConta("Ana", "0.00");

        conta.depositar(dinheiro("10.00"));
        conta.depositar(dinheiro("20.50"));

        assertEquals(dinheiro("30.50"), conta.getSaldo());
    }

    // ---------- 2. Saque válido ----------

    @Test
    @DisplayName("Saque válido reduz o saldo corretamente")
    void saqueValidoReduzSaldo() {
        Conta conta = novaConta("Bruno", "100.00");

        conta.sacar(dinheiro("30.00"));

        assertEquals(dinheiro("70.00"), conta.getSaldo());
    }

    @Test
    @DisplayName("Saque do valor exato do saldo é permitido e zera a conta")
    void saqueDoSaldoExatoZeraConta() {
        Conta conta = novaConta("Bruno", "100.00");

        conta.sacar(dinheiro("100.00"));

        assertEquals(dinheiro("0.00"), conta.getSaldo());
    }

    // ---------- 3. Saque com saldo insuficiente ----------

    @Test
    @DisplayName("Saque com saldo insuficiente é recusado e preserva o saldo")
    void saqueComSaldoInsuficienteEhRecusadoEPreservaSaldo() {
        Conta conta = novaConta("Carla", "100.00");

        assertThrows(SaldoInsuficienteException.class, () -> conta.sacar(dinheiro("100.01")));

        assertEquals(dinheiro("100.00"), conta.getSaldo());
    }

    // ---------- 4. Transferência válida ----------

    @Test
    @DisplayName("Transferência válida debita a origem e credita o destino")
    void transferenciaValidaDebitaOrigemECreditaDestino() {
        Conta origem = novaConta("Diego", "200.00");
        Conta destino = novaConta("Elisa", "50.00");

        origem.transferirPara(destino, dinheiro("80.00"));

        assertEquals(dinheiro("120.00"), origem.getSaldo());
        assertEquals(dinheiro("130.00"), destino.getSaldo());
    }

    // ---------- 5. Transferência inválida não deixa estado inconsistente ----------

    @Test
    @DisplayName("Transferência com saldo insuficiente não altera nenhuma das contas")
    void transferenciaComSaldoInsuficienteNaoAlteraNenhumaConta() {
        Conta origem = novaConta("Diego", "50.00");
        Conta destino = novaConta("Elisa", "10.00");

        assertThrows(SaldoInsuficienteException.class,
                () -> origem.transferirPara(destino, dinheiro("50.01")));

        assertEquals(dinheiro("50.00"), origem.getSaldo());
        assertEquals(dinheiro("10.00"), destino.getSaldo());
    }

    @Test
    @DisplayName("Transferência com valor inválido não altera nenhuma das contas")
    void transferenciaComValorInvalidoNaoAlteraNenhumaConta() {
        Conta origem = novaConta("Diego", "100.00");
        Conta destino = novaConta("Elisa", "10.00");

        assertThrows(IllegalArgumentException.class, () -> origem.transferirPara(destino, dinheiro("0.00")));
        assertThrows(IllegalArgumentException.class, () -> origem.transferirPara(destino, dinheiro("-5.00")));
        assertThrows(IllegalArgumentException.class, () -> origem.transferirPara(destino, null));

        assertEquals(dinheiro("100.00"), origem.getSaldo());
        assertEquals(dinheiro("10.00"), destino.getSaldo());
    }

    @Test
    @DisplayName("Transferência para destino nulo é recusada e preserva a origem")
    void transferenciaParaDestinoNuloPreservaOrigem() {
        Conta origem = novaConta("Diego", "100.00");

        assertThrows(IllegalArgumentException.class, () -> origem.transferirPara(null, dinheiro("10.00")));

        assertEquals(dinheiro("100.00"), origem.getSaldo());
    }

    @Test
    @DisplayName("Transferência para a própria conta é recusada e preserva o saldo")
    void transferenciaParaMesmaContaEhRecusada() {
        Conta conta = novaConta("Diego", "100.00");

        assertThrows(IllegalArgumentException.class, () -> conta.transferirPara(conta, dinheiro("10.00")));

        assertEquals(dinheiro("100.00"), conta.getSaldo());
    }
}
