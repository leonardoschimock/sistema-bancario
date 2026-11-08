package simulacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Conta;
import domain.TipoTransacao;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes do cenário: o plano gerado é reproduzível, o saldo esperado é coerente com ele e
 * nenhuma operação pode ser recusada. Não usam threads nem banco (testes unitários).
 */
class CenarioSimulacaoTest {

    /** Reduz o plano a texto (tipo, valor e titulares) para comparar dois cenários distintos. */
    private static List<String> assinatura(CenarioSimulacao cenario) {
        List<String> assinatura = new ArrayList<>();
        for (List<OperacaoPlanejada> plano : cenario.getPlanos()) {
            for (OperacaoPlanejada op : plano) {
                assinatura.add(op.tipo() + "|" + op.valor() + "|" + op.conta().getTitular().getNome() + "|"
                        + (op.destino() == null ? "-" : op.destino().getTitular().getNome()));
            }
        }
        return assinatura;
    }

    @Test
    @DisplayName("A mesma seed gera o mesmo plano e os mesmos saldos esperados; outra seed gera plano diferente")
    void mesmaSeedGeraMesmoPlano() {
        CenarioSimulacao a = CenarioSimulacao.gerar(42, 4, 500, 3);
        CenarioSimulacao b = CenarioSimulacao.gerar(42, 4, 500, 3);
        CenarioSimulacao outra = CenarioSimulacao.gerar(43, 4, 500, 3);

        assertEquals(assinatura(a), assinatura(b));
        assertEquals(a.getSaldosEsperados(), b.getSaldosEsperados());
        assertEquals(a.getSaldosIniciais(), b.getSaldosIniciais());
        assertEquals(a.getContagemPorTipo(), b.getContagemPorTipo());
        assertTrue(!assinatura(a).equals(assinatura(outra)), "Seeds diferentes deveriam gerar planos diferentes.");
    }

    @Test
    @DisplayName("O saldo inicial de cada conta cobre todos os débitos do plano (nenhuma operação pode ser recusada)")
    void saldoInicialCobreTodosOsDebitos() {
        CenarioSimulacao cenario = CenarioSimulacao.gerar(7, 6, 2_000, 4);

        for (Conta conta : cenario.getContas()) {
            BigDecimal debitos = BigDecimal.ZERO;
            for (List<OperacaoPlanejada> plano : cenario.getPlanos()) {
                for (OperacaoPlanejada op : plano) {
                    if (op.conta() == conta && op.tipo() != TipoTransacao.DEPOSITO) {
                        debitos = debitos.add(op.valor());
                    }
                }
            }
            assertTrue(conta.getSaldo().compareTo(debitos) >= 0,
                    "Saldo inicial de " + conta.getTitular().getNome() + " não cobre os débitos: "
                            + conta.getSaldo() + " < " + debitos);
        }
    }

    @Test
    @DisplayName("Controle: executar o plano em UMA thread, pelos métodos de Conta, dá exatamente o saldo esperado")
    void execucaoSequencialDaOSaldoEsperado() {
        CenarioSimulacao cenario = CenarioSimulacao.gerar(42, 4, 3_000, 4);

        int i = 1;
        for (List<OperacaoPlanejada> plano : cenario.getPlanos()) {
            OperacaoBancariaThread operador = new OperacaoBancariaThread("seq-" + i++, plano);
            operador.run(); // direto na thread do teste: sem concorrência
            assertEquals(0, operador.getRecusadas());
        }

        for (int c = 0; c < cenario.getContas().size(); c++) {
            assertEquals(cenario.getSaldosEsperados().get(c), cenario.getContas().get(c).getSaldo(),
                    "Saldo sequencial diferente do esperado na conta " + c);
        }
    }

    @Test
    @DisplayName("Parâmetros inválidos são recusados")
    void recusaParametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> CenarioSimulacao.gerar(1, 0, 10, 2));
        assertThrows(IllegalArgumentException.class, () -> CenarioSimulacao.gerar(1, 2, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> CenarioSimulacao.gerar(1, 2, 10, 1));
    }
}
