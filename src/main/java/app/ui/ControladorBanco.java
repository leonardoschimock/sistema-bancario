package app.ui;

import app.BancoServico;
import app.ContaResumo;
import app.ExecucaoSimulacao;
import app.ExportacaoExtratoException;
import app.ParametrosSimulacao;
import domain.LancamentoExtrato;
import domain.SaldoInsuficienteException;
import domain.Transacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import persistence.PersistenciaException;

/**
 * Único elo entre a interface e {@link BancoServico}.
 *
 * <p>Cada método roda a chamada ao serviço em um {@link SwingWorker} (fora da EDT) e entrega o
 * {@link Resultado} ao ouvinte já na EDT. Nenhuma exceção chega às telas: {@link IllegalArgumentException},
 * {@link SaldoInsuficienteException}, {@link PersistenciaException} e {@link ExportacaoExtratoException}
 * são traduzidas em {@link Falha} (o que aconteceu / o que fazer). Não há regra de saldo aqui.
 */
public final class ControladorBanco {

    private static final Logger LOG = Logger.getLogger("app.ui");

    private static final String FAZER_BANCO_LEITURA =
            "Verifique se o banco está em execução e se BANCO_URL, BANCO_USUARIO e BANCO_SENHA estão corretos.";
    private static final String FAZER_BANCO_ESCRITA =
            "A operação pode ter sido aplicada; confira o extrato antes de tentar de novo.";
    private static final String FAZER_BANCO_CONTA =
            "A conta não foi registrada. Verifique o banco e tente de novo.";

    /** Categoria de uma falha; {@link Falha#recusa()} separa recusa de regra de falha técnica. */
    public enum Tipo {
        VALOR, NOME, SALDO_INICIAL, SALDO_INSUFICIENTE, CONTA_INEXISTENTE, MESMA_CONTA,
        BANCO, ARQUIVO, SIMULACAO, CANCELADA, INESPERADO
    }

    /** Falha pronta para exibir em linguagem simples; o detalhe técnico fica recolhido na interface. */
    public record Falha(Tipo tipo, String titulo, String oQueAconteceu, String oQueFazer, String detalheTecnico) {

        /** {@code true} quando uma regra recusou o pedido (aviso); {@code false} para falha técnica. */
        public boolean recusa() {
            return switch (tipo) {
                case VALOR, NOME, SALDO_INICIAL, SALDO_INSUFICIENTE, CONTA_INEXISTENTE, MESMA_CONTA -> true;
                default -> false;
            };
        }
    }

    /** Sucesso com valor ou {@link Falha}. */
    public static final class Resultado<T> {
        private final T valor;
        private final Falha falha;

        private Resultado(T valor, Falha falha) {
            this.valor = valor;
            this.falha = falha;
        }

        static <T> Resultado<T> ok(T valor) {
            return new Resultado<>(valor, null);
        }

        static <T> Resultado<T> falha(Falha falha) {
            return new Resultado<>(null, falha);
        }

        public boolean ok() {
            return falha == null;
        }

        public T valor() {
            return valor;
        }

        public Falha falha() {
            return falha;
        }
    }

