-- =============================================================================
-- V11__create_config_parameters.sql
--
-- Cria, a pedido do usuário (21/09/2026 - ver README, seção "Evoluções
-- pedidas"), uma tabela de PARÂMETROS de configuração genéricos - diferente
-- de T_CONFIG_FEATURE_TOGGLE (V10), que só liga/desliga uma regra
-- (booleano), esta guarda um VALOR de texto livre por parâmetro (uma data,
-- um número, uma string - o que a regra que o consome precisar interpretar).
--
-- Primeiro uso: MINIMAL_TRANSACTION_DATE (ver regra 4 do pedido do usuário),
-- que passa a ser o piso aceito para "transactionDt" na criação de uma
-- compra (POST /api/v1/purchases) - ver PurchaseValidationService.validateTransactionDt
-- e ConfigParameterService/ConfigParameterRules.
--
-- Nota de nomenclatura (pedido explícito do usuário): a tabela de auditoria
-- desta tabela chama-se "T_CONFIG_PARAMS_AU" - uma abreviação do nome cheio
-- ("T_CONFIG_PARAMETERS_AU"), diferente da convenção "<nome completo>_AU"
-- usada em todas as demais tabelas de auditoria do projeto. Mantido
-- literalmente como pedido, registrado aqui para quem for procurar a tabela
-- e estranhar a inconsistência de nomenclatura.
--
-- Auditada normalmente (tem "_AU" + triggers), pelo mesmo motivo de
-- T_CONFIG_FEATURE_TOGGLE (V10): o VALUE de um parâmetro é exatamente o tipo
-- de dado que PODE mudar em produção (ex: um administrador ajustando a data
-- mínima aceita), então vale a pena rastrear quem mudou o quê - diferente
-- das tabelas T_DOMAIN_* (listas fixas, não auditadas).
-- =============================================================================

CREATE TABLE T_CONFIG_PARAMETERS
(
    ID                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT             BIGINT       NOT NULL,
    MODIFIED_DT            BIGINT       NOT NULL,
    PARAMETER_NAME         VARCHAR(100) NOT NULL,
    PARAMETER_DESCRIPTION  VARCHAR(500) NOT NULL,
    VALUE                  VARCHAR(200) NOT NULL,
    -- UNIQUE, mesmo não pedido explicitamente: o parâmetro é sempre buscado
    -- por nome (nunca pelo ID técnico) - ver ConfigParameterRepository.
    -- Mesma decisão já tomada para T_CONFIG_FEATURE_TOGGLE.RULE_NAME (V10).
    CONSTRAINT UQ_CONFIG_PARAMETERS_NAME UNIQUE (PARAMETER_NAME)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Parâmetro semeado a pedido do usuário: data mínima de transação aceita
-- pelo sistema, em epoch milissegundos (1767236400000 = 01/01/2026 03:00 UTC).
INSERT INTO T_CONFIG_PARAMETERS (CREATED_DT, MODIFIED_DT, PARAMETER_NAME, PARAMETER_DESCRIPTION, VALUE)
VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED),
        'MINIMAL_TRANSACTION_DATE',
        'Data minima de transacoes aceitas pelo sistema',
        '1767236400000');

CREATE TABLE T_CONFIG_PARAMS_AU
(
    AU_ID                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    AUDIT_DT               BIGINT      NOT NULL,
    COMMAND                VARCHAR(10) NOT NULL,
    ID                     BIGINT,
    CREATED_DT             BIGINT NOT NULL,
    MODIFIED_DT            BIGINT NOT NULL,
    PARAMETER_NAME         VARCHAR(100) NOT NULL,
    PARAMETER_DESCRIPTION  VARCHAR(500) NOT NULL,
    VALUE                  VARCHAR(200) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

DELIMITER $$
CREATE TRIGGER TRG_T_CONFIG_PARAMETERS_AU_UPD
    BEFORE UPDATE ON T_CONFIG_PARAMETERS
    FOR EACH ROW
BEGIN
    INSERT INTO T_CONFIG_PARAMS_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, PARAMETER_NAME, PARAMETER_DESCRIPTION, VALUE)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'UPDATE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.PARAMETER_NAME, OLD.PARAMETER_DESCRIPTION, OLD.VALUE);
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER TRG_T_CONFIG_PARAMETERS_AU_DEL
    BEFORE DELETE ON T_CONFIG_PARAMETERS
    FOR EACH ROW
BEGIN
    INSERT INTO T_CONFIG_PARAMS_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, PARAMETER_NAME, PARAMETER_DESCRIPTION, VALUE)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'DELETE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.PARAMETER_NAME, OLD.PARAMETER_DESCRIPTION, OLD.VALUE);
END$$
DELIMITER ;
