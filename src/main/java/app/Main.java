package app;

import domain.TipoTransacao;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import persistence.PersistenciaException;
import simulacao.CenarioSimulacao;
import simulacao.ResultadoSimulacao;
import simulacao.ResultadoSimulacao.ComparacaoSaldo;
import simulacao.ResultadoSimulacao.ResumoOperador;
import simulacao.SimulacaoConcorrente;

/**
 * Executa a simulação bancária concorrente e imprime os resultados.
 *
 * <pre>
 *   Main [seed] [threads] [operacoesPorThread] [contas] [rodadas] [--persistir]
 *   padrão:  42      8          20000             4        3
 * </pre>
 *
 * Cada rodada gera um cenário NOVO com a mesma seed: o plano de operações e os saldos esperados
 * são idênticos em todas as rodadas (e em todas as execuções com os mesmos argumentos); só a
 * intercalação das threads varia. O saldo final de cada conta deve ser sempre o mesmo.
 *
 * A simulação roda inteira em memória: sem a opção {@code --persistir}, não acessa o banco.
 *
 * <h2>Opção --persistir</h2>
 * Ao FINAL da última rodada (nunca durante a execução das threads) e somente se ela foi
 * consistente, grava no banco as contas dessa rodada, com o saldo final, por meio de
 * {@link BancoServico}. NÃO grava as operações individuais (seriam dezenas de milhares de
 * linhas). A conexão vem de BANCO_URL, BANCO_USUARIO e BANCO_SENHA. Este adaptador não contém SQL
 * nem regra de saldo.
 *
 * <h2>Código de saída</h2>
 * 0: todas as rodadas consistentes · 1: alguma rodada inconsistente · 2: argumentos inválidos ·
 * 3: falha ao gravar no banco (com --persistir).
 */
public final class Main {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final Duration PRAZO = Duration.ofSeconds(120);
    private static final String OPCAO_PERSISTIR = "--persistir";

    private Main() {
    }

