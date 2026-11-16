package app.ui.telas;

import app.ContaResumo;
import app.ui.EstadoApp;
import app.ui.Moeda;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.ItemMovimentacao;
import app.ui.tema.Icones.Icone;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import domain.TipoTransacao;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Converte lançamentos do extrato em texto e itens visuais (Início e Extrato). Só apresentação. */
final class ApresentacaoExtrato {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ApresentacaoExtrato() {
    }

    static Icone icone(TipoTransacao tipo) {
        return switch (tipo) {
            case DEPOSITO -> Icone.DEPOSITO;
            case SAQUE -> Icone.SAQUE;
            case TRANSFERENCIA -> Icone.TRANSFERENCIA;
        };
    }

    /** Titular só se a conta constar na lista conhecida; senão, {@code conta N}. */
    static String nomeDaConta(EstadoApp estado, Long id) {
        for (ContaResumo conta : estado.contas()) {
            if (id != null && conta.id() == id) {
                return conta.titular();
            }
        }
        return "conta n\u00ba " + id;
    }

    static String descricao(LancamentoExtrato lancamento, EstadoApp estado) {
        return switch (lancamento.transacao().getTipo()) {
            case DEPOSITO -> "Dep\u00f3sito";
            case SAQUE -> "Saque";
            case TRANSFERENCIA -> (lancamento.sentido() == Sentido.ENTRADA
                    ? "Transfer\u00eancia recebida de " : "Transfer\u00eancia enviada para ")
                    + nomeDaConta(estado, lancamento.contaRelacionadaId());
        };
    }

    static String hora(LocalDateTime horario) {
        return horario.format(HORA);
    }

    /** {@code Hoje}, {@code Ontem} ou {@code dd/MM/yyyy}, a partir do horário real. */
    static String rotuloData(LocalDate hoje, LocalDate data) {
        if (data.equals(hoje)) {
            return "Hoje";
        }
        if (data.equals(hoje.minusDays(1))) {
            return "Ontem";
        }
        return data.format(DATA);
    }

    static ItemMovimentacao item(LancamentoExtrato lancamento, EstadoApp estado, String detalhe, boolean divisor) {
        boolean entrada = lancamento.sentido() == Sentido.ENTRADA;
        return Componentes.itemMovimentacao(icone(lancamento.transacao().getTipo()), descricao(lancamento, estado),
                        detalhe, Moeda.formatarComSinal(lancamento.transacao().getValor(), entrada), entrada)
                .comDivisor(divisor);
    }
}
