package app.ui.telas;

import app.ContaResumo;
import app.ui.ControladorBanco;
import app.ui.ControladorBanco.Falha;
import app.ui.EstadoApp;
import app.ui.EstadoApp.Carga;
import app.ui.EstadoApp.Mudanca;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.Botao;
import app.ui.tema.Componentes.Cartao;
import app.ui.tema.Componentes.Chip;
import app.ui.tema.Componentes.Estilo;
import app.ui.tema.Componentes.Folha;
import app.ui.tema.Componentes.Pilha;
import app.ui.tema.Componentes.SeletorConta;
import app.ui.tema.Icones;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Fonte;
import app.ui.tema.Tema.Medida;
import domain.LancamentoExtrato;
import domain.LancamentoExtrato.Sentido;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.swing.ButtonGroup;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Tela Extrato.
 * <ul>
 *   <li><b>Propósito:</b> consultar as movimentações de uma conta e exportá-las para um arquivo .txt.</li>
 *   <li><b>Áreas:</b> barra superior ("Extrato" + Exportar) · seletor da conta · chips Todos/Entradas/Saídas · movimentações agrupadas por data (mais recentes primeiro).</li>
 *   <li><b>Componentes:</b> barraSuperior, botao, seletorConta, chipFiltro, cartao, itemMovimentacao, folha, JFileChooser (só para escolher o destino).</li>
 *   <li><b>Dados e thread:</b> {@code consultarExtrato} e {@code exportarExtrato} do controlador em SwingWorker; recarrega ao trocar de conta e após operações; o filtro é local, sobre os dados já lidos.</li>
 *   <li><b>Estados:</b> sem conta · consultando · lista · vazia · erro (mantém a lista anterior quando existe) · exportando (botão desabilitado).</li>
 *   <li><b>Erros:</b> falha de leitura com "Tentar de novo"; falha de exportação em folha com o que fazer; arquivo já existente exige confirmação antes de substituir.</li>
 * </ul>
 */
public final class PainelExtrato extends JPanel {
    private static final long serialVersionUID = 1L;

    private enum Filtro { TODOS, ENTRADAS, SAIDAS }

    private final transient ControladorBanco controlador;
    private final transient EstadoApp estado;
    private final transient Runnable recarregarContas;
    private final SeletorConta seletor;
    private final Botao exportar = Componentes.botao("Exportar", Icone.EXPORTAR, Estilo.SECUNDARIO).compacto();
    private final Pilha lista = Componentes.pilha();

    private transient List<LancamentoExtrato> dados;
    private transient Falha falha;
    private Filtro filtro = Filtro.TODOS;
    private boolean carregando;
    private boolean exportando;
    private Long idExibido;
    private long requisicao;

