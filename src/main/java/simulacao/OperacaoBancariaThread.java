package simulacao;

import domain.Conta;
import domain.SaldoInsuficienteException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simula UM cliente realizando uma sequência de operações bancárias sobre contas compartilhadas.
 *
 * <h2>Responsabilidade</h2>
 * Esta classe só percorre o plano e chama {@code depositar}, {@code sacar} ou
 * {@code transferirPara} da {@link Conta}. Ela NÃO tem acesso ao saldo (nem existe setter para
 * ele), NÃO valida saldo, NÃO ordena locks e NÃO usa {@code synchronized}: toda a regra e toda a
 * proteção contra concorrência estão em {@link Conta}. Por isso a mesma classe serve para
 * demonstrar tanto a conta segura quanto, no futuro, qualquer outra implementação de Conta.
 *
 * <h2>Tratamento de falhas (nada é escondido)</h2>
 * <ul>
 *   <li>{@link SaldoInsuficienteException} é um resultado de negócio esperado numa simulação de
 *       carga. A operação é CONTADA em {@link #getRecusadas()}, as primeiras ocorrências ficam
 *       registradas em {@link #getAmostrasDeRecusa()} e a thread segue com a próxima operação.
 *       Quem monta o cenário decide se recusas são aceitáveis; {@link ResultadoSimulacao} as
 *       reporta.</li>
 *   <li>QUALQUER outra exceção (argumento inválido, erro de programação etc.) NÃO é capturada:
 *       sobe por {@link #run()}, a thread para e quem a executa recebe a exceção (no
 *       {@code ExecutorService}, via {@code Future.get()} dentro de uma {@code ExecutionException}).
 *       Os contadores ficam disponíveis para mostrar até onde a thread chegou.</li>
 * </ul>
 *
 * <h2>Visibilidade dos contadores</h2>
 * Os contadores são campos comuns (sem {@code volatile}/atômicos) porque cada instância é
 * executada por UMA thread só. Eles só devem ser lidos depois que a execução terminou (por
 * exemplo, após {@code Future.get()}), momento em que o Java garante que os valores gravados
 * pela thread estão visíveis para quem lê (relação happens-before).
 */
public class OperacaoBancariaThread implements Runnable {

    /** Quantas mensagens de recusa guardar (evita crescer sem limite numa carga grande). */
    static final int MAX_AMOSTRAS_DE_RECUSA = 3;

    private final String nome;
    private final List<OperacaoPlanejada> operacoes;

    // Estado privado desta execução: escrito somente pela thread que roda run().
    private int aceitas;
    private int recusadas;
    private boolean interrompida;
    private final List<String> amostrasDeRecusa = new ArrayList<>();

    public OperacaoBancariaThread(String nome, List<OperacaoPlanejada> operacoes) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome da thread de operação é obrigatório.");
        }
        if (operacoes == null) {
            throw new IllegalArgumentException("A lista de operações é obrigatória.");
        }
        this.nome = nome;
        this.operacoes = List.copyOf(operacoes); // cópia imutável: o plano não muda durante a execução
    }

    @Override
    public void run() {
        for (OperacaoPlanejada operacao : operacoes) {
            if (Thread.currentThread().isInterrupted()) {
                interrompida = true; // cancelamento cooperativo (ex.: shutdownNow após estouro de tempo)
                return;
            }
            try {
                executar(operacao);
                aceitas++;
            } catch (SaldoInsuficienteException e) {
                recusadas++;
                if (amostrasDeRecusa.size() < MAX_AMOSTRAS_DE_RECUSA) {
                    amostrasDeRecusa.add(descrever(operacao) + " -> " + e.getMessage());
                }
            }
            // Qualquer outra exceção propaga de propósito (ver Javadoc da classe).
        }
    }

    /** Traduz a operação planejada em UMA chamada ao método correspondente de Conta. */
    private static void executar(OperacaoPlanejada operacao) {
        switch (operacao.tipo()) {
            case DEPOSITO -> operacao.conta().depositar(operacao.valor());
            case SAQUE -> operacao.conta().sacar(operacao.valor());
            case TRANSFERENCIA -> operacao.conta().transferirPara(operacao.destino(), operacao.valor());
            default -> throw new IllegalStateException("Tipo de operação não tratado: " + operacao.tipo());
        }
    }

    private static String descrever(OperacaoPlanejada op) {
        String titular = op.conta().getTitular().getNome();
        return switch (op.tipo()) {
            case DEPOSITO -> "depósito de " + op.valor() + " em " + titular;
            case SAQUE -> "saque de " + op.valor() + " de " + titular;
            case TRANSFERENCIA -> "transferência de " + op.valor() + " de " + titular
                    + " para " + op.destino().getTitular().getNome();
        };
    }

    public String getNome() {
        return nome;
    }

    public int getTotalPlanejado() {
        return operacoes.size();
    }

    /** Operações que a {@link Conta} aceitou e aplicou. Ler só após o fim da execução. */
    public int getAceitas() {
        return aceitas;
    }

    /** Operações recusadas por saldo insuficiente. Ler só após o fim da execução. */
    public int getRecusadas() {
        return recusadas;
    }

    /** As primeiras recusas, em texto. Ler só após o fim da execução. */
    public List<String> getAmostrasDeRecusa() {
        return List.copyOf(amostrasDeRecusa);
    }

    /** {@code true} se a thread parou no meio do plano por ter sido interrompida. */
    public boolean foiInterrompida() {
        return interrompida;
    }
}
