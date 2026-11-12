package app;

import domain.Cliente;
import domain.Conta;
import domain.LancamentoExtrato;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import persistence.ConexaoFactory;
import persistence.ContaRepository;
import persistence.PersistenciaException;
import persistence.TransacaoRepository;
import simulacao.CenarioSimulacao;
import simulacao.ResultadoSimulacao;
import simulacao.SimulacaoConcorrente;

/**
 * Ponto ÚNICO de orquestração do sistema: liga a operação bancária à persistência e ao extrato.
 * É a única porta de entrada para a futura interface (gráfica ou de console).
 *
 * <h2>O que esta classe faz e o que NÃO faz</h2>
 * <ul>
 *   <li><b>Faz:</b> receber o pedido, chamar o método de {@link Conta} (que aplica a regra e a
 *       sincronização) e, SOMENTE se a operação foi aceita, criar a {@link Transacao}, gravá-la
 *       pelo repository e gravar o saldo resultante.</li>
 *   <li><b>Não faz:</b> não altera saldo (só {@link Conta} altera), não valida saldo nem valor
 *       (quem recusa é {@link Conta}), não tem SQL (fica em {@code persistence}), não usa
 *       {@code synchronized}, não imprime, não lê teclado e não chama {@code System.exit}.
 *       Devolve valores e lança exceções; {@code Main} e {@code ExportarExtrato} são os
 *       adaptadores de console.</li>
 * </ul>
 *
 * <h2>Fluxo de uma operação aceita (depósito, saque, transferência)</h2>
 * <ol>
 *   <li>{@code Conta.depositar/sacar/transferirPara} (regra + lock). Se recusar, a exceção
 *       ({@code SaldoInsuficienteException} ou {@code IllegalArgumentException}) sobe daqui e
 *       NADA é gravado: nenhuma Transacao, nenhum UPDATE de saldo.</li>
 *   <li>{@code TransacaoRepository.salvar} ou {@code salvarTransferencia} (INSERT da Transacao).</li>
 *   <li>{@code ContaRepository.salvar} (UPDATE do saldo, uma vez por conta envolvida).</li>
 * </ol>
 *
 * <h2>Registro de contas abertas (por que a MESMA instância)</h2>
 * Cada {@link Conta} tem o seu próprio lock. {@code ContaRepository.buscarPorId} cria uma
 * instância NOVA a cada chamada; duas instâncias da mesma linha têm locks diferentes e NÃO se
 * sincronizam: dois depósitos simultâneos, um em cada instância, perderiam atualização, que é
 * exatamente a race condition do trabalho. Por isso o serviço guarda um mapa thread-safe
 * (idPersistencia -> Conta) e entrega SEMPRE a mesma instância para o mesmo id. O mapa não é
 * cache de saldo: não guarda valores, só garante a identidade do objeto; todo acesso ao saldo
 * continua passando por {@link Conta}. Premissa: um único processo usa o banco. Contas carregadas
 * ficam em memória até o serviço ser descartado.
 *
 * <h2>Limitações conhecidas (leia antes de confiar no banco)</h2>
 * <ul>
 *   <li><b>Duas operações JDBC separadas.</b> Gravar a Transacao e atualizar o saldo são duas
 *       conexões/instruções distintas, sem {@code commit} conjunto. Se o banco falhar ENTRE
 *       elas, a Transacao fica gravada e o saldo da linha, defasado (ou o contrário). Nesse caso
 *       o serviço lança {@link PersistenciaException} dizendo que a operação foi aceita em
 *       memória e NÃO foi gravada por completo; o saldo em memória é o correto. Correção
 *       possível (não implementada): método novo nos repositories que receba a mesma
 *       {@code Connection} e faça commit/rollback das duas gravações juntas.</li>
 *   <li><b>Gravação fora do lock da Conta.</b> A gravação acontece depois que {@link Conta}
 *       liberou o lock. Duas threads na mesma conta podem gravar em ordem diferente da ordem em
 *       que operaram: o horário e a ordem das linhas do extrato podem divergir levemente da
 *       ordem real de execução, e o saldo gravado é um instantâneo que pode ficar defasado até a
 *       próxima gravação daquela conta. Durante a operação, o saldo em memória
 *       ({@link #consultarSaldo}) é a fonte de verdade.</li>
 *   <li><b>Saldo inicial não gera lançamento.</b> O extrato lista só movimentos (depósitos,
 *       saques e transferências).</li>
 * </ul>
 *
 * <h2>Regras para a interface</h2>
 * Chamadas deste serviço fazem JDBC, escrevem arquivo ou rodam a simulação (segundos). NÃO as
 * execute na thread de eventos da interface: use uma thread própria (em Swing, {@code SwingWorker};
 * em JavaFX, {@code Task}) e devolva o resultado à thread da interface só para exibir. A
 * própria classe é thread-safe: pode ser chamada de várias threads.
 *
 * <h2>Exceções (todas já existentes no projeto)</h2>
 * {@link IllegalArgumentException} (valor/nome inválido, conta inexistente, transferência para a
 * mesma conta), {@link domain.SaldoInsuficienteException}, {@link PersistenciaException} e
 * {@link ExportacaoExtratoException}; todas não verificadas.
 */
