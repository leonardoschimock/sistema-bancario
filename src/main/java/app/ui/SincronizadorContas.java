package app.ui;

import app.ui.EstadoApp.Mudanca;
import java.util.List;

/**
 * Mantém {@link EstadoApp} em dia com o banco: lê a lista de contas e, depois de cada operação
 * confirmada, consulta o saldo das contas envolvidas (via {@link ControladorBanco}, em SwingWorker)
 * e o copia para o estado. As telas só observam o estado.
 */
public final class SincronizadorContas {

    private final ControladorBanco controlador;
    private final EstadoApp estado;

    public SincronizadorContas(ControladorBanco controlador, EstadoApp estado) {
        this.controlador = controlador;
        this.estado = estado;
        estado.adicionarOuvinte(mudanca -> {
            if (mudanca == Mudanca.OPERACAO) {
                atualizarSaldos(estado.ultimasContasAfetadas());
            }
        });
    }

    /** Lê as contas do banco e entrega o resultado, real, ao estado. */
    public void carregarContas() {
        estado.iniciarCargaContas();
        controlador.listarContas(resultado -> {
            if (resultado.ok()) {
                estado.definirContas(resultado.valor());
            } else {
                estado.definirFalhaContas(resultado.falha());
            }
        });
    }

    private void atualizarSaldos(List<Long> ids) {
        for (long id : ids) {
            controlador.consultarSaldo(id, resultado -> {
                if (resultado.ok()) {
                    estado.atualizarSaldo(id, resultado.valor());
                }
            });
        }
    }
}
