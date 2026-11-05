package domain;

/**
 * Titular de uma conta.
 *
 * A especificação não define os dados do cliente. Por isso, mantém-se apenas
 * o nome, o mínimo para identificar o titular. Novos atributos só devem ser
 * adicionados com confirmação do professor.
 */
public class Cliente {

    private final String nome;

    public Cliente(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("O nome do cliente é obrigatório.");
        }
        this.nome = nome.trim();
    }

    public String getNome() {
        return nome;
    }

    @Override
    public String toString() {
        return "Cliente{nome='" + nome + "'}";
    }
}
