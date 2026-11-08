package simulacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link SimulacaoConcorrente}: várias threads sobre as MESMAS contas, saldo final
 * comparado com o esperado calculado antes de as threads começarem, e relato correto de recusas
 * e falhas.
 *
 * Limite honesto: passar aqui é evidência, não prova. A garantia de correção vem do raciocínio
 * sobre os locks de {@link Conta}. Em máquinas com poucos núcleos a corrida pode demorar a se
 * manifestar; por isso a carga é grande e repetida em várias rodadas.
 */
class SimulacaoConcorrenteTest {

    private static final Duration PRAZO = Duration.ofSeconds(60);

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    private static Conta novaConta(String nome, String saldoInicial) {
        return new Conta(new Cliente(nome), dinheiro(saldoInicial));
    }

    @Test
    @DisplayName("8 threads sobre 4 contas compartilhadas: saldos finais iguais aos esperados em todas as rodadas")
    void saldosFinaisConferemComEsperadoEmVariasRodadas() throws Exception {
        for (int rodada = 1; rodada <= 5; rodada++) {
            CenarioSimulacao cenario = CenarioSimulacao.gerar(42, 8, 20_000, 4); // mesma seed, cenário novo

            ResultadoSimulacao resultado = SimulacaoConcorrente.executar(cenario, PRAZO);

            assertTrue(resultado.terminou(), "Não terminou na rodada " + rodada + ": " + resultado.diagnostico());
            assertEquals(List.of(), resultado.problemas(), "Problemas na rodada " + rodada);
            assertEquals(8 * 20_000, resultado.totalAceitas(), "Operações aceitas na rodada " + rodada);
            assertEquals(0, resultado.totalRecusadas());
            for (ResultadoSimulacao.ComparacaoSaldo saldo : resultado.saldos()) {
                assertEquals(saldo.esperado(), saldo.obtido(),
                        saldo.titular() + " divergiu na rodada " + rodada);
            }
        }
    }

    @Test
    @DisplayName("Um cenário não pode ser executado duas vezes (as contas já foram alteradas)")
    void cenarioEhDeUsoUnico() throws Exception {
        CenarioSimulacao cenario = CenarioSimulacao.gerar(1, 2, 100, 2);
        SimulacaoConcorrente.executar(cenario, PRAZO);

        assertThrows(IllegalStateException.class, () -> SimulacaoConcorrente.executar(cenario, PRAZO));
    }

    @Test
    @DisplayName("Operação recusada por saldo insuficiente aparece no resultado e torna a simulação inconsistente")
    void recusaTornaSimulacaoInconsistente() throws Exception {
        Conta a = novaConta("Ana", "10.00");
        Conta b = novaConta("Bruno", "10.00");
        CenarioSimulacao cenario = CenarioSimulacao.de(List.of(a, b), List.of(
                List.of(OperacaoPlanejada.saque(a, dinheiro("500.00"))),     // impossível: saldo é 10,00
                List.of(OperacaoPlanejada.deposito(b, dinheiro("1.00")))));

        ResultadoSimulacao resultado = SimulacaoConcorrente.executar(cenario, PRAZO);

        assertTrue(resultado.terminou());
        assertEquals(1, resultado.totalRecusadas());
        assertFalse(resultado.consistente());
        assertTrue(resultado.problemas().stream().anyMatch(p -> p.contains("recusada")),
                "O problema deveria citar a recusa: " + resultado.problemas());
        assertEquals(1, resultado.operadores().get(0).amostrasDeRecusa().size());
    }

    @Test
    @DisplayName("Falha inesperada numa thread é reportada com a causa original; as outras threads terminam")
    void falhaInesperadaEhReportada() throws Exception {
        Conta a = novaConta("Ana", "10.00");
        CenarioSimulacao cenario = CenarioSimulacao.de(List.of(a), List.of(
                List.of(OperacaoPlanejada.deposito(a, dinheiro("1.001"))),   // Conta lança IllegalArgumentException
                List.of(OperacaoPlanejada.deposito(a, dinheiro("2.00")))));

        ResultadoSimulacao resultado = SimulacaoConcorrente.executar(cenario, PRAZO);

        assertTrue(resultado.terminou(), "A thread saudável deveria ter terminado.");
        assertEquals(1, resultado.falhas().size());
        assertEquals("operador-1", resultado.falhas().get(0).operador());
        assertTrue(resultado.falhas().get(0).causa() instanceof IllegalArgumentException,
                "A causa original deveria ser preservada: " + resultado.falhas().get(0).causa());
        assertFalse(resultado.consistente());
        assertEquals(1, resultado.operadores().get(1).aceitas());            // a outra thread concluiu
    }

    @Test
    @DisplayName("ResultadoSimulacao aponta saldo divergente (o detector de divergência funciona)")
    void resultadoApontaDivergencia() {
        ResultadoSimulacao.ComparacaoSaldo certo = new ResultadoSimulacao.ComparacaoSaldo(
                "Ana", dinheiro("0.00"), dinheiro("100.00"), dinheiro("100.00"));
        ResultadoSimulacao.ComparacaoSaldo errado = new ResultadoSimulacao.ComparacaoSaldo(
                "Bruno", dinheiro("0.00"), dinheiro("100.00"), dinheiro("90.00"));
        ResultadoSimulacao.ResumoOperador operador = new ResultadoSimulacao.ResumoOperador(
                "operador-1", 2, 2, 0, List.of(), false);

        ResultadoSimulacao resultado = new ResultadoSimulacao(
                true, Duration.ZERO, null, List.of(operador), List.of(), List.of(certo, errado));

        assertFalse(resultado.consistente());
        assertEquals(1, resultado.problemas().size());
        assertTrue(resultado.problemas().get(0).contains("Bruno"));
        assertEquals(dinheiro("-10.00"), errado.diferenca());
    }
}