    /** Parâmetro de simulação recusado pelo serviço (mensagem já é de usuário). */
    private static final class ParametrosInvalidos extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ParametrosInvalidos(IllegalArgumentException causa) {
            super(causa.getMessage(), causa);
        }
    }

    private final BancoServico servico;

    public ControladorBanco(BancoServico servico) {
        if (servico == null) {
            throw new IllegalArgumentException("O serviço bancário é obrigatório.");
        }
        this.servico = servico;
    }

    /** Conexão configurada por BANCO_URL, BANCO_USUARIO e BANCO_SENHA (nada fica no código). */
    public static ControladorBanco doAmbiente() {
        return new ControladorBanco(BancoServico.doAmbiente());
    }

    // ------------------------------------------------------------------ operações assíncronas

    public void criarConta(String nome, BigDecimal saldoInicial, Consumer<Resultado<ContaResumo>> aoConcluir) {
        executar(() -> servico.criarConta(nome, saldoInicial), FAZER_BANCO_CONTA, aoConcluir);
    }

    public void listarContas(Consumer<Resultado<List<ContaResumo>>> aoConcluir) {
        executar(servico::listarContas, FAZER_BANCO_LEITURA, aoConcluir);
    }

    public void consultarSaldo(long idConta, Consumer<Resultado<BigDecimal>> aoConcluir) {
        executar(() -> servico.consultarSaldo(idConta), FAZER_BANCO_LEITURA, aoConcluir);
    }

    public void depositar(long idConta, BigDecimal valor, Consumer<Resultado<Transacao>> aoConcluir) {
        executar(() -> servico.depositar(idConta, valor), FAZER_BANCO_ESCRITA, aoConcluir);
    }

    public void sacar(long idConta, BigDecimal valor, Consumer<Resultado<Transacao>> aoConcluir) {
        executar(() -> servico.sacar(idConta, valor), FAZER_BANCO_ESCRITA, aoConcluir);
    }

    public void transferir(long idOrigem, long idDestino, BigDecimal valor,
                           Consumer<Resultado<Transacao>> aoConcluir) {
        executar(() -> servico.transferir(idOrigem, idDestino, valor), FAZER_BANCO_ESCRITA, aoConcluir);
    }

    public void consultarExtrato(long idConta, Consumer<Resultado<List<LancamentoExtrato>>> aoConcluir) {
        executar(() -> servico.consultarExtrato(idConta), FAZER_BANCO_LEITURA, aoConcluir);
    }

    public void exportarExtrato(long idConta, Path destino, Consumer<Resultado<Path>> aoConcluir) {
        executar(() -> servico.exportarExtrato(idConta, destino), FAZER_BANCO_LEITURA, aoConcluir);
    }

    /**
     * Roda a simulação (bloqueante, sem progresso parcial). Devolve o worker para que a tela possa
     * chamar {@code cancel(true)}: isso interrompe a thread que aguarda e o serviço cancela as threads
     * da simulação (ver Javadoc de {@code SimulacaoConcorrente.executar}).
     */
    public SwingWorker<Resultado<ExecucaoSimulacao>, Void> executarSimulacao(
            ParametrosSimulacao parametros, Consumer<Resultado<ExecucaoSimulacao>> aoConcluir) {
        return executar(() -> {
            try {
                return servico.executarSimulacao(parametros);
            } catch (IllegalArgumentException e) {
                throw new ParametrosInvalidos(e);   // não passa pela classificação de valor/nome das contas
            }
        }, FAZER_BANCO_LEITURA, aoConcluir);
    }

    // ------------------------------------------------------------------ infraestrutura

    private <T> SwingWorker<Resultado<T>, Void> executar(Callable<T> tarefa, String fazerSeBanco,
                                                         Consumer<Resultado<T>> aoConcluir) {
        SwingWorker<Resultado<T>, Void> worker = new SwingWorker<>() {
            @Override
            protected Resultado<T> doInBackground() {
                try {
                    return Resultado.ok(tarefa.call());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Resultado.falha(traduzir(e, fazerSeBanco));
                } catch (Exception e) {
                    return Resultado.falha(traduzir(e, fazerSeBanco));
                }
            }

            @Override
            protected void done() {
                Resultado<T> resultado;
                try {
                    resultado = get();
                } catch (CancellationException e) {
                    resultado = Resultado.falha(traduzir(e, fazerSeBanco));
                } catch (ExecutionException | InterruptedException e) {
                    resultado = Resultado.falha(traduzir(e, fazerSeBanco));
                }
                aoConcluir.accept(resultado);
            }
        };
        worker.execute();
        return worker;
    }

    /** Traduz exceções em mensagens de usuário; o detalhe técnico vai só para o log e para o detalhe recolhido. */
    static Falha traduzir(Throwable erro, String fazerSeBanco) {
        Throwable e = erro instanceof ExecutionException && erro.getCause() != null ? erro.getCause() : erro;
        String detalhe = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (e instanceof CancellationException || e instanceof InterruptedException) {
            return new Falha(Tipo.CANCELADA, "Operação interrompida",
                    "A operação foi interrompida antes de terminar.", "Se precisar, inicie-a de novo.", detalhe);
        }
        if (e instanceof ParametrosInvalidos) {
            return new Falha(Tipo.SIMULACAO, "Parâmetros inválidos", String.valueOf(e.getMessage()),
                    "Confira os valores informados e tente de novo.", detalhe);
        }
        if (e instanceof SaldoInsuficienteException) {
            return new Falha(Tipo.SALDO_INSUFICIENTE, "Saldo insuficiente",
                    "A conta de origem não tem saldo suficiente para esta operação.",
                    "Confira o saldo e tente um valor menor.", detalhe);
        }
        if (e instanceof PersistenciaException) {
            LOG.log(Level.WARNING, "Falha de persistência", e);
            boolean parcial = e.getMessage() != null && e.getMessage().contains("NÃO foi gravada");
            return new Falha(Tipo.BANCO, "Banco de dados indisponível",
                    parcial ? "A operação foi aceita, mas não foi gravada por completo no banco."
                            : "Não foi possível acessar o banco de dados.",
                    fazerSeBanco, detalhe);
        }
        if (e instanceof ExportacaoExtratoException) {
            LOG.log(Level.WARNING, "Falha ao exportar o extrato", e);
            return new Falha(Tipo.ARQUIVO, "Extrato não exportado",
                    "Não foi possível gravar o arquivo do extrato.",
                    "Escolha outra pasta, confira a permissão de escrita e tente de novo.", detalhe);
        }
        if (e instanceof IllegalArgumentException) {
            return traduzirArgumento(e, detalhe);
        }
        LOG.log(Level.WARNING, "Erro inesperado", e);
        return new Falha(Tipo.INESPERADO, "Algo deu errado", "Ocorreu um erro inesperado.",
                "Tente de novo. Se persistir, reinicie o aplicativo.", detalhe);
    }

    /** As mensagens de {@code IllegalArgumentException} vêm do domínio/serviço; aqui só são classificadas. */
    private static Falha traduzirArgumento(Throwable e, String detalhe) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("própria conta") || m.contains("mesma conta")) {
            return new Falha(Tipo.MESMA_CONTA, "Contas iguais", "A origem e o destino são a mesma conta.",
                    "Escolha uma conta de destino diferente.", detalhe);
        }
        if (m.startsWith("conta não encontrada")) {
            return new Falha(Tipo.CONTA_INEXISTENTE, "Conta não encontrada", "Essa conta não existe mais.",
                    "Atualize a lista de contas e escolha outra.", detalhe);
        }
        if (m.contains("destino")) {
            return new Falha(Tipo.CONTA_INEXISTENTE, "Conta de destino", "A conta de destino não foi informada.",
                    "Escolha a conta de destino.", detalhe);
        }
        if (m.contains("saldo inicial")) {
            return new Falha(Tipo.SALDO_INICIAL, "Saldo inicial inválido", msg,
                    "Informe 0,00 ou um valor positivo, com até 2 casas decimais.", detalhe);
        }
        if (m.contains("titular") || m.contains("nome")) {
            return new Falha(Tipo.NOME, "Nome do titular", "O nome do titular é obrigatório.",
                    "Informe o nome do titular da conta.", detalhe);
        }
        if (m.contains("valor")) {
            return new Falha(Tipo.VALOR, "Valor inválido", msg,
                    "Informe um valor maior que zero, com até 2 casas decimais.", detalhe);
        }
        return new Falha(Tipo.SIMULACAO, "Dados inválidos", msg.isEmpty() ? "Os dados informados não são válidos." : msg,
                "Confira os valores informados e tente de novo.", detalhe);
    }
}
