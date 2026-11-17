package app.ui.telas;

import app.ContaResumo;
import app.ui.ControladorBanco;
import app.ui.ControladorBanco.Falha;
import app.ui.ControladorBanco.Resultado;
import app.ui.ControladorBanco.Tipo;
import app.ui.EstadoApp;
import app.ui.EstadoApp.Mudanca;
import app.ui.Moeda;
import app.ui.telas.PainelCelular.Aba;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.Botao;
import app.ui.tema.Componentes.CampoValor;
import app.ui.tema.Componentes.Estilo;
import app.ui.tema.Componentes.Folha;
import app.ui.tema.Componentes.Pilha;
import app.ui.tema.Componentes.Segmentos;
import app.ui.tema.Componentes.BotaoIcone;
import app.ui.tema.Componentes.TextoQuebrado;
import app.ui.tema.Icones;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Fonte;
import app.ui.tema.Tema.Medida;
import domain.Transacao;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Tela Operações.
 * <ul>
 *   <li><b>Propósito:</b> depositar, sacar e transferir; o resultado só aparece depois que o serviço confirma.</li>
 *   <li><b>Áreas:</b> barra superior · segmentos (Depósito, Saque, Transferência) · valor grande · \"Pagando com\"/\"Depositando em\"/\"Sacando de\" com o cartão da conta e o saldo atual (olho oculta) · \"Transferir para\" · botão primário fixo no fim.</li>
 *   <li><b>Componentes:</b> barraSuperior, segmentos, campoValor (máscara pt-BR), cartaoContaAtual, botaoIcone (olho), botao, folha de resultado.</li>
 *   <li><b>Dados e thread:</b> {@code depositar/sacar/transferir} do controlador em SwingWorker; controles desabilitados durante a chamada e reabilitados ao retorno; depois saldo, lista e extrato são recarregados pelo estado.</li>
 *   <li><b>Estados:</b> aguardando · em execução (sem envio duplicado) · concluída · recusada (saldo insuficiente, conta inexistente, origem = destino) · falha técnica.</li>
 *   <li><b>Erros:</b> valor inválido destaca o campo; conta não escolhida avisa abaixo dos seletores; falha de persistência diz para conferir o extrato antes de repetir.</li>
 * </ul>
 */
