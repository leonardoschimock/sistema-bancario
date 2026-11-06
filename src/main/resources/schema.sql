-- Schema do Sistema Bancário (MySQL 8.0.16+).
-- Idempotente: pode ser executado mais de uma vez.
-- Execute DENTRO do banco já criado (veja docs/banco-setup.sql):
--   mysql -u root -p banco < src/main/resources/schema.sql

CREATE TABLE IF NOT EXISTS contas (
                                      id            BIGINT        NOT NULL AUTO_INCREMENT,
                                      titular_nome  VARCHAR(150)  NOT NULL,
                                      saldo         DECIMAL(19,2) NOT NULL,
                                      PRIMARY KEY (id),
                                      CONSTRAINT ck_contas_saldo CHECK (saldo >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- conta_id         : conta dona do lançamento (depósito/saque) ou ORIGEM da transferência.
-- conta_destino_id : preenchida somente em transferências (conta de DESTINO).
-- tipo             : nome do enum domain.TipoTransacao (DEPOSITO, SAQUE, TRANSFERENCIA).
CREATE TABLE IF NOT EXISTS transacoes (
                                          id                BIGINT        NOT NULL AUTO_INCREMENT,
                                          conta_id          BIGINT        NOT NULL,
                                          conta_destino_id  BIGINT        NULL,
                                          tipo              VARCHAR(20)   NOT NULL,
                                          valor             DECIMAL(19,2) NOT NULL,
                                          horario           DATETIME(6)   NOT NULL,
                                          PRIMARY KEY (id),
                                          CONSTRAINT fk_transacoes_conta   FOREIGN KEY (conta_id)         REFERENCES contas (id),
                                          CONSTRAINT fk_transacoes_destino FOREIGN KEY (conta_destino_id) REFERENCES contas (id),
                                          CONSTRAINT ck_transacoes_valor   CHECK (valor > 0),
                                          INDEX idx_transacoes_conta   (conta_id, horario),
                                          INDEX idx_transacoes_destino (conta_destino_id, horario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