public class BancoServico {

    private final ContaRepository contas;
    private final TransacaoRepository transacoes;
    private final ExtratoExportador exportador;

    /** Contas abertas: idPersistencia -> a ÚNICA instância de Conta daquele id (ver Javadoc da classe). */
    private final ConcurrentMap<Long, Conta> abertas = new ConcurrentHashMap<>();

    public BancoServico(ContaRepository contas, TransacaoRepository transacoes) {
        if (contas == null) {
            throw new IllegalArgumentException("O repository de contas é obrigatório.");
        }
        if (transacoes == null) {
            throw new IllegalArgumentException("O repository de transações é obrigatório.");
        }
        this.contas = contas;
        this.transacoes = transacoes;
        this.exportador = new ExtratoExportador(transacoes);
    }

    /** Monta o serviço com a conexão configurada por BANCO_URL, BANCO_USUARIO e BANCO_SENHA. */
    public static BancoServico doAmbiente() {
        ConexaoFactory conexoes = ConexaoFactory.doAmbiente();
        return new BancoServico(new ContaRepository(conexoes), new TransacaoRepository(conexoes));
    }

    // ------------------------------------------------------------------ contas

    /**
     * Cria a conta com o saldo inicial informado e a grava no banco.
     *
     * @throws IllegalArgumentException nome vazio, saldo negativo ou com mais de 2 casas decimais
     * @throws PersistenciaException    falha ao gravar (a conta não é registrada)
     */
    public ContaResumo criarConta(String nomeTitular, BigDecimal saldoInicial) {
        Conta conta = new Conta(new Cliente(nomeTitular), saldoInicial); // validações: domínio
        contas.salvar(conta);                                            // INSERT; define o id
        return ContaResumo.de(registrar(conta));
    }

    /** Saldo em memória da conta (a fonte de verdade enquanto o serviço está em uso). */
    public BigDecimal consultarSaldo(long idConta) {
        return obterConta(idConta).getSaldo();
    }

    /**
     * Lista todas as contas do banco. Contas já abertas neste serviço aparecem com o saldo em
     * memória; as demais, com o saldo lido do banco (e passam a ficar abertas).
     */
    public List<ContaResumo> listarContas() {
        List<ContaResumo> resumos = new ArrayList<>();
        for (Conta doBanco : contas.listarTodas()) {
            resumos.add(ContaResumo.de(registrar(doBanco)));
        }
        return List.copyOf(resumos);
    }

    // -------------------------------------------------------------- operações

    /**
     * Deposita na conta e, aceito o depósito, grava a Transacao e o saldo.
     *
     * @return a Transacao gravada
     */
    public Transacao depositar(long idConta, BigDecimal valor) {
        Conta conta = obterConta(idConta);
        conta.depositar(valor);                                  // regra e lock: só em Conta
        Transacao transacao = new Transacao(TipoTransacao.DEPOSITO, valor);
        gravar(() -> {
            transacoes.salvar(conta, transacao);
            contas.salvar(conta);
        });
        return transacao;
    }

    /**
     * Saca da conta e, aceito o saque, grava a Transacao e o saldo.
     *
     * @throws domain.SaldoInsuficienteException se não houver saldo (nada é gravado)
     * @return a Transacao gravada
     */
    public Transacao sacar(long idConta, BigDecimal valor) {
        Conta conta = obterConta(idConta);
        conta.sacar(valor);                                      // regra e lock: só em Conta
        Transacao transacao = new Transacao(TipoTransacao.SAQUE, valor);
        gravar(() -> {
            transacoes.salvar(conta, transacao);
            contas.salvar(conta);
        });
        return transacao;
    }

    /**
     * Transfere entre duas contas e, aceita a transferência, grava UMA Transacao (visível no
     * extrato de origem e de destino) e o saldo das duas contas.
     *
     * @throws IllegalArgumentException          valor inválido, conta inexistente ou origem = destino
     * @throws domain.SaldoInsuficienteException se a origem não tiver saldo (nada é gravado)
     * @return a Transacao gravada
     */
    public Transacao transferir(long idOrigem, long idDestino, BigDecimal valor) {
        Conta origem = obterConta(idOrigem);
        Conta destino = obterConta(idDestino);                   // mesmo id => mesma instância
        origem.transferirPara(destino, valor);                   // regra, locks e ordem: só em Conta
        Transacao transacao = new Transacao(TipoTransacao.TRANSFERENCIA, valor);
        gravar(() -> {
            transacoes.salvarTransferencia(origem, destino, transacao);
            contas.salvar(origem);
            contas.salvar(destino);
        });
        return transacao;
    }

