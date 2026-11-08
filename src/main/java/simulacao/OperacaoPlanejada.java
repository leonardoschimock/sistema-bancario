package simulacao;

import domain.Conta;
import domain.TipoTransacao;
import java.math.BigDecimal;

/**
 * Uma operação bancária PLANEJADA: o que uma thread deverá pedir a uma {@link Conta}.
 *
 * É só um dado (record). Não executa nada e não conhece regra bancária: se o valor é válido,
 * se há saldo, se origem e destino são a mesma conta, quem decide é {@link Conta}. Este record
 * só garante que o plano é coerente em si: transferência tem destino; depósito e saque não.
 *
 * <ul>
 *   <li>{@code DEPOSITO}: deposita {@code valor} em {@code conta};</li>
 *   <li>{@code SAQUE}: saca {@code valor} de {@code conta};</li>
 *   <li>{@code TRANSFERENCIA}: {@code conta} é a ORIGEM e {@code destino} é o destino.</li>
 * </ul>
 */
public record OperacaoPlanejada(TipoTransacao tipo, Conta conta, Conta destino, BigDecimal valor) {

    public OperacaoPlanejada {
        if (tipo == null) {
            throw new IllegalArgumentException("O tipo da operação é obrigatório.");
        }
        if (conta == null) {
            throw new IllegalArgumentException("A conta da operação é obrigatória.");
        }
        if (valor == null) {
            throw new IllegalArgumentException("O valor da operação é obrigatório.");
        }
        if (tipo == TipoTransacao.TRANSFERENCIA && destino == null) {
            throw new IllegalArgumentException("Transferência exige a conta de destino.");
        }
        if (tipo != TipoTransacao.TRANSFERENCIA && destino != null) {
            throw new IllegalArgumentException("Somente transferência possui conta de destino.");
        }
    }

    public static OperacaoPlanejada deposito(Conta conta, BigDecimal valor) {
        return new OperacaoPlanejada(TipoTransacao.DEPOSITO, conta, null, valor);
    }

    public static OperacaoPlanejada saque(Conta conta, BigDecimal valor) {
        return new OperacaoPlanejada(TipoTransacao.SAQUE, conta, null, valor);
    }

    public static OperacaoPlanejada transferencia(Conta origem, Conta destino, BigDecimal valor) {
        return new OperacaoPlanejada(TipoTransacao.TRANSFERENCIA, origem, destino, valor);
    }
}
