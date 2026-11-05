package domain;

/**
 * Lançada quando um saque ou uma transferência exige mais do que o saldo
 * disponível na conta de origem.
 *
 * É uma exceção não verificada (unchecked) para não obrigar threads e testes
 * a declarar "throws" em cada chamada.
 */
public class SaldoInsuficienteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SaldoInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
