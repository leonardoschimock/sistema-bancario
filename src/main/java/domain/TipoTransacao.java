package domain;

/**
 * Tipos de operação bancária registrados em uma {@link Transacao}.
 *
 * A especificação não fecha esse conjunto; estes três refletem as operações
 * pedidas (depósito, saque e transferência).
 */
public enum TipoTransacao {
    DEPOSITO,
    SAQUE,
    TRANSFERENCIA
}
