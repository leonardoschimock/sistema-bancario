package app;

import java.time.Duration;

/**
 * Parâmetros de uma simulação concorrente pedida a {@link BancoServico#executarSimulacao}.
 *
 * Os valores numéricos são validados por {@code CenarioSimulacao.gerar} (threads, operações e
 * contas) e por {@code SimulacaoConcorrente.executar} (prazo); quem violar recebe
 * {@link IllegalArgumentException}.
 *
 * @param seed             semente do sorteio (mesma seed, mesmo plano e mesmos saldos esperados)
 * @param threads          quantidade de threads de operação
 * @param operacoesPorThread operações de cada thread
 * @param numeroDeContas   contas compartilhadas por todas as threads (&gt;= 2)
 * @param prazo            tempo máximo para a simulação inteira
 * @param persistirAoFinal se {@code true}, grava as contas no banco DEPOIS de a simulação terminar
 *                         de forma consistente (nunca durante a execução das threads)
 */
public record ParametrosSimulacao(long seed, int threads, int operacoesPorThread,
                                  int numeroDeContas, Duration prazo, boolean persistirAoFinal) {

    public ParametrosSimulacao {
        if (prazo == null) {
            throw new IllegalArgumentException("O prazo da simulação é obrigatório.");
        }
    }

    /** Os mesmos valores padrão de {@code Main}: seed 42, 8 threads, 20.000 operações, 4 contas, 120 s. */
    public static ParametrosSimulacao padrao() {
        return new ParametrosSimulacao(42, 8, 20_000, 4, Duration.ofSeconds(120), false);
    }
}
