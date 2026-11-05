package simulacao.didatico;

import java.math.BigDecimal;

/**
 * *** CLASSE EXCLUSIVAMENTE DIDÁTICA — NÃO USAR NO SISTEMA ***
 *
 * Reproduz a lógica de depósito/saque de {@code domain.Conta} SEM nenhuma proteção
 * contra acesso concorrente, para demonstrar a race condition (Semana 2).
 *
 * Esta classe existe separada de Conta de propósito: quando Conta for sincronizada
 * (Semana 3), a demonstração do "antes" continua reproduzível sem precisar
 * desfazer a correção do domínio.
 *
 * O problema está em {@code saldo = saldo.add(valor)}: parece uma linha só, mas são
 * três passos (ler, calcular, gravar). Duas threads podem ler o mesmo saldo antigo,
 * e a segunda gravação sobrescreve a primeira (perda de atualização).
 *
 * PRESERVADA de propósito após a correção (Semana 3): é a evidência do "antes" do relatório.
 * A implementação segura é {@code domain.Conta}; esta classe nunca deve ser usada pelo sistema.
 */
public final class ContaInsegura {

    private BigDecimal saldo; // estado compartilhado, SEM proteção (propositalmente)

    public ContaInsegura(BigDecimal saldoInicial) {
        this.saldo = saldoInicial;
    }

    public void depositar(BigDecimal valor) {
        saldo = saldo.add(valor);          // ler -> calcular -> gravar (não atômico)
    }

    public void sacar(BigDecimal valor) {
        if (saldo.compareTo(valor) < 0) {  // verificar e depois agir (também não atômico)
            throw new IllegalStateException("Saldo insuficiente");
        }
        saldo = saldo.subtract(valor);
    }

    public BigDecimal getSaldo() {
        return saldo;
    }
}
