-- Preparação do servidor MariaDB (executar uma vez, como administrador):
--   mysql -u root -p < docs/banco-setup.sql
-- Depois, criar as tabelas (src/main/resources/schema.sql) em cada banco.

CREATE DATABASE IF NOT EXISTS banco
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Banco separado para os testes de integração (nunca aponte os testes para o banco real).
CREATE DATABASE IF NOT EXISTS banco_teste
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
