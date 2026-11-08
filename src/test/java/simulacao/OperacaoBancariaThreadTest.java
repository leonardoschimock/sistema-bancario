package simulacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link OperacaoBancariaThread}. O {@code run()} é chamado direto na thread do teste:
 * aqui interessa o que a classe faz com cada resultado de {@link Conta}, não a concorrência
 * (essa é testada em {@link SimulacaoConcorrenteTest}).
 */
class OperacaoBancariaThreadTest {

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static Conta novaConta(String nome, String saldoInicial) {
        return new Conta(new Cliente(nome), dinheiro(saldoInicial));
    }

    @Test
    @DisplayName("Depósito, saque e transferência são executados pelos métodos de Conta")
    void executaOperacoesPelaConta() {
        Conta a = novaConta("Ana", "100.00");
        Conta b = novaConta("Bruno", "50.00");
        OperacaoBancariaThread operador = new OperacaoBancariaThread("op", List.of(
                OperacaoPlanejada.deposito(a, dinheiro("10.00")),
                OperacaoPlanejada.saque(b, dinheiro("5.00")),
                OperacaoPlanejada.transferencia(a, b, dinheiro("30.00"))));

        operador.run();

        assertEquals(dinheiro("80.00"), a.getSaldo());   // 100 + 10 - 30
        assertEquals(dinheiro("75.00"), b.getSaldo());   // 50 - 5 + 30
        assertEquals(3, operador.getAceitas());
        assertEquals(0, operador.getRecusadas());
    }

    @Test
    @DisplayName("Saldo insuficiente é contado e registrado (não escondido), não altera o saldo e a thread segue")
    void recusaPorSaldoInsuficienteEhContadaESegue() {
        Conta a = novaConta("Ana", "20.00");
        Conta b = novaConta("Bruno", "0.00");
        OperacaoBancariaThread operador = new OperacaoBancariaThread("op", List.of(
                OperacaoPlanejada.saque(a, dinheiro("50.00")),              // recusada
                OperacaoPlanejada.transferencia(a, b, dinheiro("25.00")),   // recusada
                OperacaoPlanejada.deposito(a, dinheiro("5.00"))));          // aceita: prova que a thread seguiu

        operador.run();

        assertEquals(2, operador.getRecusadas());
        assertEquals(1, operador.getAceitas());
        assertEquals(dinheiro("25.00"), a.getSaldo());   // 20 + 5; as recusas não mexeram em nada
        assertEquals(dinheiro("0.00"), b.getSaldo());    // transferência recusada: nenhum crédito sem débito
        assertEquals(2, operador.getAmostrasDeRecusa().size());
        assertTrue(operador.getAmostrasDeRecusa().get(0).contains("saque de 50.00"),
                "A amostra deveria descrever a operação recusada.");
    }

    @Test
    @DisplayName("Exceção inesperada NÃO é engolida: sobe por run() e os contadores mostram até onde a thread chegou")
    void excecaoInesperadaPropaga() {
        Conta a = novaConta("Ana", "100.00");
        OperacaoBancariaThread operador = new OperacaoBancariaThread("op", List.of(
                OperacaoPlanejada.deposito(a, dinheiro("10.00")),
                OperacaoPlanejada.deposito(a, dinheiro("1.001")),   // Conta recusa: mais de 2 casas decimais
                OperacaoPlanejada.deposito(a, dinheiro("10.00"))));

        assertThrows(IllegalArgumentException.class, operador::run);

        assertEquals(1, operador.getAceitas());                     // só a primeira foi concluída
        assertEquals(dinheiro("110.00"), a.getSaldo());             // a terceira nunca executou
    }

    @Test
    @DisplayName("Thread interrompida para antes da próxima operação e sinaliza a interrupção")
    void interrupcaoPara() {
        Conta a = novaConta("Ana", "100.00");
        OperacaoBancariaThread operador = new OperacaoBancariaThread("op", List.of(
                OperacaoPlanejada.deposito(a, dinheiro("10.00"))));

        Thread.currentThread().interrupt();
        try {
            operador.run();
        } finally {
            Thread.interrupted(); // limpa a flag para não afetar outros testes
        }

        assertTrue(operador.foiInterrompida());
        assertEquals(0, operador.getAceitas());
        assertEquals(dinheiro("100.00"), a.getSaldo());
    }

    @Test
    @DisplayName("Plano incoerente é recusado na criação (transferência sem destino; depósito com destino)")
    void planoIncoerenteEhRecusado() {
        Conta a = novaConta("Ana", "1.00");
        Conta b = novaConta("Bruno", "1.00");
        assertThrows(IllegalArgumentException.class, () ->
                new OperacaoPlanejada(domain.TipoTransacao.TRANSFERENCIA, a, null, dinheiro("1.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new OperacaoPlanejada(domain.TipoTransacao.DEPOSITO, a, b, dinheiro("1.00")));
    }
}
