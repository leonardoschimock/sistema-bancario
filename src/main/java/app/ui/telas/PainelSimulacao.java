package app.ui.telas;

import app.ExecucaoSimulacao;
import app.ParametrosSimulacao;
import app.ui.ControladorBanco;
import app.ui.ControladorBanco.Falha;
import app.ui.ControladorBanco.Resultado;
import app.ui.ControladorBanco.Tipo;
import app.ui.Moeda;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.Badge;
import app.ui.tema.Componentes.BarraProgresso;
import app.ui.tema.Componentes.Botao;
import app.ui.tema.Componentes.CaixaSelecao;
import app.ui.tema.Componentes.CampoTexto;
import app.ui.tema.Componentes.Cartao;
import app.ui.tema.Componentes.Estilo;
import app.ui.tema.Componentes.EstadoBadge;
import app.ui.tema.Componentes.Pilha;
import app.ui.tema.Icones;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Fonte;
import app.ui.tema.Tema.Medida;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import simulacao.ResultadoSimulacao;
import simulacao.ResultadoSimulacao.ComparacaoSaldo;
import simulacao.ResultadoSimulacao.FalhaOperador;
import simulacao.ResultadoSimulacao.ResumoOperador;

/**
 * Tela Simulação.
 * <ul>
 *   <li><b>Propósito:</b> rodar várias threads sobre contas de teste e conferir se cada saldo final bate com o esperado.</li>
 *   <li><b>Áreas:</b> aviso do que a simulação faz · parâmetros (threads, operações por thread, contas, seed, prazo, gravar no banco) · Iniciar/Encerrar · status · resultado (métricas, saldos por conta, detalhes técnicos).</li>
 *   <li><b>Componentes:</b> cartao, campoTexto, caixaSelecao, botao, badge, progressoIndeterminado (indeterminada: não finge percentual).</li>
 *   <li><b>Dados e thread:</b> {@code executarSimulacao} em SwingWorker (a EDT segue livre: a barra anima); sem progresso parcial; "Encerrar" chama {@code cancel(true)}, que interrompe e cancela as threads da simulação.</li>
 *   <li><b>Estados:</b> pronta · em execução (campos travados, Iniciar desabilitado) · concluída consistente · concluída com divergência ou recusas · não terminou no prazo · interrompida · falhou.</li>
 *   <li><b>Erros:</b> número vazio marca o campo; parâmetro recusado pelo serviço e falhas aparecem no resultado com o que fazer e detalhe técnico recolhido.</li>
 * </ul>
 */
