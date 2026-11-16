package app.ui.telas;

import app.ContaResumo;
import app.ui.ControladorBanco;
import app.ui.ControladorBanco.Falha;
import app.ui.EstadoApp;
import app.ui.EstadoApp.Carga;
import app.ui.EstadoApp.Mudanca;
import app.ui.Moeda;
import app.ui.telas.PainelCelular.Aba;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.BotaoIcone;
import app.ui.tema.Componentes.Cartao;
import app.ui.tema.Componentes.Chip;
import app.ui.tema.Componentes.Estilo;
import app.ui.tema.Componentes.Hero;
import app.ui.tema.Componentes.Pilha;
import app.ui.tema.Componentes.SeletorConta;
import app.ui.tema.Icones;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Fonte;
import app.ui.tema.Tema.Medida;
import domain.LancamentoExtrato;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Tela Início.
 * <ul>
 *   <li><b>Propósito:</b> leitura rápida da conta selecionada (saldo) e atalhos para as operações.</li>
 *   <li><b>Áreas:</b> hero em cor primária (marca, chip do banco, saudação, saldo com olho, seletor de conta) · 4 ações rápidas · cartão "Últimas movimentações" (até 5).</li>
 *   <li><b>Componentes:</b> hero, chipSobrePrimaria, botaoIcone, seletorConta, acaoCircular, cartao, itemMovimentacao, botao.</li>
 *   <li><b>Dados e thread:</b> saldo vem da ContaResumo em {@link EstadoApp}; movimentações via {@code consultarExtrato} em SwingWorker; saldo e lista recarregam após operações.</li>
 *   <li><b>Estados:</b> sem conta (convite + Nova conta) · carregando · com dados · vazia · erro (a tela segue utilizável, com "Tentar de novo").</li>
 *   <li><b>Erros:</b> falha ao ler contas ou extrato aparece no cartão em texto simples; o chip só diz "Banco conectado/indisponível" após leitura real das contas.</li>
 * </ul>
 */
