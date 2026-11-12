package app;

import domain.Conta;
import java.math.BigDecimal;

/**
 * Visão SOMENTE LEITURA de uma conta, própria para a interface (console ou gráfica) exibir.
 *
 * Existe para que quem está fora do domínio nunca receba a {@link Conta} em si: sem a instância,
 * não há como chamar {@code depositar}, {@code sacar} ou {@code transferirPara} por fora do
 * {@link BancoServico}.
 *
 * @param id      identificador da conta no banco (o mesmo usado em todas as operações do serviço)
 * @param titular nome do titular
 * @param saldo   saldo no INSTANTE da leitura; com outras operações em andamento ele pode já ter
 *                mudado quando for exibido
 */
public record ContaResumo(long id, String titular, BigDecimal saldo) {

    public ContaResumo {
        if (titular == null || titular.isBlank()) {
            throw new IllegalArgumentException("O titular é obrigatório.");
        }
        if (saldo == null) {
            throw new IllegalArgumentException("O saldo é obrigatório.");
        }
    }

    /** Fotografia de uma conta já persistida (lê o saldo pelo método de {@link Conta}, com o lock dela). */
    static ContaResumo de(Conta conta) {
        Long id = conta.getIdPersistencia();
        if (id == null) {
            throw new IllegalStateException("A conta ainda não foi persistida.");
        }
        return new ContaResumo(id, conta.getTitular().getNome(), conta.getSaldo());
    }
}