public final class PainelSimulacao extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final String SEM_PROGRESSO =
            "A simula\u00e7\u00e3o roda tudo de uma vez e s\u00f3 informa o resultado ao final; n\u00e3o h\u00e1 percentual parcial.";

    private final transient ControladorBanco controlador;
    private final transient Runnable recarregarContas;

    private final CampoTexto campoThreads = Componentes.campoTexto("Threads", "Ex.: 8")
            .somenteDigitos(Medida.DIGITOS_THREADS);
    private final CampoTexto campoOperacoes = Componentes.campoTexto("Opera\u00e7\u00f5es por thread", "Ex.: 20000")
            .somenteDigitos(Medida.DIGITOS_OPERACOES);
    private final CampoTexto campoContas = Componentes.campoTexto("Contas", "Ex.: 4")
            .somenteDigitos(Medida.DIGITOS_CONTAS);
    private final CampoTexto campoSeed = Componentes.campoTexto("Seed", "Ex.: 42")
            .somenteDigitos(Medida.DIGITOS_SEED);
    private final CampoTexto campoPrazo = Componentes.campoTexto("Prazo (s)", "Ex.: 120")
            .somenteDigitos(Medida.DIGITOS_PRAZO);
    private final CaixaSelecao gravar = Componentes.caixaSelecao(
            "Gravar as contas no banco ao final (s\u00f3 se o resultado for consistente)");
    private final Botao iniciar = Componentes.botao("Iniciar simula\u00e7\u00e3o", Icone.CONCORRENCIA, Estilo.PRIMARIO);
    private final Botao encerrar = Componentes.botao("Encerrar", null, Estilo.SECUNDARIO);
    private final Badge badge = Componentes.badge(EstadoBadge.INFO, "Em execu\u00e7\u00e3o");
    private final BarraProgresso progresso = Componentes.progressoIndeterminado();
    private final Pilha status = Componentes.pilha();
    private final Pilha resultado = Componentes.pilha();

    private transient SwingWorker<?, ?> execucao;
    private boolean emExecucao;
    private final List<String> log = new ArrayList<>();

    public PainelSimulacao(ControladorBanco controlador, Runnable recarregarContas) {
        super(new BorderLayout());
        this.controlador = controlador;
        this.recarregarContas = recarregarContas;
        setOpaque(false);
        add(Componentes.barraSuperior("Simula\u00e7\u00e3o"), BorderLayout.NORTH);

        ParametrosSimulacao padrao = ParametrosSimulacao.padrao();
        campoThreads.setTexto(Integer.toString(padrao.threads()));
        campoOperacoes.setTexto(Integer.toString(padrao.operacoesPorThread()));
        campoContas.setTexto(Integer.toString(padrao.numeroDeContas()));
        campoSeed.setTexto(Long.toString(padrao.seed()));
        campoPrazo.setTexto(Long.toString(padrao.prazo().toSeconds()));
        gravar.setSelected(padrao.persistirAoFinal());

        Pilha p = Componentes.pilha();
        p.adicionar(montarAviso()).espaco(Medida.ESP_5);
        p.adicionar(Componentes.rotulo("Par\u00e2metros", Fonte.SECAO, Cor.TEXTO)).espaco(Medida.ESP_3);
        p.adicionar(montarCampos()).espaco(Medida.ESP_3);
        p.adicionar(gravar).espaco(Medida.ESP_4);
        p.adicionar(iniciar).espaco(Medida.ESP_2);
        p.adicionar(encerrar).espaco(Medida.ESP_4);
        p.adicionar(status).espaco(Medida.ESP_3);
        p.adicionar(resultado).espaco(Medida.ESP_6);
        encerrar.setVisible(false);
        status.setVisible(false);

        iniciar.addActionListener(e -> iniciar());
        encerrar.addActionListener(e -> encerrar());

        Pilha conteudo = Componentes.pilha();
        conteudo.adicionar(Componentes.comMargemDeTela(p));
        add(Componentes.rolagem(conteudo), BorderLayout.CENTER);
    }

    private Cartao montarAviso() {
        Cartao cartao = Componentes.cartao(new BorderLayout(Medida.ESP_3, 0));
        JLabel icone = new JLabel(Icones.de(Icone.INFO, Medida.ICONE, Cor.INFO));
        icone.setVerticalAlignment(JLabel.TOP);
        cartao.add(icone, BorderLayout.WEST);
        cartao.add(Componentes.textoQuebrado(
                "A simula\u00e7\u00e3o cria contas pr\u00f3prias (Titular 1, 2, ...), independentes das suas, e roda v\u00e1rias threads "
                        + "sobre elas ao mesmo tempo. As opera\u00e7\u00f5es individuais n\u00e3o entram no extrato. Se voc\u00ea marcar "
                        + "a grava\u00e7\u00e3o, s\u00f3 as contas (com o saldo final) v\u00e3o para o banco, e apenas se o resultado for consistente.",
                Fonte.CORPO, Cor.TEXTO), BorderLayout.CENTER);
        return cartao;
    }

    private JPanel montarCampos() {
        JPanel grade = new JPanel(new GridLayout(0, 2, Medida.ESP_3, Medida.ESP_3));
        grade.setOpaque(false);
        grade.add(campoThreads);
        grade.add(campoOperacoes);
        grade.add(campoContas);
        grade.add(campoSeed);
        grade.add(campoPrazo);
        JPanel vazio = new JPanel();
        vazio.setOpaque(false);
        grade.add(vazio);
        return grade;
    }

    // ------------------------------------------------------------------ execução

    private void iniciar() {
        if (emExecucao) {
            return;
        }
        limparErros();
        Long threads = lerNumero(campoThreads);
        Long operacoes = lerNumero(campoOperacoes);
        Long contas = lerNumero(campoContas);
        Long seed = lerNumero(campoSeed);
        Long prazo = lerNumero(campoPrazo);
        if (threads == null || operacoes == null || contas == null || seed == null || prazo == null) {
            return;
        }
        ParametrosSimulacao parametros = new ParametrosSimulacao(seed, threads.intValue(), operacoes.intValue(),
                contas.intValue(), Duration.ofSeconds(prazo), gravar.isSelected());
        log.clear();
        registrar("INFO", "Iniciada: seed=" + seed + ", threads=" + threads + ", opera\u00e7\u00f5es/thread=" + operacoes
                + ", contas=" + contas + ", prazo=" + prazo + "s, gravar=" + (gravar.isSelected() ? "sim" : "n\u00e3o"));
        resultado.removeAll();
        entrarEmExecucao(true);
        execucao = controlador.executarSimulacao(parametros, r -> concluir(parametros, r));
    }

    private void encerrar() {
        if (execucao != null && emExecucao) {
            encerrar.setEnabled(false);
            badge.definir(EstadoBadge.ATENCAO, "Encerrando...");
            registrar("AVISO", "Encerramento solicitado pelo usu\u00e1rio");
            execucao.cancel(true);
        }
    }

    private Long lerNumero(CampoTexto campo) {
        String texto = campo.texto().strip();
        if (texto.isEmpty()) {
            campo.mostrarErro("Informe um n\u00famero.");
            return null;
        }
        return Long.parseLong(texto);   // o campo só aceita dígitos e tem limite de tamanho
    }

    private void limparErros() {
        for (CampoTexto c : new CampoTexto[] {campoThreads, campoOperacoes, campoContas, campoSeed, campoPrazo}) {
            c.limparErro();
        }
    }

    private void entrarEmExecucao(boolean executando) {
        emExecucao = executando;
        boolean livre = !executando;
        for (CampoTexto c : new CampoTexto[] {campoThreads, campoOperacoes, campoContas, campoSeed, campoPrazo}) {
            c.setEnabled(livre);
        }
        gravar.setEnabled(livre);
        iniciar.setEnabled(livre);
        encerrar.setVisible(executando);
        encerrar.setEnabled(executando);
        status.removeAll();
        status.setVisible(true);
        if (executando) {
            badge.definir(EstadoBadge.INFO, "Em execu\u00e7\u00e3o");
            status.adicionar(badge).espaco(Medida.ESP_2);
            status.adicionar(progresso).espaco(Medida.ESP_2);
            status.adicionar(Componentes.textoQuebrado(SEM_PROGRESSO, Fonte.ROTULO, Cor.TEXTO_SECUNDARIO));
            progresso.iniciar();
        } else {
            progresso.parar();
        }
        revalidate();
        repaint();
    }

    /** Linha do log técnico: horário, nível (INFO/AVISO/ERRO), origem e mensagem. */
    private void registrar(String nivel, String mensagem) {
        log.add(String.format("%s  %-5s  simulacao  %s", LocalTime.now().format(HORA), nivel, mensagem));
    }

    // ------------------------------------------------------------------ resultado

    private void concluir(ParametrosSimulacao parametros, Resultado<ExecucaoSimulacao> r) {
        try {
            entrarEmExecucao(false);
            status.adicionar(badge);
            resultado.removeAll();
            if (r.ok()) {
                mostrarExecucao(parametros, r.valor());
            } else {
                mostrarFalha(parametros, r.falha());
            }
        } finally {
            if (emExecucao) {
                entrarEmExecucao(false);
            }
            revalidate();
            repaint();
        }
    }

    private void mostrarExecucao(ParametrosSimulacao parametros, ExecucaoSimulacao execucaoConcluida) {
        ResultadoSimulacao res = execucaoConcluida.resultado();
        boolean consistente = res.consistente();
        registrar(consistente ? "INFO" : "AVISO", "Conclu\u00edda em " + duracao(res.duracao()) + ": terminou=" + (res.terminou() ? "sim" : "n\u00e3o")
                + ", aceitas=" + res.totalAceitas() + ", recusadas=" + res.totalRecusadas());
        if (!res.terminou()) {
            badge.definir(EstadoBadge.PERIGO, "N\u00e3o terminou no prazo");
        } else if (consistente) {
            badge.definir(EstadoBadge.SUCESSO, "Saldos conferem");
        } else {
            badge.definir(EstadoBadge.PERIGO, "Diverg\u00eancia a investigar");
        }
        if (consistente) {
            resultado.adicionar(Componentes.textoQuebrado(
                    "A simula\u00e7\u00e3o terminou. O saldo final corresponde ao valor esperado.",
                    Fonte.CORPO, Cor.TEXTO)).espaco(Medida.ESP_4);
        } else {
            for (String problema : res.problemas()) {
                resultado.adicionar(Componentes.textoQuebrado("\u2022 " + problema, Fonte.CORPO, Cor.TEXTO))
                        .espaco(Medida.ESP_2);
            }
            resultado.espaco(Medida.ESP_2);
        }
        if (res.terminou()) {
            resultado.adicionar(montarMetricas(res)).espaco(Medida.ESP_5);
            resultado.adicionar(Componentes.rotulo("Saldos por conta", Fonte.SECAO, Cor.TEXTO)).espaco(Medida.ESP_3);
            for (ComparacaoSaldo saldo : res.saldos()) {
                resultado.adicionar(montarSaldo(saldo)).espaco(Medida.ESP_2);
            }
            resultado.espaco(Medida.ESP_3);
        }
        if (!execucaoConcluida.contasGravadas().isEmpty()) {
            resultado.adicionar(Componentes.textoQuebrado(execucaoConcluida.contasGravadas().size()
                            + " conta(s) gravada(s) no banco, com o saldo final da simula\u00e7\u00e3o.", Fonte.CORPO, Cor.TEXTO))
                    .espaco(Medida.ESP_2);
            Botao atualizar = Componentes.botao("Atualizar contas", Icone.ATUALIZAR, Estilo.SECUNDARIO);
            atualizar.addActionListener(e -> recarregarContas.run());
            resultado.adicionar(atualizar).espaco(Medida.ESP_4);
        } else if (parametros.persistirAoFinal()) {
            resultado.adicionar(Componentes.textoQuebrado(
                    "Nenhuma conta foi gravada: a grava\u00e7\u00e3o s\u00f3 ocorre quando o resultado \u00e9 consistente.",
                    Fonte.CORPO, Cor.TEXTO_SECUNDARIO)).espaco(Medida.ESP_4);
        }
        resultado.adicionar(blocoDetalhes(detalhesTecnicos(res)));
    }

    private void mostrarFalha(ParametrosSimulacao parametros, Falha falha) {
        boolean interrompida = falha.tipo() == Tipo.CANCELADA;
        registrar(interrompida ? "AVISO" : "ERRO", interrompida ? "Interrompida" : "Falhou: " + falha.detalheTecnico());
        badge.definir(interrompida ? EstadoBadge.ATENCAO : EstadoBadge.PERIGO, interrompida ? "Interrompida" : "Falhou");
        String texto = falha.oQueAconteceu() + " " + falha.oQueFazer();
        if (interrompida && parametros.persistirAoFinal()) {
            texto += " Se a grava\u00e7\u00e3o estava marcada, confira a lista de Contas.";
        }
        resultado.adicionar(Componentes.textoQuebrado(texto, Fonte.CORPO, Cor.TEXTO)).espaco(Medida.ESP_4);
        resultado.adicionar(blocoDetalhes(String.join("\n", log) + "\n\n" + falha.detalheTecnico()));
    }

    private Cartao montarMetrica(String rotulo, String valor) {
        Cartao cartao = Componentes.cartao(new BorderLayout());
        Pilha p = Componentes.pilha();
        p.adicionar(Componentes.rotulo(valor, Fonte.SECAO, Cor.TEXTO));
        p.adicionar(Componentes.rotulo(rotulo, Fonte.ROTULO, Cor.TEXTO_SECUNDARIO));
        cartao.add(p, BorderLayout.CENTER);
        return cartao;
    }

    private JPanel montarMetricas(ResultadoSimulacao res) {
        JPanel grade = new JPanel(new GridLayout(2, 2, Medida.ESP_3, Medida.ESP_3));
        grade.setOpaque(false);
        grade.add(montarMetrica("Planejadas", numero(res.totalPlanejadas())));
        grade.add(montarMetrica("Aceitas", numero(res.totalAceitas())));
        grade.add(montarMetrica("Recusadas", numero(res.totalRecusadas())));
        grade.add(montarMetrica("Dura\u00e7\u00e3o", duracao(res.duracao())));
        return grade;
    }

    private Cartao montarSaldo(ComparacaoSaldo saldo) {
        Cartao cartao = Componentes.cartao(new BorderLayout(Medida.ESP_3, 0));
        Pilha dados = Componentes.pilha();
        dados.adicionar(Componentes.rotulo(saldo.titular(), Fonte.SUBTITULO, Cor.TEXTO)).espaco(Medida.ESP_1);
        dados.adicionar(Componentes.rotulo("Esperado  " + Moeda.formatar(saldo.esperado()), Fonte.ROTULO,
                Cor.TEXTO_SECUNDARIO));
        dados.adicionar(Componentes.rotulo("Obtido  " + Moeda.formatar(saldo.obtido()), Fonte.ROTULO,
                Cor.TEXTO_SECUNDARIO));
        if (!saldo.confere()) {
            dados.adicionar(Componentes.rotulo("Diferen\u00e7a  " + Moeda.formatar(saldo.diferenca()), Fonte.ROTULO,
                    Cor.PERIGO));
        }
        JPanel direita = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        direita.setOpaque(false);
        direita.add(saldo.confere() ? Componentes.badge(EstadoBadge.SUCESSO, "Confere")
                : Componentes.badge(EstadoBadge.PERIGO, "Diverge"));
        cartao.add(dados, BorderLayout.CENTER);
        cartao.add(direita, BorderLayout.EAST);
        return cartao;
    }

    /** Botão "Detalhes técnicos" que recolhe/expande um texto em fonte mono. */
    private Pilha blocoDetalhes(String texto) {
        Pilha bloco = Componentes.pilha();
        Botao alternar = Componentes.botao("Detalhes t\u00e9cnicos", Icone.CHEVRON_BAIXO, Estilo.FANTASMA);
        Cartao caixa = Componentes.cartao(new BorderLayout());
        caixa.add(Componentes.textoQuebrado(texto, Fonte.DADO, Cor.TEXTO_SECUNDARIO), BorderLayout.CENTER);
        caixa.setVisible(false);
        alternar.addActionListener(e -> {
            caixa.setVisible(!caixa.isVisible());
            bloco.revalidate();
        });
        bloco.adicionar(alternar).espaco(Medida.ESP_2);
        bloco.adicionar(caixa);
        return bloco;
    }

    private String detalhesTecnicos(ResultadoSimulacao res) {
        StringBuilder sb = new StringBuilder(String.join("\n", log)).append("\n");
        if (res.diagnostico() != null && !res.diagnostico().isBlank()) {
            sb.append("\nDiagn\u00f3stico: ").append(res.diagnostico()).append("\n");
        }
        sb.append("\nOperadores:\n");
        for (ResumoOperador o : res.operadores()) {
            sb.append(o.nome()).append("  planejadas=").append(o.planejadas()).append("  aceitas=").append(o.aceitas())
                    .append("  recusadas=").append(o.recusadas()).append("  interrompida=")
                    .append(o.interrompida() ? "sim" : "n\u00e3o").append("\n");
        }
        for (FalhaOperador f : res.falhas()) {
            sb.append("FALHA ").append(f.operador()).append(": ").append(f.causa()).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    private static String numero(int n) {
        return String.format(PT_BR, "%,d", n);
    }

    private static String duracao(Duration d) {
        long ms = d.toMillis();
        return ms < 1000 ? ms + " ms" : String.format(PT_BR, "%.2f s", ms / 1000.0);
    }
}
