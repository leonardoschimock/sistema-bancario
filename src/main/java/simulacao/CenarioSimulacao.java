package simulacao;

import domain.Cliente;
import domain.Conta;
import domain.TipoTransacao;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tudo o que uma simulação precisa ANTES de qualquer thread existir: as contas compartilhadas, o
 * plano de operações de cada thread e os saldos que as contas DEVEM ter no final.
 *
 * <h2>Como o saldo esperado é calculado</h2>
 * Por aritmética pura com {@link BigDecimal}, percorrendo o plano: depósito soma na conta, saque
 * subtrai, transferência subtrai da origem e soma no destino. O cálculo NÃO chama {@link Conta}
 * e é feito dentro de {@link #de}, antes de a simulação começar; assim ele é uma referência
 * independente do código que está sendo testado.
 *
 * <h2>Por que o esperado vale em qualquer intercalação de threads</h2>
 * A soma só é o resultado final se NENHUMA operação for recusada por saldo insuficiente (uma
 * recusa depende da ordem em que as threads chegam, e então o resultado deixaria de ser
 * previsível). {@link #gerar} elimina essa possibilidade por construção: o saldo inicial de
 * cada conta é a soma de TODOS os débitos (saques e transferências enviadas) que o plano faz
 * nela, mais uma reserva. Mesmo no pior caso, com todos os débitos acontecendo antes de
 * qualquer crédito, o saldo nunca fica menor que o valor pedido.
 * Se, mesmo assim, uma operação for recusada, o resultado da simulação a reporta e a
 * simulação é considerada inconsistente.
 *
 * <h2>Reprodutibilidade</h2>
 * {@link #gerar} usa {@link Random} com a {@code seed} informada, cujo algoritmo é especificado
 * pelo Java: a mesma seed e os mesmos parâmetros produzem o mesmo plano e os mesmos saldos
 * esperados em qualquer execução e em qualquer máquina. O que NÃO é reproduzível é a
 * intercalação das threads (decidida pelo sistema operacional); o resultado final, porém, não
 * pode depender dela se a sincronização de {@link Conta} estiver correta, e é isso que a
 * simulação verifica.
 *
 * <h2>Uso único</h2>
 * As contas são mutáveis e a simulação as altera. Rodar o mesmo cenário duas vezes aplicaria o
 * plano em cima de saldos já alterados; por isso {@link #consumir()} recusa o segundo uso. Para
 * repetir, gere outro cenário com a mesma seed.
 */
public final class CenarioSimulacao {

    /** Reserva somada ao saldo inicial de cada conta, além do total de débitos do plano. */
    private static final BigDecimal RESERVA = new BigDecimal("1000.00");

    private final List<Conta> contas;
    private final List<BigDecimal> saldosIniciais;
    private final List<BigDecimal> saldosEsperados;
    private final List<List<OperacaoPlanejada>> planos;
    private final Map<TipoTransacao, Integer> contagemPorTipo;
    private final AtomicBoolean consumido = new AtomicBoolean();

    private CenarioSimulacao(List<Conta> contas, List<List<OperacaoPlanejada>> planos) {
        this.contas = List.copyOf(contas);
        this.planos = copiarPlanos(planos);

        List<BigDecimal> iniciais = new ArrayList<>();
        for (Conta conta : this.contas) {
            iniciais.add(conta.getSaldo()); // fotografia antes de qualquer thread
        }
        this.saldosIniciais = List.copyOf(iniciais);
        this.saldosEsperados = List.copyOf(calcularEsperado(this.contas, this.saldosIniciais, this.planos));
        this.contagemPorTipo = contar(this.planos);
    }

    /**
     * Monta um cenário com contas e planos já prontos. O saldo esperado é calculado AQUI, a
     * partir dos saldos atuais das contas e do plano.
     *
     * <p>Quem chama é responsável por garantir que nenhuma operação será recusada; caso
     * contrário o esperado não vale e o resultado da simulação apontará o problema.
     * Todas as contas usadas pelo plano devem estar em {@code contas}.
     */
    public static CenarioSimulacao de(List<Conta> contas, List<List<OperacaoPlanejada>> planos) {
        if (contas == null || contas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma conta.");
        }
        if (planos == null || planos.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um plano de thread.");
        }
        return new CenarioSimulacao(contas, planos);
    }

    /**
     * Gera um cenário aleatório porém reproduzível: depósitos (40%), saques (30%) e
     * transferências (30%) de R$ 1,00 a R$ 100,00, entre contas sorteadas, todas compartilhadas
     * por todas as threads.
     *
     * @param seed                 semente do sorteio (mesma seed, mesmo plano)
     * @param threads              quantidade de threads de operação (>= 1)
     * @param operacoesPorThread   operações de cada thread (>= 1)
     * @param numeroDeContas       contas compartilhadas (>= 2, para existirem transferências)
     */
    public static CenarioSimulacao gerar(long seed, int threads, int operacoesPorThread, int numeroDeContas) {
        if (threads < 1) {
            throw new IllegalArgumentException("O número de threads deve ser >= 1.");
        }
        if (operacoesPorThread < 1) {
            throw new IllegalArgumentException("O número de operações por thread deve ser >= 1.");
        }
        if (numeroDeContas < 2) {
            throw new IllegalArgumentException("São necessárias ao menos 2 contas (para haver transferências).");
        }

        // Fase 1: sorteia o plano usando só ÍNDICES de contas e soma os débitos de cada conta.
        record Sorteada(TipoTransacao tipo, int conta, int destino, BigDecimal valor) { }
        Random random = new Random(seed);
        BigDecimal[] debitos = new BigDecimal[numeroDeContas];
        Arrays.fill(debitos, BigDecimal.ZERO);
        List<List<Sorteada>> sorteadas = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            List<Sorteada> daThread = new ArrayList<>(operacoesPorThread);
            for (int i = 0; i < operacoesPorThread; i++) {
                int sorteio = random.nextInt(100);
                TipoTransacao tipo = sorteio < 40 ? TipoTransacao.DEPOSITO
                        : sorteio < 70 ? TipoTransacao.SAQUE : TipoTransacao.TRANSFERENCIA;
                int conta = random.nextInt(numeroDeContas);
                int destino = (tipo == TipoTransacao.TRANSFERENCIA)
                        ? (conta + 1 + random.nextInt(numeroDeContas - 1)) % numeroDeContas
                        : -1;
                BigDecimal valor = BigDecimal.valueOf(100 + random.nextInt(9901), 2); // 1,00 .. 100,00
                if (tipo != TipoTransacao.DEPOSITO) {
                    debitos[conta] = debitos[conta].add(valor);
                }
                daThread.add(new Sorteada(tipo, conta, destino, valor));
            }
            sorteadas.add(daThread);
        }

        // Fase 2: cria as contas já com saldo que cobre todos os débitos do plano.
        List<Conta> contas = new ArrayList<>();
        for (int c = 0; c < numeroDeContas; c++) {
            contas.add(new Conta(new Cliente("Titular " + (c + 1)), debitos[c].add(RESERVA)));
        }

        // Fase 3: troca os índices pelas contas reais.
        List<List<OperacaoPlanejada>> planos = new ArrayList<>();
        for (List<Sorteada> daThread : sorteadas) {
            List<OperacaoPlanejada> plano = new ArrayList<>(daThread.size());
            for (Sorteada s : daThread) {
                plano.add(new OperacaoPlanejada(s.tipo(), contas.get(s.conta()),
                        s.destino() < 0 ? null : contas.get(s.destino()), s.valor()));
            }
            planos.add(plano);
        }
        return de(contas, planos);
    }

    /** Aritmética pura: não chama nenhum método de {@link Conta}. */
    private static List<BigDecimal> calcularEsperado(List<Conta> contas, List<BigDecimal> iniciais,
                                                     List<List<OperacaoPlanejada>> planos) {
        Map<Conta, Integer> indice = new IdentityHashMap<>();
        for (int i = 0; i < contas.size(); i++) {
            indice.put(contas.get(i), i);
        }
        BigDecimal[] esperado = iniciais.toArray(new BigDecimal[0]);
        for (List<OperacaoPlanejada> plano : planos) {
            for (OperacaoPlanejada op : plano) {
                int origem = posicao(indice, op.conta());
                switch (op.tipo()) {
                    case DEPOSITO -> esperado[origem] = esperado[origem].add(op.valor());
                    case SAQUE -> esperado[origem] = esperado[origem].subtract(op.valor());
                    case TRANSFERENCIA -> {
                        esperado[origem] = esperado[origem].subtract(op.valor());
                        int destino = posicao(indice, op.destino());
                        esperado[destino] = esperado[destino].add(op.valor());
                    }
                    default -> throw new IllegalStateException("Tipo de operação não tratado: " + op.tipo());
                }
            }
        }
        return List.of(esperado);
    }

    private static int posicao(Map<Conta, Integer> indice, Conta conta) {
        Integer i = indice.get(conta);
        if (i == null) {
            throw new IllegalArgumentException(
                    "O plano usa uma conta (" + conta.getTitular().getNome() + ") que não está na lista de contas do cenário.");
        }
        return i;
    }

    private static List<List<OperacaoPlanejada>> copiarPlanos(List<List<OperacaoPlanejada>> planos) {
        List<List<OperacaoPlanejada>> copia = new ArrayList<>();
        for (List<OperacaoPlanejada> plano : planos) {
            copia.add(List.copyOf(plano));
        }
        return List.copyOf(copia);
    }

    private static Map<TipoTransacao, Integer> contar(List<List<OperacaoPlanejada>> planos) {
        Map<TipoTransacao, Integer> contagem = new EnumMap<>(TipoTransacao.class);
        for (TipoTransacao tipo : TipoTransacao.values()) {
            contagem.put(tipo, 0);
        }
        for (List<OperacaoPlanejada> plano : planos) {
            for (OperacaoPlanejada op : plano) {
                contagem.merge(op.tipo(), 1, Integer::sum);
            }
        }
        return contagem;
    }

    /** Marca o cenário como usado; falha se ele já foi executado antes (ver "Uso único"). */
    void consumir() {
        if (!consumido.compareAndSet(false, true)) {
            throw new IllegalStateException(
                    "Este cenário já foi executado: as contas já foram alteradas. Gere um novo cenário.");
        }
    }

    public List<Conta> getContas() {
        return contas;
    }

    public List<BigDecimal> getSaldosIniciais() {
        return saldosIniciais;
    }

    public List<BigDecimal> getSaldosEsperados() {
        return saldosEsperados;
    }

    /** Um plano por thread, na ordem das threads. Listas imutáveis. */
    public List<List<OperacaoPlanejada>> getPlanos() {
        return planos;
    }

    public int getTotalDeOperacoes() {
        return contagemPorTipo.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<TipoTransacao, Integer> getContagemPorTipo() {
        return Map.copyOf(contagemPorTipo);
    }
}
