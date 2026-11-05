package domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro imutável de uma operação bancária: tipo, valor e horário.
 *
 * Imutabilidade: a classe é final, todos os atributos são privados e finais,
 * não há setters, e os tipos usados (enum, BigDecimal, LocalDateTime) também
 * são imutáveis.
 */
public final class Transacao {

    private final TipoTransacao tipo;
    private final BigDecimal valor;
    private final LocalDateTime horario;

    /** Cria uma transação registrada no momento atual. */
    public Transacao(TipoTransacao tipo, BigDecimal valor) {
        this(tipo, valor, LocalDateTime.now());
    }

    /** Cria uma transação com horário informado (útil para testes e para recuperar dados salvos). */
    public Transacao(TipoTransacao tipo, BigDecimal valor, LocalDateTime horario) {
        if (tipo == null) {
            throw new IllegalArgumentException("O tipo da transação é obrigatório.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalArgumentException("O valor da transação deve ser maior que zero.");
        }
        if (horario == null) {
            throw new IllegalArgumentException("O horário da transação é obrigatório.");
        }
        this.tipo = tipo;
        this.valor = valor;
        this.horario = horario;
    }

    public TipoTransacao getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    @Override
    public String toString() {
        return "Transacao{tipo=" + tipo + ", valor=" + valor + ", horario=" + horario + "}";
    }
}
