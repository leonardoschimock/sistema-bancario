package app;

import domain.Conta;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import persistence.ConexaoFactory;
import persistence.ContaRepository;
import persistence.PersistenciaException;
import persistence.TransacaoRepository;

/**
 * Ponto de entrada de linha de comando para exportar o extrato de uma conta.
 *
 * <pre>
 *   ExportarExtrato &lt;idDaConta&gt; [arquivo.txt]
 * </pre>
 * Sem o segundo argumento, grava em {@code extrato-conta-<id>.txt} na pasta atual. A conexão vem
 * de {@link ConexaoFactory#doAmbiente()} (BANCO_URL, BANCO_USUARIO, BANCO_SENHA ou -Dbanco.*).
 *
 * Somente leitura: consulta a conta e as transações e grava o arquivo; não altera o banco.
 * Códigos de saída: 0 sucesso; 1 uso incorreto; 2 conta não encontrada; 3 falha de banco ou de arquivo.
 */
public final class ExportarExtrato {

    private ExportarExtrato() {
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Uso: ExportarExtrato <idDaConta> [arquivo.txt]");
            System.exit(1);
        }

        long id;
        try {
            id = Long.parseLong(args[0].trim());
        } catch (NumberFormatException e) {
            System.err.println("O id da conta deve ser um número inteiro: " + args[0]);
            System.exit(1);
            return;
        }
        Path destino = Path.of(args.length == 2 ? args[1] : "extrato-conta-" + id + ".txt");

        try {
            ConexaoFactory conexoes = ConexaoFactory.doAmbiente();
            Optional<Conta> conta = new ContaRepository(conexoes).buscarPorId(id);
            if (conta.isEmpty()) {
                System.err.println("Conta não encontrada: " + id);
                System.exit(2);
                return;
            }

            Path gravado = new ExtratoExportador(new TransacaoRepository(conexoes)).exportar(conta.get(), destino);
            System.out.print(Files.readString(gravado, StandardCharsets.UTF_8));
            System.out.println("Extrato gravado em: " + gravado.toAbsolutePath());
        } catch (PersistenciaException | ExportacaoExtratoException | IOException e) {
            System.err.println("Falha ao exportar o extrato: " + e.getMessage());
            System.exit(3);
        }
    }
}