public final class PainelOperacoes extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient ControladorBanco controlador;
    private final transient EstadoApp estado;
    private final transient Consumer<Aba> navegar;

    private final Segmentos segmentos = Componentes.segmentos(
            TipoOperacao.DEPOSITO.segmento(), TipoOperacao.SAQUE.segmento(), TipoOperacao.TRANSFERENCIA.segmento());
    private final CampoValor valor = Componentes.campoValor("Valor");
    private final JLabel rotuloConta = Componentes.rotulo("", Fonte.SUBTITULO, Cor.TEXTO_SECUNDARIO);
    private final JLabel rotuloDestino = Componentes.rotulo("Transferir para", Fonte.SUBTITULO, Cor.TEXTO_SECUNDARIO);
    private final BotaoIcone olho = Componentes.botaoIcone(Icone.OLHO, "Ocultar saldo", false);
    private final Pilha zonaConta = Componentes.pilha();
    private final Pilha zonaDestino = Componentes.pilha();
    private final TextoQuebrado erroConta = Componentes.textoQuebrado(" ", Fonte.ROTULO, Cor.PERIGO);
    private final Botao acao = Componentes.botao("", null, Estilo.PRIMARIO);
    private final java.awt.Component folgaDestino = Componentes.espaco(Medida.ESP_3);

    private TipoOperacao tipo = TipoOperacao.DEPOSITO;
    private transient ContaResumo conta;
    private transient ContaResumo destino;
    private boolean ocupado;

    public PainelOperacoes(ControladorBanco controlador, EstadoApp estado, Consumer<Aba> navegar) {
        super(new BorderLayout());
        this.controlador = controlador;
        this.estado = estado;
        this.navegar = navegar;
        setOpaque(false);

        olho.addActionListener(e -> estado.alternarSaldoOculto());
        erroConta.setVisible(false);
        erroConta.setAlignmentX(LEFT_ALIGNMENT);

        add(Componentes.barraSuperior("Opera\u00e7\u00f5es"), BorderLayout.NORTH);

        Pilha campos = Componentes.pilha();
        campos.adicionar(segmentos).espaco(Medida.ESP_6);
        campos.adicionar(valor).espaco(Medida.ESP_6);
        JPanel linhaConta = new JPanel(new BorderLayout());
        linhaConta.setOpaque(false);
        linhaConta.add(rotuloConta, BorderLayout.WEST);
        linhaConta.add(olho, BorderLayout.EAST);
        campos.adicionar(linhaConta).espaco(Medida.ESP_1);
        campos.adicionar(zonaConta);
        campos.add(folgaDestino);
        campos.adicionar(rotuloDestino).espaco(Medida.ESP_2);
        campos.adicionar(zonaDestino).espaco(Medida.ESP_3);
        campos.adicionar(erroConta);
        Pilha conteudo = Componentes.pilha();
        conteudo.adicionar(Componentes.comMargemDeTela(campos)).espaco(Medida.ESP_6);
        add(Componentes.rolagem(conteudo), BorderLayout.CENTER);

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setBorder(BorderFactory.createEmptyBorder(Medida.ESP_3, Medida.MARGEM_TELA, Medida.ESP_4,
                Medida.MARGEM_TELA));
        rodape.add(acao, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        segmentos.aoMudar(i -> {
            tipo = TipoOperacao.values()[i];
            aplicarTipo();
        });
        acao.addActionListener(e -> executar());
        valor.aoConfirmar(this::executar);
        estado.adicionarOuvinte(this::aoMudar);
        conta = estado.contaSelecionada();
        aplicarTipo();
    }

    private void escolherPrincipal(ContaResumo escolhida) {
        conta = escolhida;
        erroConta.setVisible(false);
        redesenharContas();
    }

    private void escolherDestino(ContaResumo escolhida) {
        destino = escolhida;
        erroConta.setVisible(false);
        redesenharContas();
    }

    /** Reconstrói os cartões de conta com os dados atuais (saldo "Atual" vem do estado). */
    private void redesenharContas() {
        boolean oculto = estado.saldoOculto();
        olho.setIcone(oculto ? Icone.OLHO_FECHADO : Icone.OLHO, oculto ? "Mostrar saldo" : "Ocultar saldo");
        zonaConta.removeAll();
        zonaConta.adicionar(Componentes.cartaoContaAtual(conta, true, oculto).aoClicar(() -> {
            if (!ocupado) {
                Componentes.escolherConta(this, conta, estado::contas, estado::saldoOculto, this::escolherPrincipal);
            }
        }));
        zonaDestino.removeAll();
        zonaDestino.adicionar(Componentes.cartaoContaAtual(destino, false, oculto).aoClicar(() -> {
            if (!ocupado) {
                Componentes.escolherConta(this, destino, estado::contas, estado::saldoOculto, this::escolherDestino);
            }
        }));
        revalidate();
        repaint();
    }

    private ContaResumo atualizada(ContaResumo c) {
        if (c == null) {
            return null;
        }
        for (ContaResumo candidata : estado.contas()) {
            if (candidata.id() == c.id()) {
                return candidata;
            }
        }
        return c;
    }

    /** Usado pelas ações rápidas do Início: pré-seleciona o tipo da operação. */
    public void selecionarTipo(TipoOperacao novo) {
        this.tipo = novo;
        segmentos.selecionar(novo.ordinal());
        aplicarTipo();
    }

    private void aplicarTipo() {
        boolean transferencia = tipo == TipoOperacao.TRANSFERENCIA;
        rotuloConta.setText(switch (tipo) {
            case DEPOSITO -> "Depositando em";
            case SAQUE -> "Sacando de";
            case TRANSFERENCIA -> "Pagando com";
        });
        rotuloDestino.setVisible(transferencia);
        zonaDestino.setVisible(transferencia);
        folgaDestino.setVisible(transferencia);
        redesenharContas();
        acao.setText(tipo.botao());
        valor.limparErro();
        erroConta.setVisible(false);
        revalidate();
        repaint();
    }

    private void aoMudar(Mudanca mudanca) {
        switch (mudanca) {
            case SELECAO -> {
                if (!ocupado) {
                    conta = estado.contaSelecionada();
                    erroConta.setVisible(false);
                }
                redesenharContas();
            }
            case CONTAS -> {
                conta = atualizada(conta);
                destino = atualizada(destino);
                redesenharContas();
            }
            case SALDO_OCULTO -> redesenharContas();
            case OPERACAO -> { }
        }
    }

    private void executar() {
        if (ocupado) {
            return;
        }
        valor.limparErro();
        erroConta.setVisible(false);
        if (conta == null) {
            avisarConta(tipo == TipoOperacao.DEPOSITO ? "Selecione a conta de destino." : "Selecione a conta de origem.");
            return;
        }
        if (tipo == TipoOperacao.TRANSFERENCIA && destino == null) {
            avisarConta("Selecione a conta de destino.");
            return;
        }
        TipoOperacao pedido = tipo;
        BigDecimal quantia = valor.valor();
        ContaResumo principal = conta;
        ContaResumo outra = destino;
        ocupar(true);
        Consumer<Resultado<Transacao>> aoConcluir = resultado -> {
            try {
                ocupar(false);
                concluir(pedido, quantia, principal, outra, resultado);
            } finally {
                ocupar(false);
            }
        };
        switch (pedido) {
            case DEPOSITO -> controlador.depositar(principal.id(), quantia, aoConcluir);
            case SAQUE -> controlador.sacar(principal.id(), quantia, aoConcluir);
            case TRANSFERENCIA -> controlador.transferir(principal.id(), outra.id(), quantia, aoConcluir);
        }
    }

    private void avisarConta(String mensagem) {
        erroConta.setTexto(mensagem);
        erroConta.setVisible(true);
        revalidate();
    }

    private void ocupar(boolean emExecucao) {
        ocupado = emExecucao;
        boolean livre = !emExecucao;
        acao.setEnabled(livre);
        segmentos.setEnabled(livre);
        valor.setEnabled(livre);
        olho.setEnabled(livre);
    }

    private void concluir(TipoOperacao pedido, BigDecimal quantia, ContaResumo principal, ContaResumo outra,
                          Resultado<Transacao> resultado) {
        long[] ids = pedido == TipoOperacao.TRANSFERENCIA
                ? new long[] {principal.id(), outra.id()} : new long[] {principal.id()};
        if (resultado.ok()) {
            valor.limpar();
            estado.operacaoConcluida(ids);   // recarrega saldo, lista e extratos enquanto a folha está aberta
            mostrarResultado(true, pedido, quantia, principal, outra, null);
            return;
        }
        Falha falha = resultado.falha();
        if (falha.tipo() == Tipo.VALOR) {
            valor.mostrarErro(falha.oQueAconteceu());
        } else if (falha.recusa()) {
            mostrarResultado(false, pedido, quantia, principal, outra, falha);
        } else {
            if (falha.tipo() == Tipo.BANCO) {
                estado.operacaoConcluida(ids);   // a operação pode ter sido aplicada: mostra o estado real
            }
            Componentes.mostrarErro(this, Icone.ERRO, falha.titulo(), falha.oQueAconteceu(), falha.oQueFazer(),
                    falha.detalheTecnico());
        }
    }

    private void mostrarResultado(boolean sucesso, TipoOperacao pedido, BigDecimal quantia, ContaResumo principal,
                                  ContaResumo outra, Falha falha) {
        Pilha corpo = Componentes.pilha();
        JLabel titulo = Componentes.rotulo(sucesso ? pedido.tituloSucesso() : pedido.tituloRecusa(),
                Fonte.SECAO, Cor.TEXTO);
        titulo.setIcon(Icones.de(sucesso ? Icone.OK : Icone.ALERTA, Medida.ICONE_GRANDE,
                sucesso ? Cor.SUCESSO : Cor.ATENCAO));
        titulo.setIconTextGap(Medida.ESP_3);
        corpo.adicionar(titulo).espaco(Medida.ESP_4);
        corpo.adicionar(Componentes.rotulo(Moeda.formatar(quantia), Fonte.SALDO, Cor.TEXTO)).espaco(Medida.ESP_2);
        corpo.adicionar(Componentes.textoQuebrado(
                sucesso ? pedido.mensagemSucesso() : falha.oQueAconteceu() + " " + falha.oQueFazer(),
                Fonte.CORPO, sucesso ? Cor.TEXTO_SECUNDARIO : Cor.TEXTO)).espaco(Medida.ESP_4);
        switch (pedido) {
            case DEPOSITO -> linhaConta(corpo, "Para", principal);
            case SAQUE -> linhaConta(corpo, "De", principal);
            case TRANSFERENCIA -> {
                linhaConta(corpo, "De", principal);
                linhaConta(corpo, "Para", outra);
            }
        }
        Botao fechar = Componentes.botao("Fechar", null, Estilo.SECUNDARIO);
        Botao verExtrato = Componentes.botao("Ver extrato", Icone.HISTORICO, Estilo.PRIMARIO);
        JPanel botoes = new JPanel(new GridLayout(1, 2, Medida.ESP_3, 0));
        botoes.setOpaque(false);
        botoes.add(fechar);
        botoes.add(verExtrato);
        corpo.espaco(Medida.ESP_2).adicionar(botoes);

        Folha folha = Componentes.folha(this, corpo);
        boolean[] irParaExtrato = {false};
        fechar.addActionListener(e -> folha.fechar());
        verExtrato.addActionListener(e -> {
            irParaExtrato[0] = true;
            folha.fechar();
        });
        folha.mostrar();
        if (irParaExtrato[0]) {
            estado.selecionar(principal);
            navegar.accept(Aba.EXTRATO);
        }
    }

    private static void linhaConta(Pilha corpo, String papel, ContaResumo c) {
        corpo.adicionar(Componentes.rotulo(papel, Fonte.ROTULO, Cor.TEXTO_SECUNDARIO)).espaco(Medida.ESP_1);
        corpo.adicionar(Componentes.rotulo(c.titular() + "  \u00b7  Conta n\u00ba " + c.id(), Fonte.SUBTITULO, Cor.TEXTO))
                .espaco(Medida.ESP_3);
    }
}
