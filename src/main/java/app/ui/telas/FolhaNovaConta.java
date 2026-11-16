package app.ui.telas;

import app.ui.ControladorBanco;
import app.ui.ControladorBanco.Tipo;
import app.ui.EstadoApp;
import app.ui.tema.Componentes;
import app.ui.tema.Componentes.Botao;
import app.ui.tema.Componentes.CampoTexto;
import app.ui.tema.Componentes.CampoValor;
import app.ui.tema.Componentes.Estilo;
import app.ui.tema.Componentes.Folha;
import app.ui.tema.Componentes.Pilha;
import app.ui.tema.Icones.Icone;
import app.ui.tema.Tema.Cor;
import app.ui.tema.Tema.Fonte;
import app.ui.tema.Tema.Medida;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Folha "Nova conta" (compartilhada por Início e Contas): nome do titular e saldo inicial.
 * Cria pelo {@link ControladorBanco} (em SwingWorker) e, confirmada a gravação, inclui e seleciona a conta.
 */
final class FolhaNovaConta {

    private FolhaNovaConta() {
    }

    static void abrir(Component dono, ControladorBanco controlador, EstadoApp estado) {
        CampoTexto nome = Componentes.campoTexto("Nome do titular", "Ex.: Maria Silva");
        CampoValor saldo = Componentes.campoValor("Saldo inicial");
        Botao criar = Componentes.botao("Criar conta", null, Estilo.PRIMARIO);
        Botao cancelar = Componentes.botao("Cancelar", null, Estilo.FANTASMA);

        Pilha corpo = Componentes.pilha();
        corpo.adicionar(Componentes.rotulo("Nova conta", Fonte.SECAO, Cor.TEXTO)).espaco(Medida.ESP_4);
        corpo.adicionar(nome).espaco(Medida.ESP_4);
        corpo.adicionar(saldo).espaco(Medida.ESP_5);
        JPanel acoes = new JPanel(new GridLayout(1, 2, Medida.ESP_3, 0));
        acoes.setOpaque(false);
        acoes.add(cancelar);
        acoes.add(criar);
        corpo.adicionar(acoes);

        Folha folha = Componentes.folha(dono, corpo);
        cancelar.addActionListener(e -> folha.fechar());
        Runnable enviar = () -> {
            nome.limparErro();
            saldo.limparErro();
            ocupado(true, nome, saldo, criar, cancelar);
            controlador.criarConta(nome.texto(), saldo.valor(), resultado -> {
                try {
                    if (resultado.ok()) {
                        folha.fechar();
                        estado.adicionarESelecionar(resultado.valor());
                        return;
                    }
                    ControladorBanco.Falha falha = resultado.falha();
                    if (falha.tipo() == Tipo.NOME) {
                        nome.mostrarErro(falha.oQueAconteceu());
                    } else if (falha.tipo() == Tipo.SALDO_INICIAL) {
                        saldo.mostrarErro(falha.oQueAconteceu() + " " + falha.oQueFazer());
                    } else {
                        Componentes.mostrarErro(dono, falha.recusa() ? Icone.ALERTA : Icone.ERRO, falha.titulo(),
                                falha.oQueAconteceu(), falha.oQueFazer(), falha.detalheTecnico());
                    }
                } finally {
                    ocupado(false, nome, saldo, criar, cancelar);
                }
            });
        };
        criar.addActionListener(e -> enviar.run());
        nome.aoConfirmar(enviar);
        saldo.aoConfirmar(enviar);
        SwingUtilities.invokeLater(nome::requestFocusInWindow);
        folha.mostrar();
    }

    private static void ocupado(boolean ocupado, CampoTexto nome, CampoValor saldo, Botao criar, Botao cancelar) {
        nome.setEnabled(!ocupado);
        saldo.setEnabled(!ocupado);
        criar.setEnabled(!ocupado);
        cancelar.setEnabled(!ocupado);
    }
}