    public static void main(String[] argumentos) throws InterruptedException {
        boolean persistir = List.of(argumentos).contains(OPCAO_PERSISTIR);
        String[] args = List.of(argumentos).stream()
                .filter(a -> !OPCAO_PERSISTIR.equals(a))
                .toArray(String[]::new);
        long seed;
        int threads;
        int operacoes;
        int contas;
        int rodadas;
        try {
            seed = args.length > 0 ? Long.parseLong(args[0]) : 42;
            threads = inteiroPositivo(args, 1, 8, "threads");
            operacoes = inteiroPositivo(args, 2, 20_000, "operacoesPorThread");
            contas = inteiroPositivo(args, 3, 4, "contas");
            rodadas = inteiroPositivo(args, 4, 3, "rodadas");
            if (contas < 2) {
                throw new IllegalArgumentException("O número de contas deve ser >= 2 (para haver transferências).");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Argumentos inválidos: " + e.getMessage());
            System.err.println("Uso: Main [seed] [threads] [operacoesPorThread] [contas] [rodadas] [--persistir]");
            System.exit(2);
            return;
        }

        System.out.println("=== Simulação bancária concorrente ===");
        System.out.println("Java                 : " + System.getProperty("java.version"));
        System.out.println("Núcleos disponíveis  : " + Runtime.getRuntime().availableProcessors());
        System.out.printf("Parâmetros           : seed=%d | threads=%d | operações/thread=%d | contas=%d | rodadas=%d%n",
                seed, threads, operacoes, contas, rodadas);

        int inconsistentes = 0;
        boolean falhaAoGravar = false;
        for (int rodada = 1; rodada <= rodadas; rodada++) {
            // Cenário novo a cada rodada (as contas são alteradas pela simulação), mesma seed = mesmo plano.
            CenarioSimulacao cenario = CenarioSimulacao.gerar(seed, threads, operacoes, contas);
            if (rodada == 1) {
                imprimirPlano(cenario);
            }
            ResultadoSimulacao resultado = SimulacaoConcorrente.executar(cenario, PRAZO);
            if (!resultado.consistente()) {
                inconsistentes++;
            }
            if (rodada == 1 || !resultado.consistente()) {
                imprimirDetalhado(rodada, rodadas, resultado);
            } else {
                imprimirResumo(rodada, rodadas, resultado);
            }
            // Gravação só DEPOIS de todas as threads terminarem e só na última rodada.
            if (persistir && rodada == rodadas) {
                falhaAoGravar = !persistirContas(cenario, resultado);
            }
        }

        System.out.println();
        System.out.printf("Rodadas consistentes: %d de %d%n", rodadas - inconsistentes, rodadas);
        System.out.println(inconsistentes == 0
                ? "VEREDITO FINAL: CONSISTENTE (todos os saldos conferem com o esperado)"
                : "VEREDITO FINAL: INCONSISTENTE (" + inconsistentes + " rodada(s) com problemas)");
        if (falhaAoGravar) {
            System.out.println("ATENÇÃO: a gravação no banco (--persistir) FALHOU; código de saída 3.");
        }
        System.exit(inconsistentes > 0 ? 1 : falhaAoGravar ? 3 : 0);
    }

    /** Grava as contas da simulação pelo serviço. Devolve {@code false} se o banco falhou. */
    private static boolean persistirContas(CenarioSimulacao cenario, ResultadoSimulacao resultado) {
        System.out.println();
        if (!resultado.consistente()) {
            System.out.println("--persistir: contas NÃO gravadas, porque a última rodada foi inconsistente.");
            return true;
        }
        try {
            List<ContaResumo> gravadas = BancoServico.doAmbiente().gravarContasSimuladas(cenario.getContas());
            System.out.println("--persistir: contas gravadas no banco (saldo final da última rodada):");
            for (ContaResumo c : gravadas) {
                System.out.printf("  conta #%d | %-12s | saldo %20s%n", c.id(), c.titular(), brl(c.saldo()));
            }
            System.out.println("Para exportar um extrato: ExportarExtrato <idDaConta> [arquivo.txt]");
            return true;
        } catch (PersistenciaException e) {
            System.err.println("--persistir: falha ao gravar no banco: " + e.getMessage());
            return false;
        }
    }

    private static void imprimirPlano(CenarioSimulacao cenario) {
        System.out.println();
        System.out.println("--- Plano (idêntico em todas as rodadas) ---");
        System.out.printf("Operações planejadas : %d (", cenario.getTotalDeOperacoes());
        boolean primeiro = true;
        for (TipoTransacao tipo : TipoTransacao.values()) {
            System.out.printf("%s%d %s", primeiro ? "" : ", ", cenario.getContagemPorTipo().get(tipo), tipo);
            primeiro = false;
        }
        System.out.println(")");
        System.out.println("Saldos ESPERADOS (calculados antes de iniciar qualquer thread):");
        for (int i = 0; i < cenario.getContas().size(); i++) {
            System.out.printf("  %-12s inicial %20s | esperado %20s%n",
                    cenario.getContas().get(i).getTitular().getNome(),
                    brl(cenario.getSaldosIniciais().get(i)), brl(cenario.getSaldosEsperados().get(i)));
        }
    }

    private static void imprimirDetalhado(int rodada, int total, ResultadoSimulacao r) {
        System.out.println();
        System.out.printf("--- Rodada %d/%d ---%n", rodada, total);
        if (!r.terminou()) {
            System.out.printf("As threads NÃO terminaram (%.2fs). %s%n", segundos(r), r.diagnostico());
        } else {
            System.out.println("Threads (operações aceitas / recusadas / planejadas):");
            for (ResumoOperador o : r.operadores()) {
                System.out.printf("  %-11s aceitas=%-7d recusadas=%-5d planejadas=%-7d%s%n", o.nome(), o.aceitas(),
                        o.recusadas(), o.planejadas(), o.interrompida() ? "  (INTERROMPIDA)" : "");
                o.amostrasDeRecusa().forEach(a -> System.out.println("      recusa: " + a));
            }
            System.out.println("Saldos (esperado x obtido):");
            for (ComparacaoSaldo s : r.saldos()) {
                System.out.printf("  %-12s esperado %20s | obtido %20s | diferença %12s | %s%n", s.titular(),
                        brl(s.esperado()), brl(s.obtido()), brl(s.diferenca()), s.confere() ? "OK" : "DIVERGE");
            }
            BigDecimal esperado = soma(r.saldos(), true);
            BigDecimal obtido = soma(r.saldos(), false);
            System.out.printf("  %-12s esperado %20s | obtido %20s%n", "TOTAL", brl(esperado), brl(obtido));
            System.out.printf("Operações: planejadas=%d | aceitas=%d | recusadas=%d | threads com falha=%d%n",
                    r.totalPlanejadas(), r.totalAceitas(), r.totalRecusadas(), r.falhas().size());
            System.out.printf("Tempo: %.2fs%n", segundos(r));
        }
        r.falhas().forEach(f -> {
            System.out.println("FALHA em " + f.operador() + ":");
            f.causa().printStackTrace(System.out);
        });
        System.out.println(r.consistente() ? "Resultado: CONSISTENTE" : "Resultado: INCONSISTENTE");
        r.problemas().forEach(p -> System.out.println("  problema: " + p));
    }

    private static void imprimirResumo(int rodada, int total, ResultadoSimulacao r) {
        System.out.printf("--- Rodada %d/%d --- aceitas=%d/%d | recusadas=%d | contas OK=%d/%d | %.2fs | CONSISTENTE%n",
                rodada, total, r.totalAceitas(), r.totalPlanejadas(), r.totalRecusadas(),
                r.saldos().stream().filter(ComparacaoSaldo::confere).count(), r.saldos().size(), segundos(r));
    }

    private static BigDecimal soma(List<ComparacaoSaldo> saldos, boolean esperado) {
        BigDecimal total = BigDecimal.ZERO;
        for (ComparacaoSaldo s : saldos) {
            total = total.add(esperado ? s.esperado() : s.obtido());
        }
        return total;
    }

    private static double segundos(ResultadoSimulacao r) {
        return r.duracao().toNanos() / 1_000_000_000.0;
    }

    private static String brl(BigDecimal valor) {
        return String.format(PT_BR, "R$ %,.2f", valor);
    }

    private static int inteiroPositivo(String[] args, int posicao, int padrao, String nome) {
        if (args.length <= posicao) {
            return padrao;
        }
        try {
            int valor = Integer.parseInt(args[posicao].trim());
            if (valor > 0) {
                return valor;
            }
        } catch (NumberFormatException e) {
            // cai na mensagem abaixo
        }
        throw new IllegalArgumentException(nome + " deve ser um inteiro positivo: '" + args[posicao] + "'.");
    }
}