public final class PainelVisaoGeral extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient ControladorBanco controlador;
    private final transient EstadoApp estado;
    private final transient Runnable recarregarContas;
    private final transient Consumer<Aba> navegar;

    private final JLabel saudacao = Componentes.rotulo("", Fonte.TITULO, Cor.SOBRE_PRIMARIA);
    private final JLabel saldo = Componentes.rotulo("", Fonte.SALDO, Cor.SOBRE_PRIMARIA);
    private final BotaoIcone olho = Componentes.botaoIcone(Icone.OLHO, "Ocultar saldo", true);
    private final SeletorConta seletor;
    private final Chip chipBanco = Componentes.chipSobrePrimaria("Banco conectado", Icone.BANCO_DADOS);
    private final Pilha corpoMovimentos = Componentes.pilha();

    private transient List<LancamentoExtrato> ultimos;
    private boolean carregandoMovimentos;
    private transient Falha falhaMovimentos;
    private Long idExibido;
    private long requisicao;

    public PainelVisaoGeral(ControladorBanco controlador, EstadoApp estado, Runnable recarregarContas,
                            Consumer<Aba> navegar, Consumer<TipoOperacao> abrirOperacao) {
        super(new BorderLayout());
        this.controlador = controlador;
        this.estado = estado;
        this.recarregarContas = recarregarContas;
        this.navegar = navegar;
        setOpaque(false);

        seletor = Componentes.seletorConta(estado::contas, estado::saldoOculto, estado::selecionar);
        olho.addActionListener(e -> estado.alternarSaldoOculto());
        saldo.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ajustarSaldo();
            }
        });

        Pilha conteudo = Componentes.pilha();
        conteudo.adicionar(montarHero()).espaco(Medida.ESP_6);
        conteudo.adicionar(Componentes.comMargemDeTela(montarAcoes(abrirOperacao))).espaco(Medida.ESP_6);
        conteudo.adicionar(Componentes.comMargemDeTela(montarMovimentos())).espaco(Medida.ESP_6);
        add(Componentes.rolagem(conteudo), BorderLayout.CENTER);

        estado.adicionarOuvinte(this::aoMudar);
        atualizarHero();
        recarregarMovimentacoes();
    }

    private Hero montarHero() {
        Hero hero = Componentes.hero();
        Pilha p = Componentes.pilha();

        JLabel marca = Componentes.rotulo("Sistema Banc\u00e1rio", Fonte.SUBTITULO, Cor.SOBRE_PRIMARIA);
        marca.setIcon(Icones.de(Icone.BANCO, Medida.ICONE, Cor.SOBRE_PRIMARIA));
        marca.setIconTextGap(Medida.ESP_2);
        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        direita.setOpaque(false);
        direita.add(chipBanco);
        JPanel linhaMarca = new JPanel(new BorderLayout(Medida.ESP_2, 0));
        linhaMarca.setOpaque(false);
        linhaMarca.add(marca, BorderLayout.WEST);
        linhaMarca.add(direita, BorderLayout.CENTER);

        JPanel linhaSaldo = new JPanel(new BorderLayout(Medida.ESP_2, 0));
        linhaSaldo.setOpaque(false);
        linhaSaldo.add(saldo, BorderLayout.CENTER);
        linhaSaldo.add(olho, BorderLayout.EAST);

        p.adicionar(linhaMarca).espaco(Medida.ESP_5);
        p.adicionar(saudacao).espaco(Medida.ESP_5);
        p.adicionar(Componentes.rotulo("Saldo em conta", Fonte.ROTULO, Cor.SOBRE_PRIMARIA_SUAVE)).espaco(Medida.ESP_1);
        p.adicionar(linhaSaldo).espaco(Medida.ESP_4);
        p.adicionar(seletor);
        hero.add(p, BorderLayout.CENTER);
        return hero;
    }

    private JPanel montarAcoes(Consumer<TipoOperacao> abrirOperacao) {
        JPanel acoes = new JPanel(new GridLayout(1, 4));
        acoes.setOpaque(false);
        var depositar = Componentes.acaoCircular(Icone.DEPOSITO, "Depositar");
        var sacar = Componentes.acaoCircular(Icone.SAQUE, "Sacar");
        var transferir = Componentes.acaoCircular(Icone.TRANSFERENCIA, "Transferir");
        var extrato = Componentes.acaoCircular(Icone.HISTORICO, "Extrato");
        depositar.addActionListener(e -> abrirOperacao.accept(TipoOperacao.DEPOSITO));
        sacar.addActionListener(e -> abrirOperacao.accept(TipoOperacao.SAQUE));
        transferir.addActionListener(e -> abrirOperacao.accept(TipoOperacao.TRANSFERENCIA));
        extrato.addActionListener(e -> navegar.accept(Aba.EXTRATO));
        acoes.add(depositar);
        acoes.add(sacar);
        acoes.add(transferir);
        acoes.add(extrato);
        return acoes;
    }

    private Cartao montarMovimentos() {
        Cartao cartao = Componentes.cartao(new BorderLayout(0, Medida.ESP_2));
        var verTudo = Componentes.botao("Ver tudo", null, Estilo.FANTASMA).compacto();
        verTudo.addActionListener(e -> navegar.accept(Aba.EXTRATO));
        JPanel cabecalho = new JPanel(new BorderLayout());
        cabecalho.setOpaque(false);
        cabecalho.add(Componentes.rotulo("\u00daltimas movimenta\u00e7\u00f5es", Fonte.SECAO, Cor.TEXTO), BorderLayout.WEST);
        cabecalho.add(verTudo, BorderLayout.EAST);
        cartao.add(cabecalho, BorderLayout.NORTH);
        cartao.add(corpoMovimentos, BorderLayout.CENTER);
        return cartao;
    }

    // ------------------------------------------------------------------ reação ao estado

    private void aoMudar(Mudanca mudanca) {
        switch (mudanca) {
            case CONTAS -> {
                atualizarHero();
                if (estado.contaSelecionada() == null) {
                    redesenharMovimentos();
                }
            }
            case SELECAO -> {
                atualizarHero();
                ContaResumo conta = estado.contaSelecionada();
                Long id = conta == null ? null : conta.id();
                if (!java.util.Objects.equals(id, idExibido)) {
                    recarregarMovimentacoes();
                }
            }
            case SALDO_OCULTO -> atualizarHero();
            case OPERACAO -> {
                ContaResumo conta = estado.contaSelecionada();
                if (conta != null && estado.ultimasContasAfetadas().contains(conta.id())) {
                    recarregarMovimentacoes();
                }
            }
        }
    }

    private void atualizarHero() {
        ContaResumo conta = estado.contaSelecionada();
        boolean oculto = estado.saldoOculto();
        saudacao.setText(conta == null ? "Ol\u00e1!" : "Ol\u00e1, " + primeiroNome(conta.titular()));
        saldo.setText(conta == null ? "\u2014" : oculto ? Componentes.SALDO_OCULTO : Moeda.formatar(conta.saldo()));
        olho.setIcone(oculto ? Icone.OLHO_FECHADO : Icone.OLHO, oculto ? "Mostrar saldo" : "Ocultar saldo");
        seletor.setConta(conta);
        switch (estado.conexao()) {
            case DESCONHECIDA -> chipBanco.setVisible(false);
            case CONECTADA -> {
                chipBanco.setTexto("Banco conectado");
                chipBanco.setVisible(true);
            }
            case INDISPONIVEL -> {
                chipBanco.setTexto("Banco indispon\u00edvel");
                chipBanco.setVisible(true);
            }
        }
        ajustarSaldo();
        revalidate();
        repaint();
    }

    private static String primeiroNome(String titular) {
        return titular.strip().split("\\s+")[0];
    }

    /** Encolhe a fonte do saldo (até um mínimo) quando o valor não cabe na largura. */
    private void ajustarSaldo() {
        int disponivel = saldo.getWidth();
        if (disponivel <= 0) {
            return;
        }
        Font fonte = Fonte.SALDO;
        float tamanho = fonte.getSize2D();
        while (tamanho > Fonte.VALOR_GRANDE_MINIMO
                && saldo.getFontMetrics(fonte).stringWidth(saldo.getText()) > disponivel) {
            tamanho -= Fonte.PASSO_ENCOLHER;
            fonte = Fonte.SALDO.deriveFont(tamanho);
        }
        if (!fonte.equals(saldo.getFont())) {
            saldo.setFont(fonte);
        }
    }

    // ------------------------------------------------------------------ movimentações

    private void recarregarMovimentacoes() {
        ContaResumo conta = estado.contaSelecionada();
        Long id = conta == null ? null : conta.id();
        if (!java.util.Objects.equals(id, idExibido)) {
            ultimos = null;
        }
        idExibido = id;
        falhaMovimentos = null;
        long minha = ++requisicao;
        if (conta == null) {
            ultimos = null;
            carregandoMovimentos = false;
            redesenharMovimentos();
            return;
        }
        carregandoMovimentos = true;
        redesenharMovimentos();
        controlador.consultarExtrato(conta.id(), resultado -> {
            if (minha != requisicao) {
                return;
            }
            carregandoMovimentos = false;
            if (resultado.ok()) {
                ultimos = resultado.valor();
                falhaMovimentos = null;
            } else {
                falhaMovimentos = resultado.falha();
            }
            redesenharMovimentos();
        });
    }

    private void redesenharMovimentos() {
        corpoMovimentos.removeAll();
        ContaResumo conta = estado.contaSelecionada();
        if (conta == null) {
            semConta();
        } else if (carregandoMovimentos && ultimos == null) {
            corpoMovimentos.adicionar(Componentes.mensagem("Consultando extrato..."));
        } else if (falhaMovimentos != null && ultimos == null) {
            comTentarDeNovo(falhaMovimentos.oQueAconteceu() + " " + falhaMovimentos.oQueFazer(),
                    this::recarregarMovimentacoes);
        } else if (ultimos != null && ultimos.isEmpty()) {
            corpoMovimentos.adicionar(Componentes.mensagem("Nenhuma movimenta\u00e7\u00e3o nesta conta."));
        } else if (ultimos != null) {
            if (falhaMovimentos != null) {
                corpoMovimentos.adicionar(Componentes.rotulo("N\u00e3o foi poss\u00edvel atualizar as movimenta\u00e7\u00f5es.",
                        Fonte.ROTULO, Cor.PERIGO)).espaco(Medida.ESP_1);
            }
            LocalDate hoje = LocalDate.now();
            int inicio = Math.max(0, ultimos.size() - Medida.LIMITE_ITENS_INICIO);
            for (int i = ultimos.size() - 1; i >= inicio; i--) {
                LancamentoExtrato l = ultimos.get(i);
                String detalhe = ApresentacaoExtrato.rotuloData(hoje, l.transacao().getHorario().toLocalDate())
                        + " \u00b7 " + ApresentacaoExtrato.hora(l.transacao().getHorario());
                corpoMovimentos.adicionar(ApresentacaoExtrato.item(l, estado, detalhe, i > inicio));
            }
        }
        corpoMovimentos.revalidate();
        corpoMovimentos.repaint();
    }

    private void semConta() {
        if (estado.carga() == Carga.CARREGANDO) {
            corpoMovimentos.adicionar(Componentes.mensagem("Carregando contas..."));
        } else if (estado.carga() == Carga.FALHA && estado.falhaContas() != null) {
            Falha falha = estado.falhaContas();
            comTentarDeNovo(falha.oQueAconteceu() + " " + falha.oQueFazer(), recarregarContas);
        } else {
            corpoMovimentos.adicionar(Componentes.mensagem(
                    "Selecione ou crie uma conta para ver seu saldo e movimenta\u00e7\u00f5es."));
            var nova = Componentes.botao("Nova conta", Icone.ADICIONAR, Estilo.PRIMARIO);
            nova.addActionListener(e -> FolhaNovaConta.abrir(this, controlador, estado));
            corpoMovimentos.adicionar(nova);
        }
    }

    private void comTentarDeNovo(String texto, Runnable acao) {
        corpoMovimentos.adicionar(Componentes.mensagem(texto));
        var tentar = Componentes.botao("Tentar de novo", Icone.ATUALIZAR, Estilo.SECUNDARIO);
        tentar.addActionListener(e -> acao.run());
        corpoMovimentos.adicionar(tentar);
    }
}