    // ---------------------------------------------------------------- extrato

    /** Extrato da conta lido do banco, em ordem cronológica, com o sentido de cada lançamento. */
    public List<LancamentoExtrato> consultarExtrato(long idConta) {
        return List.copyOf(transacoes.buscarExtrato(obterConta(idConta)));
    }

    /**
     * Consulta o extrato no banco e o exporta para um arquivo .txt (UTF-8), substituindo-o se já
     * existir. Somente leitura em relação aos dados bancários.
     *
     * @return o caminho gravado
     * @throws IllegalArgumentException   caminho nulo ou sem extensão .txt
     * @throws PersistenciaException      falha na consulta (nada é gravado)
     * @throws ExportacaoExtratoException falha ao escrever o arquivo
     */
    public Path exportarExtrato(long idConta, Path caminhoTxt) {
        return exportador.exportar(obterConta(idConta), caminhoTxt);
    }

    // -------------------------------------------------------------- simulação

    /**
     * Gera o cenário, roda a simulação concorrente (em memória) e compara esperado x obtido.
     * Bloqueia até o fim ou até o prazo: chame fora da thread de eventos da interface.
     *
     * <p>Com {@code persistirAoFinal}, as contas da simulação são gravadas no banco SOMENTE
     * depois que todas as threads terminaram e apenas se o resultado for consistente. Gravam-se
     * as contas com o saldo final; NÃO se gravam as operações individuais (uma linha por
     * operação seria dezenas de milhares de INSERTs). As contas passam a ficar abertas neste
     * serviço, com a mesma instância usada na simulação.
     *
     * @throws IllegalArgumentException parâmetros inválidos
     * @throws InterruptedException     se a thread que chamou for interrompida enquanto aguarda
     * @throws PersistenciaException    falha ao gravar as contas (a simulação já terminou)
     */
    public ExecucaoSimulacao executarSimulacao(ParametrosSimulacao parametros) throws InterruptedException {
        if (parametros == null) {
            throw new IllegalArgumentException("Os parâmetros da simulação são obrigatórios.");
        }
        CenarioSimulacao cenario = CenarioSimulacao.gerar(parametros.seed(), parametros.threads(),
                parametros.operacoesPorThread(), parametros.numeroDeContas());
        ResultadoSimulacao resultado = SimulacaoConcorrente.executar(cenario, parametros.prazo());

        List<ContaResumo> gravadas = List.of();
        // consistente() é falso se as threads não terminaram: nesse caso nem os saldos são lidos.
        if (parametros.persistirAoFinal() && resultado.consistente()) {
            gravadas = gravarContasSimuladas(cenario.getContas());
        }
        return new ExecucaoSimulacao(resultado, gravadas);
    }

    /**
     * Grava as contas de uma simulação JÁ TERMINADA (INSERT com o saldo atual de cada uma) e as
     * registra como abertas. Visível só no pacote: quem usa é {@code Main --persistir} e
     * {@link #executarSimulacao}. Não é atômico: se falhar na terceira conta, as duas primeiras
     * já estão gravadas.
     */
    List<ContaResumo> gravarContasSimuladas(List<Conta> simuladas) {
        if (simuladas == null || simuladas.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos uma conta para gravar.");
        }
        List<ContaResumo> gravadas = new ArrayList<>();
        for (Conta conta : simuladas) {
            contas.salvar(conta);
            gravadas.add(ContaResumo.de(registrar(conta)));
        }
        return List.copyOf(gravadas);
    }

    // ------------------------------------------------------- uso interno

    /** A instância única da conta: a aberta, ou a lida do banco (que passa a ficar aberta). */
    private Conta obterConta(long id) {
        Conta aberta = abertas.get(id);
        if (aberta != null) {
            return aberta;
        }
        Conta doBanco = contas.buscarPorId(id).orElseThrow(
                () -> new IllegalArgumentException("Conta não encontrada: " + id + "."));
        return registrar(doBanco);
    }

    /**
     * Registra a conta (já persistida) e devolve a instância vencedora. Se duas threads leram a
     * mesma linha ao mesmo tempo, só uma instância fica no mapa e a outra é descartada ANTES de
     * qualquer operação, então nunca existem duas instâncias em uso para o mesmo id.
     */
    private Conta registrar(Conta conta) {
        Conta anterior = abertas.putIfAbsent(conta.getIdPersistencia(), conta);
        return (anterior != null) ? anterior : conta;
    }

    /** Executa a gravação deixando claro, se falhar, que a operação JÁ foi aplicada em memória. */
    private static void gravar(Runnable gravacao) {
        try {
            gravacao.run();
        } catch (PersistenciaException e) {
            throw new PersistenciaException(
                    "A operação foi aceita e aplicada ao saldo em memória, mas NÃO foi gravada por "
                            + "completo no banco: " + e.getMessage(), e);
        }
    }
}
