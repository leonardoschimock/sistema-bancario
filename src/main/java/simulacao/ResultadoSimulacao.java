package simulacao;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * O que foi observado numa execução de {@link SimulacaoConcorrente}, e o veredito.
 *
 * @param terminou    {@code true} se TODAS as threads terminaram dentro do prazo. Se for
 *                    {@code false}, {@code operadores} e {@code saldos} ficam vazios de propósito:
 *                    ler contadores de threads ainda em execução seria uma corrida de dados, e
 *                    ler os saldos poderia bloquear para sempre (os locks podem estar presos).
 * @param duracao     tempo entre a largada e o fim da última thread (ou o estouro do prazo)
 * @param diagnostico explicação quando {@code terminou} é {@code false} (inclusive se a JVM
 *                    detectou deadlock); {@code null} caso contrário
 * @param operadores  resumo de cada thread de operação
 * @param falhas      exceções inesperadas, uma por thread que falhou (com a causa original)
 * @param saldos      comparação esperado x obtido por conta
 */
public record ResultadoSimulacao(
        boolean terminou,
        Duration duracao,
        String diagnostico,
        List<ResumoOperador> operadores,
        List<FalhaOperador> falhas,
        List<ComparacaoSaldo> saldos) {

    /** Resumo de uma thread. Os contadores só são lidos depois que a thread terminou. */
    public record ResumoOperador(String nome, int planejadas, int aceitas, int recusadas,
                                 List<String> amostrasDeRecusa, boolean interrompida) { }

    /** Exceção inesperada (qualquer uma que não seja saldo insuficiente) ocorrida numa thread. */
    public record FalhaOperador(String operador, Throwable causa) { }

    /** Saldo de uma conta: inicial, esperado (pré-calculado) e obtido (lido ao final). */
    public record ComparacaoSaldo(String titular, BigDecimal inicial, BigDecimal esperado, BigDecimal obtido) {

        /** {@code compareTo}, não {@code equals}: 10.0 e 10.00 têm o mesmo valor mas escalas diferentes. */
        public boolean confere() {
            return esperado.compareTo(obtido) == 0;
        }

        public BigDecimal diferenca() {
            return obtido.subtract(esperado);
        }
    }

    public int totalPlanejadas() {
        return operadores.stream().mapToInt(ResumoOperador::planejadas).sum();
    }

    public int totalAceitas() {
        return operadores.stream().mapToInt(ResumoOperador::aceitas).sum();
    }

    public int totalRecusadas() {
        return operadores.stream().mapToInt(ResumoOperador::recusadas).sum();
    }

    /** Tudo o que invalida a simulação. Lista vazia significa simulação consistente. */
    public List<String> problemas() {
        List<String> problemas = new ArrayList<>();
        if (!terminou) {
            problemas.add("As threads não terminaram no prazo. " + (diagnostico == null ? "" : diagnostico));
            return problemas; // sem contadores nem saldos confiáveis para analisar
        }
        for (FalhaOperador falha : falhas) {
            problemas.add("Falha inesperada em " + falha.operador() + ": " + falha.causa());
        }
        if (totalRecusadas() > 0) {
            problemas.add(totalRecusadas() + " operação(ões) recusada(s) por saldo insuficiente; "
                    + "o saldo esperado só vale se nenhuma for recusada.");
        }
        if (totalAceitas() + totalRecusadas() != totalPlanejadas()) {
            problemas.add("Operações concluídas (" + (totalAceitas() + totalRecusadas())
                    + ") diferente das planejadas (" + totalPlanejadas() + ").");
        }
        for (ComparacaoSaldo saldo : saldos) {
            if (!saldo.confere()) {
                problemas.add("Saldo divergente em " + saldo.titular() + ": esperado " + saldo.esperado()
                        + ", obtido " + saldo.obtido() + " (diferença " + saldo.diferenca() + ").");
            }
        }
        return problemas;
    }

    public boolean consistente() {
        return problemas().isEmpty();
    }
}
