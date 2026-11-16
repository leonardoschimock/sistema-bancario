package app.ui;

import app.ContaResumo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Estado compartilhado entre as telas: contas carregadas, conta selecionada e preferência visual de
 * ocultar saldo. Sem regra de negócio e sem acesso ao serviço. Use somente na EDT.
 */
public final class EstadoApp {

    /** O que mudou; as telas decidem o que atualizar. */
    public enum Mudanca { CONTAS, SELECAO, SALDO_OCULTO, OPERACAO }

    public enum Carga { NAO_INICIADA, CARREGANDO, PRONTA, FALHA }

    /** Resultado REAL da última leitura de contas (nunca presumido). */
    public enum Conexao { DESCONHECIDA, CONECTADA, INDISPONIVEL }

    /** Ouvinte de mudanças (chamado na EDT). */
    public interface Ouvinte {
        void aoMudar(Mudanca mudanca);
    }

    private final List<Ouvinte> ouvintes = new ArrayList<>();
    private List<ContaResumo> contas = List.of();
    private ContaResumo selecionada;
    private Carga carga = Carga.NAO_INICIADA;
    private Conexao conexao = Conexao.DESCONHECIDA;
    private ControladorBanco.Falha falhaContas;
    private boolean saldoOculto;
    private List<Long> ultimasContasAfetadas = List.of();

    public void adicionarOuvinte(Ouvinte ouvinte) {
        ouvintes.add(ouvinte);
    }

    public List<ContaResumo> contas() {
        return contas;
    }

    /** A conta selecionada ou {@code null}. */
    public ContaResumo contaSelecionada() {
        return selecionada;
    }

    public Carga carga() {
        return carga;
    }

    public Conexao conexao() {
        return conexao;
    }

    public ControladorBanco.Falha falhaContas() {
        return falhaContas;
    }

    public boolean saldoOculto() {
        return saldoOculto;
    }

    /** Contas envolvidas na última operação concluída (para recarregar saldos e extratos). */
    public List<Long> ultimasContasAfetadas() {
        return ultimasContasAfetadas;
    }

    public void iniciarCargaContas() {
        carga = Carga.CARREGANDO;
        falhaContas = null;
        avisar(Mudanca.CONTAS);
    }

    public void definirContas(List<ContaResumo> novas) {
        contas = List.copyOf(novas);
        carga = Carga.PRONTA;
        conexao = Conexao.CONECTADA;
        falhaContas = null;
        ContaResumo anterior = selecionada;
        selecionada = anterior == null ? null : buscar(anterior.id());
        avisar(Mudanca.CONTAS);
        if (anterior != selecionada) {
            avisar(Mudanca.SELECAO);
        }
    }

    public void definirFalhaContas(ControladorBanco.Falha falha) {
        carga = Carga.FALHA;
        conexao = Conexao.INDISPONIVEL;
        falhaContas = falha;
        avisar(Mudanca.CONTAS);
    }

    /** Inclui uma conta recém-criada (já gravada) e a seleciona. */
    public void adicionarESelecionar(ContaResumo nova) {
        List<ContaResumo> lista = new ArrayList<>(contas);
        lista.removeIf(c -> c.id() == nova.id());
        lista.add(nova);
        contas = List.copyOf(lista);
        carga = Carga.PRONTA;
        conexao = Conexao.CONECTADA;
        selecionada = nova;
        avisar(Mudanca.CONTAS);
        avisar(Mudanca.SELECAO);
    }

    public void selecionar(ContaResumo conta) {
        ContaResumo nova = conta == null ? null : buscar(conta.id());
        if (nova == null && conta != null) {
            nova = conta;
        }
        selecionada = nova;
        avisar(Mudanca.SELECAO);
    }

    /** Copia para a lista e para a seleção o saldo que o serviço acabou de informar. */
    public void atualizarSaldo(long id, BigDecimal saldo) {
        List<ContaResumo> lista = new ArrayList<>(contas.size());
        for (ContaResumo c : contas) {
            lista.add(c.id() == id ? new ContaResumo(c.id(), c.titular(), saldo) : c);
        }
        contas = List.copyOf(lista);
        boolean eraSelecionada = selecionada != null && selecionada.id() == id;
        if (eraSelecionada) {
            selecionada = buscar(id);
        }
        avisar(Mudanca.CONTAS);
        if (eraSelecionada) {
            avisar(Mudanca.SELECAO);
        }
    }

    public void alternarSaldoOculto() {
        saldoOculto = !saldoOculto;
        avisar(Mudanca.SALDO_OCULTO);
    }

    /** Avisa que uma operação foi confirmada pelo serviço nas contas informadas. */
    public void operacaoConcluida(long... ids) {
        List<Long> afetadas = new ArrayList<>();
        for (long id : ids) {
            afetadas.add(id);
        }
        ultimasContasAfetadas = List.copyOf(afetadas);
        avisar(Mudanca.OPERACAO);
    }

    private ContaResumo buscar(long id) {
        for (ContaResumo c : contas) {
            if (c.id() == id) {
                return c;
            }
        }
        return null;
    }

    private void avisar(Mudanca mudanca) {
        for (Ouvinte ouvinte : List.copyOf(ouvintes)) {
            ouvinte.aoMudar(mudanca);
        }
    }
}
