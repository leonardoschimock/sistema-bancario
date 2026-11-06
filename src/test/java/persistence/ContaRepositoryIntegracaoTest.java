package persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * TESTES DE INTEGRAÇÃO: precisam de um servidor MariaDB com o schema.sql aplicado no banco
 * de teste. Não rodam no "mvn test" comum; use "mvn test -Pintegracao".
 */
@Tag("integracao")
class ContaRepositoryIntegracaoTest {

    private ContaRepository repository;

    @BeforeEach
    void preparar() {
        repository = new ContaRepository(BancoDeTeste.conexoes());
    }

    private static BigDecimal dinheiro(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("salvar conta nova faz INSERT e registra o id gerado na conta")
    void salvarContaNovaAtribuiId() {
        Conta conta = new Conta(new Cliente("Ana"), dinheiro("100.00"));

        repository.salvar(conta);

        assertNotNull(conta.getIdPersistencia());
    }

    @Test
    @DisplayName("Conta salva é recuperada com titular e saldo iguais")
    void salvaERecuperaConta() {
        Conta conta = new Conta(new Cliente("Ana Souza"), dinheiro("1234.56"));
        repository.salvar(conta);

        Optional<Conta> recuperada = repository.buscarPorId(conta.getIdPersistencia());

        assertTrue(recuperada.isPresent());
        assertEquals(conta.getIdPersistencia(), recuperada.get().getIdPersistencia());
        assertEquals("Ana Souza", recuperada.get().getTitular().getNome());
        assertEquals(0, dinheiro("1234.56").compareTo(recuperada.get().getSaldo()));
    }

    @Test
    @DisplayName("Titular com acentos sobrevive ao ciclo gravar/ler")
    void preservaAcentos() {
        Conta conta = new Conta(new Cliente("José Ângelo Conceição"), dinheiro("1.00"));
        repository.salvar(conta);

        Conta recuperada = repository.buscarPorId(conta.getIdPersistencia()).orElseThrow();

        assertEquals("José Ângelo Conceição", recuperada.getTitular().getNome());
    }

    @Test
    @DisplayName("buscarPorId de id inexistente devolve Optional vazio")
    void idInexistenteDevolveVazio() {
        assertTrue(repository.buscarPorId(Long.MAX_VALUE).isEmpty());
    }

    @Test
    @DisplayName("salvar conta já persistida atualiza o saldo sem criar nova linha")
    void salvarContaPersistidaAtualizaSaldo() {
        Conta conta = new Conta(new Cliente("Bruno"), dinheiro("100.00"));
        repository.salvar(conta);
        long idOriginal = conta.getIdPersistencia();

        conta.depositar(dinheiro("50.25"));
        repository.salvar(conta);

        assertEquals(idOriginal, conta.getIdPersistencia());
        Conta recuperada = repository.buscarPorId(idOriginal).orElseThrow();
        assertEquals(0, dinheiro("150.25").compareTo(recuperada.getSaldo()));
    }

    @Test
    @DisplayName("Conta recuperada do banco continua operando normalmente")
    void contaRecuperadaOpera() {
        Conta conta = new Conta(new Cliente("Carla"), dinheiro("80.00"));
        repository.salvar(conta);

        Conta recuperada = repository.buscarPorId(conta.getIdPersistencia()).orElseThrow();
        recuperada.sacar(dinheiro("30.00"));

        assertEquals(dinheiro("50.00"), recuperada.getSaldo());
    }

    @Test
    @DisplayName("listarTodas inclui as contas salvas")
    void listarTodasIncluiContasSalvas() {
        Conta a = new Conta(new Cliente("Diego"), dinheiro("1.00"));
        Conta b = new Conta(new Cliente("Elisa"), dinheiro("2.00"));
        repository.salvar(a);
        repository.salvar(b);

        List<Conta> todas = repository.listarTodas();

        assertTrue(todas.stream().anyMatch(c -> a.getIdPersistencia().equals(c.getIdPersistencia())));
        assertTrue(todas.stream().anyMatch(c -> b.getIdPersistencia().equals(c.getIdPersistencia())));
    }

    @Test
    @DisplayName("Atualizar conta cujo id não existe no banco lança PersistenciaException")
    void atualizarContaInexistenteFalha() {
        Conta fantasma = Conta.restaurar(Long.MAX_VALUE, new Cliente("Fantasma"), dinheiro("1.00"));

        assertThrows(PersistenciaException.class, () -> repository.salvar(fantasma));
    }
}
