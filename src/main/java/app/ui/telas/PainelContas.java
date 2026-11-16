package app.ui.telas;

import app.ContaResumo;
import app.ui.ControladorBanco;
import app.ui.ControladorBanco.Falha;
import app.ui.EstadoApp;
import app.ui.EstadoApp.Carga;
import app.ui.EstadoApp.Mudanca;
import app.ui.telas.PainelCelular.Aba;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.Estilo;
import app.ui.tema.Componentes.Pilha;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Medida;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * Tela Contas.
 * <ul>
 *   <li><b>Propósito:</b> listar as contas do banco, escolher a conta ativa e criar contas novas.</li>
 *   <li><b>Áreas:</b> barra superior ("Contas" + ação Nova conta) e lista de cartões de conta; a conta ativa tem borda primária e o texto "Selecionada".</li>
 *   <li><b>Componentes:</b> barraSuperior, botao, cartaoConta, mensagem, folha "Nova conta" (nome + saldo inicial).</li>
 *   <li><b>Dados e thread:</b> a lista vem de {@code listarContas} (carregada pela janela, em SwingWorker) e fica em {@link EstadoApp}; criar conta usa SwingWorker.</li>
 *   <li><b>Estados:</b> "Carregando contas..." · lista · "Nenhuma conta cadastrada." · falha com "Tentar de novo".</li>
 *   <li><b>Erros:</b> nome vazio, saldo negativo ou com mais de 2 casas e falha de persistência, mostrados na folha em linguagem simples.</li>
 * </ul>
 */
public final class PainelContas extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient ControladorBanco controlador;
    private final transient EstadoApp estado;
    private final transient Runnable recarregarContas;
    private final transient Consumer<Aba> navegar;
    private final Pilha lista = Componentes.pilha();

    public PainelContas(ControladorBanco controlador, EstadoApp estado, Runnable recarregarContas,
                        Consumer<Aba> navegar) {
        super(new BorderLayout());
        this.controlador = controlador;
        this.estado = estado;
        this.recarregarContas = recarregarContas;
        this.navegar = navegar;
        setOpaque(false);

        var nova = Componentes.botao("Nova conta", Icone.ADICIONAR, Estilo.FANTASMA).compacto();
        nova.addActionListener(e -> FolhaNovaConta.abrir(this, controlador, estado));
        add(Componentes.barraSuperior("Contas", nova), BorderLayout.NORTH);

        Pilha conteudo = Componentes.pilha();
        conteudo.adicionar(Componentes.comMargemDeTela(lista)).espaco(Medida.ESP_6);
        add(Componentes.rolagem(conteudo), BorderLayout.CENTER);

        estado.adicionarOuvinte(this::aoMudar);
        redesenhar();
    }

    private void aoMudar(Mudanca mudanca) {
        if (mudanca == Mudanca.CONTAS || mudanca == Mudanca.SELECAO || mudanca == Mudanca.SALDO_OCULTO) {
            redesenhar();
        }
    }

    private void redesenhar() {
        lista.removeAll();
        Carga carga = estado.carga();
        if (carga == Carga.FALHA && estado.falhaContas() != null) {
            Falha falha = estado.falhaContas();
            lista.adicionar(Componentes.mensagem(falha.oQueAconteceu() + " " + falha.oQueFazer()));
            var tentar = Componentes.botao("Tentar de novo", Icone.ATUALIZAR, Estilo.SECUNDARIO);
            tentar.addActionListener(e -> recarregarContas.run());
            lista.adicionar(tentar);
        } else if (carga == Carga.CARREGANDO || carga == Carga.NAO_INICIADA) {
            lista.adicionar(Componentes.mensagem("Carregando contas..."));
        } else if (estado.contas().isEmpty()) {
            lista.adicionar(Componentes.mensagem("Nenhuma conta cadastrada."));
        } else {
            ContaResumo ativa = estado.contaSelecionada();
            for (ContaResumo conta : estado.contas()) {
                boolean selecionada = ativa != null && ativa.id() == conta.id();
                var cartao = Componentes.cartaoConta(conta, selecionada, estado.saldoOculto());
                cartao.aoClicar(() -> {
                    estado.selecionar(conta);
                    navegar.accept(Aba.INICIO);
                });
                lista.adicionar(cartao).espaco(Medida.ESP_3);
            }
        }
        lista.revalidate();
        lista.repaint();
    }
}
