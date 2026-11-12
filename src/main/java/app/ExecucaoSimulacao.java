package app;

import java.util.List;
import simulacao.ResultadoSimulacao;

/**
 * O que {@link BancoServico#executarSimulacao} devolve: o veredito da simulação e, se foi pedido
 * e permitido, as contas que ficaram gravadas no banco.
 *
 * @param resultado     o {@link ResultadoSimulacao} (saldos esperados x obtidos, recusas, falhas)
 * @param contasGravadas contas gravadas ao final; VAZIA se não foi pedido para persistir, ou se a
 *                       simulação não terminou ou terminou inconsistente (dados suspeitos não são
 *                       gravados; veja {@code resultado.problemas()})
 */
public record ExecucaoSimulacao(ResultadoSimulacao resultado, List<ContaResumo> contasGravadas) {

    public ExecucaoSimulacao {
        if (resultado == null) {
            throw new IllegalArgumentException("O resultado da simulação é obrigatório.");
        }
        contasGravadas = (contasGravadas == null) ? List.of() : List.copyOf(contasGravadas);
    }
}
