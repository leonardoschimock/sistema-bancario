package domain;

/**
 * Uma linha do extrato de uma conta: a {@link Transacao} vista DO PONTO DE VISTA dessa conta.
 *
 * {@link Transacao} é imutável e não conhece contas, então sozinha ela não diz se uma
 * transferência entrou ou saiu. Este registro acrescenta o que o extrato precisa:
 * <ul>
 *   <li>{@link Sentido}: se o lançamento aumentou (ENTRADA) ou diminuiu (SAIDA) o saldo da conta
 *       consultada;</li>
 *   <li>{@code contaRelacionadaId}: na transferência, o id (no banco) da OUTRA conta; nulo em
 *       depósito e saque.</li>
 * </ul>
 * É somente leitura (record): não altera saldo nem a transação original.
 */
public record LancamentoExtrato(Transacao transacao, Sentido sentido, Long contaRelacionadaId) {

    /** Efeito do lançamento sobre o saldo da conta cujo extrato está sendo consultado. */
    public enum Sentido {
        ENTRADA,
        SAIDA
    }

    public LancamentoExtrato {
        if (transacao == null) {
            throw new IllegalArgumentException("A transação do lançamento é obrigatória.");
        }
        if (sentido == null) {
            throw new IllegalArgumentException("O sentido do lançamento é obrigatório.");
        }
        boolean transferencia = transacao.getTipo() == TipoTransacao.TRANSFERENCIA;
        if (transferencia && contaRelacionadaId == null) {
            throw new IllegalArgumentException("Transferência exige a conta relacionada.");
        }
        if (!transferencia && contaRelacionadaId != null) {
            throw new IllegalArgumentException("Somente transferência possui conta relacionada.");
        }
    }
}