    public PainelExtrato(ControladorBanco controlador, EstadoApp estado, Runnable recarregarContas) {
        super(new BorderLayout());
        this.controlador = controlador;
        this.estado = estado;
        this.recarregarContas = recarregarContas;
        setOpaque(false);

        seletor = Componentes.seletorConta(estado::contas, estado::saldoOculto, estado::selecionar);
        exportar.addActionListener(e -> exportar());
        add(Componentes.barraSuperior("Extrato", exportar), BorderLayout.NORTH);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, Medida.ESP_2, 0));
        chips.setOpaque(false);
        ButtonGroup grupo = new ButtonGroup();
        String[] nomes = {"Todos", "Entradas", "Sa\u00eddas"};
        for (Filtro f : Filtro.values()) {
            Chip chip = Componentes.chipFiltro(nomes[f.ordinal()]);
            chip.setSelected(f == Filtro.TODOS);
            chip.addActionListener(e -> {
                filtro = f;
                redesenhar();
            });
            grupo.add(chip);
            chips.add(chip);
        }

        Pilha conteudo = Componentes.pilha();
        conteudo.adicionar(Componentes.comMargemDeTela(seletor)).espaco(Medida.ESP_3);
        conteudo.adicionar(Componentes.comMargemDeTela(chips)).espaco(Medida.ESP_4);
        conteudo.adicionar(Componentes.comMargemDeTela(lista)).espaco(Medida.ESP_6);
        add(Componentes.rolagem(conteudo), BorderLayout.CENTER);

        estado.adicionarOuvinte(this::aoMudar);
        seletor.setConta(estado.contaSelecionada());
        recarregar();
    }

    private void aoMudar(Mudanca mudanca) {
        ContaResumo conta = estado.contaSelecionada();
        switch (mudanca) {
            case CONTAS -> {
                seletor.setConta(conta);
                atualizarBotao();
                if (conta == null) {
                    redesenhar();
                }
            }
            case SELECAO -> {
                seletor.setConta(conta);
                if (!Objects.equals(conta == null ? null : conta.id(), idExibido)) {
                    recarregar();
                }
            }
            case OPERACAO -> {
                if (conta != null && estado.ultimasContasAfetadas().contains(conta.id())) {
                    recarregar();
                }
            }
            case SALDO_OCULTO -> { }
        }
    }

    // ------------------------------------------------------------------ leitura

    private void recarregar() {
        ContaResumo conta = estado.contaSelecionada();
        Long id = conta == null ? null : conta.id();
        if (!Objects.equals(id, idExibido)) {
            dados = null;
        }
        idExibido = id;
        falha = null;
        long minha = ++requisicao;
        atualizarBotao();
        if (conta == null) {
            dados = null;
            carregando = false;
            redesenhar();
            return;
        }
        carregando = true;
        redesenhar();
        controlador.consultarExtrato(conta.id(), resultado -> {
            if (minha != requisicao) {
                return;
            }
            carregando = false;
            if (resultado.ok()) {
                dados = resultado.valor();
                falha = null;
            } else {
                falha = resultado.falha();
            }
            redesenhar();
        });
    }

    private void atualizarBotao() {
        exportar.setEnabled(estado.contaSelecionada() != null && !exportando);
    }

    private void redesenhar() {
        lista.removeAll();
        ContaResumo conta = estado.contaSelecionada();
        if (conta == null) {
            if (estado.carga() == Carga.CARREGANDO) {
                lista.adicionar(Componentes.mensagem("Carregando contas..."));
            } else if (estado.carga() == Carga.FALHA && estado.falhaContas() != null) {
                comTentarDeNovo(estado.falhaContas().oQueAconteceu() + " " + estado.falhaContas().oQueFazer(),
                        recarregarContas);
            } else {
                lista.adicionar(Componentes.mensagem("Selecione uma conta para consultar o extrato."));
            }
        } else if (carregando && dados == null) {
            lista.adicionar(Componentes.mensagem("Consultando extrato..."));
        } else if (falha != null && dados == null) {
            comTentarDeNovo(falha.oQueAconteceu() + " " + falha.oQueFazer(), this::recarregar);
        } else if (dados != null) {
            if (falha != null) {
                lista.adicionar(Componentes.textoQuebrado("N\u00e3o foi poss\u00edvel atualizar o extrato. Mostrando os dados anteriores.",
                        Fonte.ROTULO, Cor.PERIGO)).espaco(Medida.ESP_2);
            }
            listarGrupos();
        }
        lista.revalidate();
        lista.repaint();
    }

    private void listarGrupos() {
        if (dados.isEmpty()) {
            lista.adicionar(Componentes.mensagem("Nenhuma movimenta\u00e7\u00e3o nesta conta."));
            return;
        }
        LocalDate hoje = LocalDate.now();
        LocalDate grupoAtual = null;
        Pilha itens = null;
        int mostrados = 0;
        for (int i = dados.size() - 1; i >= 0; i--) {
            LancamentoExtrato l = dados.get(i);
            boolean entrada = l.sentido() == Sentido.ENTRADA;
            if ((filtro == Filtro.ENTRADAS && !entrada) || (filtro == Filtro.SAIDAS && entrada)) {
                continue;
            }
            if (mostrados == Medida.LIMITE_ITENS_EXTRATO) {
                lista.adicionar(Componentes.textoQuebrado("Mostrando as " + Medida.LIMITE_ITENS_EXTRATO
                                + " movimenta\u00e7\u00f5es mais recentes. O arquivo exportado traz o extrato completo.",
                        Fonte.ROTULO, Cor.TEXTO_SECUNDARIO));
                return;
            }
            LocalDate data = l.transacao().getHorario().toLocalDate();
            if (!data.equals(grupoAtual)) {
                grupoAtual = data;
                lista.adicionar(Componentes.rotulo(ApresentacaoExtrato.rotuloData(hoje, data), Fonte.SUBTITULO,
                        Cor.TEXTO_SECUNDARIO)).espaco(Medida.ESP_2);
                Cartao cartao = Componentes.cartao(new BorderLayout());
                itens = Componentes.pilha();
                cartao.add(itens, BorderLayout.CENTER);
                lista.adicionar(cartao).espaco(Medida.ESP_4);
            }
            // divisor entre itens do mesmo grupo: o item anterior ganha a linha ao chegar o próximo
            if (itens.getComponentCount() > 0) {
                ((Componentes.ItemMovimentacao) itens.getComponent(itens.getComponentCount() - 1)).comDivisor(true);
            }
            itens.adicionar(ApresentacaoExtrato.item(l, estado, ApresentacaoExtrato.hora(l.transacao().getHorario()), false));
            mostrados++;
        }
        if (mostrados == 0) {
            lista.adicionar(Componentes.mensagem("Nenhuma movimenta\u00e7\u00e3o neste filtro."));
        }
    }

    private void comTentarDeNovo(String texto, Runnable acao) {
        lista.adicionar(Componentes.mensagem(texto));
        Botao tentar = Componentes.botao("Tentar de novo", Icone.ATUALIZAR, Estilo.SECUNDARIO);
        tentar.addActionListener(e -> acao.run());
        lista.adicionar(tentar);
    }

    // ------------------------------------------------------------------ exportação

    private void exportar() {
        ContaResumo conta = estado.contaSelecionada();
        if (conta == null || exportando) {
            return;
        }
        JFileChooser escolha = new JFileChooser();
        escolha.setDialogTitle("Exportar extrato");
        escolha.setFileFilter(new FileNameExtensionFilter("Arquivo de texto (*.txt)", "txt"));
        escolha.setSelectedFile(new File("extrato_conta_" + conta.id() + ".txt"));
        if (escolha.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destino = escolha.getSelectedFile().toPath();
        if (!destino.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
            destino = destino.resolveSibling(destino.getFileName() + ".txt");
        }
        if (Files.exists(destino) && !confirmarSubstituicao(destino)) {
            return;
        }
        exportando = true;
        atualizarBotao();
        Path alvo = destino;
        controlador.exportarExtrato(conta.id(), alvo, resultado -> {
            try {
                exportando = false;
                atualizarBotao();
                if (resultado.ok()) {
                    avisarExportado(resultado.valor());
                } else {
                    Falha f = resultado.falha();
                    Componentes.mostrarErro(this, f.recusa() ? Icone.ALERTA : Icone.ERRO, f.titulo(),
                            f.oQueAconteceu(), f.oQueFazer(), f.detalheTecnico());
                }
            } finally {
                exportando = false;
                atualizarBotao();
            }
        });
    }

    private boolean confirmarSubstituicao(Path destino) {
        Pilha corpo = Componentes.pilha();
        JLabel titulo = Componentes.rotulo("Substituir arquivo?", Fonte.SECAO, Cor.TEXTO);
        titulo.setIcon(Icones.de(Icone.ALERTA, Medida.ICONE_GRANDE, Cor.ATENCAO));
        titulo.setIconTextGap(Medida.ESP_3);
        corpo.adicionar(titulo).espaco(Medida.ESP_3);
        corpo.adicionar(Componentes.textoQuebrado("O arquivo " + destino.getFileName()
                + " j\u00e1 existe e ser\u00e1 substitu\u00eddo pelo extrato atual.", Fonte.CORPO, Cor.TEXTO)).espaco(Medida.ESP_5);
        Botao cancelar = Componentes.botao("Cancelar", null, Estilo.SECUNDARIO);
        Botao substituir = Componentes.botao("Substituir", null, Estilo.PRIMARIO);
        JPanel botoes = new JPanel(new GridLayout(1, 2, Medida.ESP_3, 0));
        botoes.setOpaque(false);
        botoes.add(cancelar);
        botoes.add(substituir);
        corpo.adicionar(botoes);
        Folha folha = Componentes.folha(this, corpo);
        boolean[] confirmado = {false};
        cancelar.addActionListener(e -> folha.fechar());
        substituir.addActionListener(e -> {
            confirmado[0] = true;
            folha.fechar();
        });
        folha.mostrar();
        return confirmado[0];
    }

    private void avisarExportado(Path arquivo) {
        Pilha corpo = Componentes.pilha();
        JLabel titulo = Componentes.rotulo("Extrato exportado", Fonte.SECAO, Cor.TEXTO);
        titulo.setIcon(Icones.de(Icone.OK, Medida.ICONE_GRANDE, Cor.SUCESSO));
        titulo.setIconTextGap(Medida.ESP_3);
        corpo.adicionar(titulo).espaco(Medida.ESP_3);
        corpo.adicionar(Componentes.textoQuebrado("Extrato exportado com sucesso.", Fonte.CORPO, Cor.TEXTO_SECUNDARIO))
                .espaco(Medida.ESP_3);
        corpo.adicionar(Componentes.rotulo("Arquivo gravado em", Fonte.ROTULO, Cor.TEXTO_SECUNDARIO)).espaco(Medida.ESP_1);
        corpo.adicionar(Componentes.textoQuebrado(arquivo.toAbsolutePath().toString(), Fonte.DADO, Cor.TEXTO))
                .espaco(Medida.ESP_5);
        Botao fechar = Componentes.botao("Fechar", null, Estilo.PRIMARIO);
        corpo.adicionar(fechar);
        Folha folha = Componentes.folha(this, corpo);
        fechar.addActionListener(e -> folha.fechar());
        folha.mostrar();
    }
}
