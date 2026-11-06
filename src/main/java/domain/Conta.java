package domain;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conta bancária: núcleo das regras de negócio e único lugar onde o saldo é alterado.
 *
 * <h2>Concorrência (etapa atual: Semana 4)</h2>
 *
 * <b>Estado compartilhado.</b> O único estado mutável é o {@code saldo}. O titular é final
 * (imutável) e BigDecimal é imutável, então quem recebe o valor de {@link #getSaldo()} não
 * consegue alterar o saldo. Várias threads podem operar a mesma conta ao mesmo tempo,
 * portanto TODO acesso ao {@code saldo} (leitura ou escrita) acontece dentro de
 * {@code synchronized (lock)}, onde {@code lock} é o lock PRIVADO DA PRÓPRIA CONTA.
 *
 * <b>Regiões críticas protegidas</b> (cada uma é um bloco que não pode ser intercalado com
 * outro acesso ao saldo):
 * <ul>
 *   <li>{@link #depositar}: ler o saldo, somar e gravar (3 passos que parecem 1 linha);</li>
 *   <li>{@link #sacar}: verificar saldo suficiente E subtrair/gravar. A verificação e a gravação
 *       precisam ser indivisíveis; senão duas threads passam na verificação com o mesmo saldo e
 *       sacam mais do que existe (verificar-e-agir);</li>
 *   <li>{@link #transferirPara}: verificar saldo da origem, debitar a origem e creditar o destino,
 *       tudo de uma vez, com os locks das DUAS contas adquiridos numa ordem global (ver abaixo):
 *       nenhuma thread enxerga (nem altera) o dinheiro "no meio do caminho";</li>
 *   <li>{@link #getSaldo}: leitura; o lock garante que a thread leitora enxergue o valor mais
 *       recente gravado por outra thread (visibilidade, não só exclusão mútua).</li>
 * </ul>
 * As validações de argumento (valor positivo, no máximo 2 casas, destino nulo, mesma conta)
 * ficam FORA do lock: dependem só dos parâmetros, não do estado compartilhado, e assim a região
 * crítica fica a menor possível.
 *
 * <b>Por que isso protege o saldo.</b> {@code synchronized} oferece exclusão mútua (no máximo
 * uma thread por vez dentro das regiões críticas) e uma relação happens-before (o que uma thread
 * gravou ao sair do bloco é visível para a próxima que entrar). Como todas as leituras e
 * escritas do saldo de uma conta passam pelo mesmo lock (o dela), não existe intercalação de
 * "ler / calcular / gravar" sobre aquele saldo.
 * Se uma validação lançar exceção dentro do bloco, o lock é liberado automaticamente e nenhum
 * saldo foi alterado (a exceção é lançada antes de qualquer gravação).
 *
 * <b>Por que o lock é privado.</b> Se o lock fosse {@code this} ou {@code Conta.class}, código
 * externo poderia fazer {@code synchronized (conta) {...}} e interferir (ou travar) as
 * operações. Um objeto privado só é acessível aqui dentro.
 *
 * <h2>Transferências e deadlock (Semana 4)</h2>
 *
 * Cada conta tem seu PRÓPRIO lock, então operações em contas diferentes não se bloqueiam. Uma
 * transferência precisa de exclusão sobre DUAS contas ao mesmo tempo, o que exige adquirir dois
 * locks, e a ORDEM de aquisição é o que decide se pode haver deadlock.
 *
 * <b>O problema.</b> Se cada thread travasse primeiro a sua origem e depois o destino, A→B
 * travaria A e esperaria B, enquanto B→A travaria B e esperaria A: espera circular, para sempre.
 *
 * <b>A estratégia.</b> Cada conta recebe, ao ser criada, um {@code id} único e imutável. Toda
 * transferência trava SEMPRE primeiro a conta de MENOR id e depois a de MAIOR id, não importa
 * qual delas é a origem. A→B e B→A passam a executar a mesma sequência de aquisição.
 *
 * <b>Por que não há espera circular.</b> Quem segura a conta de id maior só chegou nela depois
 * de já segurar a de id menor; logo ninguém segura a "maior" esperando a "menor". Como a ordem
 * é total e igual para todas as threads, não existe ciclo de espera, também para cadeias com
 * mais de duas contas (A→B, B→C, C→A).
 *
 * <b>Por que o id e não o hashCode.</b> {@code hashCode}/{@code identityHashCode} podem
 * colidir, e numa colisão a ordem deixaria de ser total. O id vem de um contador atômico, é
 * único e estável durante toda a vida da conta.
 *
 * <b>Integridade.</b> Com as duas contas travadas, a verificação de saldo e as duas gravações
 * formam uma só operação. Se a validação falhar, a exceção sai antes de qualquer gravação e
 * nenhum saldo muda.
 *
 * <h2>Decisões de implementação (não definidas na especificação)</h2>
 * <ul>
 *   <li>O saldo usa BigDecimal com 2 casas decimais, para evitar erros de arredondamento de double.</li>
 *   <li>Valores com mais de 2 casas decimais são recusados, em vez de arredondados em silêncio.</li>
 *   <li>Transferir para a própria conta é recusado.</li>
 *   <li>Operações inválidas lançam exceção ANTES de qualquer alteração de saldo.</li>
 * </ul>
 */
public class Conta {

    private static final int CASAS_DECIMAIS = 2;

    /** Gerador dos identificadores usados para ordenar os locks (único por conta, nunca repete). */
    private static final AtomicLong PROXIMO_ID = new AtomicLong();

    /** Identificador único e imutável; define a ordem global de aquisição dos locks. */
    private final long id = PROXIMO_ID.incrementAndGet();

    /**
     * Lock que protege o saldo DESTA conta. Privado: ninguém fora desta classe consegue adquiri-lo.
     */
    private final Object lock = new Object();

    private final Cliente titular;

    /** Estado compartilhado. Só pode ser lido ou escrito dentro de {@code synchronized (lock)}. */
    private BigDecimal saldo;

    /**
     * Identificador da linha desta conta no banco (chave primária). É {@code null} enquanto a conta
     * não foi persistida. NÃO é o {@link #id} usado para ordenar locks: aquele é gerado por um
     * contador em memória e reinicia a cada execução. Acesso protegido por {@code lock}.
     */
    private Long idPersistencia;

    public Conta(Cliente titular, BigDecimal saldoInicial) {
        if (titular == null) {
            throw new IllegalArgumentException("O titular da conta é obrigatório.");
        }
        BigDecimal inicial = normalizar(saldoInicial);
        if (inicial.signum() < 0) {
            throw new IllegalArgumentException("O saldo inicial não pode ser negativo.");
        }
        this.titular = titular;
        // Sem lock: o objeto ainda não foi publicado para outras threads. Ao entregar a conta a
        // outra thread (Thread.start, ExecutorService.submit), o Java garante a visibilidade.
        this.saldo = inicial;
    }

    public Cliente getTitular() {
        return titular;
    }

    /** Somente leitura: BigDecimal é imutável, então quem recebe o valor não altera o saldo. */
    public BigDecimal getSaldo() {
        synchronized (lock) {
            return saldo;
        }
    }

    /**
     * Reconstrói uma conta já persistida (usado pela camada de persistência). Aplica as mesmas
     * validações do construtor: titular obrigatório, saldo com no máximo 2 casas e não negativo.
     */
    public static Conta restaurar(long idPersistencia, Cliente titular, BigDecimal saldo) {
        Conta conta = new Conta(titular, saldo);
        conta.idPersistencia = idPersistencia; // ainda não publicada: não precisa de lock
        return conta;
    }

    /** Identificador no banco, ou {@code null} se a conta ainda não foi persistida. */
    public Long getIdPersistencia() {
        synchronized (lock) {
            return idPersistencia;
        }
    }

    /**
     * Registra o identificador gerado pelo banco. Só pode ser chamado uma vez: depois de definido,
     * o identificador da conta não muda. Não altera o saldo.
     */
    public void definirIdPersistencia(long idPersistencia) {
        synchronized (lock) {
            if (this.idPersistencia != null) {
                throw new IllegalStateException(
                        "A conta já possui identificador de persistência: " + this.idPersistencia + ".");
            }
            this.idPersistencia = idPersistencia;
        }
    }

    public void depositar(BigDecimal valor) {
        BigDecimal v = validarValorPositivo(valor);       // fora do lock: só usa o parâmetro
        synchronized (lock) {                             // região crítica: ler -> somar -> gravar
            saldo = saldo.add(v);
        }
    }

    public void sacar(BigDecimal valor) {
        BigDecimal v = validarValorPositivo(valor);       // fora do lock: só usa o parâmetro
        synchronized (lock) {                             // região crítica: verificar E debitar, juntos
            garantirSaldoSuficiente(v);
            saldo = saldo.subtract(v);
        }
    }

    public void transferirPara(Conta destino, BigDecimal valor) {
        if (destino == null) {
            throw new IllegalArgumentException("A conta de destino é obrigatória.");
        }
        if (destino == this) {
            throw new IllegalArgumentException("Não é possível transferir para a própria conta.");
        }
        BigDecimal v = validarValorPositivo(valor);       // fora do lock: só usa o parâmetro

        // Ordem global de aquisição: sempre a conta de MENOR id primeiro, independentemente de
        // qual é a origem e qual é o destino. Os ids são únicos, então nunca há empate.
        Conta primeira = (this.id < destino.id) ? this : destino;
        Conta segunda = (primeira == this) ? destino : this;

        synchronized (primeira.lock) {
            synchronized (segunda.lock) {
                // Com as DUAS contas travadas: verificar, debitar e creditar formam uma só operação.
                garantirSaldoSuficiente(v);

                // Calcula os dois novos saldos antes de gravar qualquer um:
                // se algo falhasse no cálculo, nenhuma conta teria sido alterada.
                BigDecimal novoSaldoOrigem = this.saldo.subtract(v);
                BigDecimal novoSaldoDestino = destino.saldo.add(v);
                this.saldo = novoSaldoOrigem;
                destino.saldo = novoSaldoDestino;
            }
        }
    }

    /** Deve ser chamado somente com o {@code lock} desta conta adquirido (lê o saldo compartilhado). */
    private void garantirSaldoSuficiente(BigDecimal valor) {
        assert Thread.holdsLock(lock) : "garantirSaldoSuficiente exige o lock da conta.";
        if (saldo.compareTo(valor) < 0) {
            throw new SaldoInsuficienteException(
                    "Saldo insuficiente: saldo atual " + saldo + ", valor solicitado " + valor + ".");
        }
    }

    private static BigDecimal validarValorPositivo(BigDecimal valor) {
        BigDecimal v = normalizar(valor);
        if (v.signum() <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser maior que zero.");
        }
        return v;
    }

    /** Recusa null e mais de 2 casas decimais; devolve o valor com exatamente 2 casas. */
    private static BigDecimal normalizar(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("O valor é obrigatório.");
        }
        if (valor.stripTrailingZeros().scale() > CASAS_DECIMAIS) {
            throw new IllegalArgumentException("O valor deve ter no máximo 2 casas decimais.");
        }
        return valor.setScale(CASAS_DECIMAIS);
    }
}