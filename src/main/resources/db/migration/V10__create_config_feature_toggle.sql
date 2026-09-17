-- =============================================================================
-- V10__create_config_feature_toggle.sql
--
-- Cria a infraestrutura de "feature toggle" pedida pelo usuário (17/09/2026 -
-- ver README, seção "Evoluções pedidas"): uma tabela de configuração que liga
-- ou desliga, em runtime (sem novo deploy), regras de negócio condicionais.
--
-- Nota de nomenclatura importante: T_CONFIG_FEATURE_TOGGLE NÃO tem a palavra
-- "DOMAIN" no nome, então NÃO se enquadra na exceção de auditoria descrita em
-- V1 ("tabelas de domínio... não fazem sentido auditar, pois não mudam por
-- ação do usuário final"). Aqui é o oposto: o valor de STATUS_B é exatamente
-- o tipo de dado que PODE mudar em produção (ex: um administrador desligando
-- uma regra) - e o usuário pediu EXPLICITAMENTE a tabela _AU + triggers nos
-- mesmos moldes das demais tabelas de aplicação. Por isso T_CONFIG_FEATURE_TOGGLE
-- segue o padrão normal (ID/CREATED_DT/MODIFIED_DT + tabela _AU + 2 triggers),
-- em vez do padrão mais simples das T_DOMAIN_* (sem auditoria).
-- =============================================================================

CREATE TABLE T_CONFIG_FEATURE_TOGGLE
(
    ID               BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT       BIGINT       NOT NULL,
    MODIFIED_DT      BIGINT       NOT NULL,
    RULE_NAME        VARCHAR(100) NOT NULL,
    RULE_DESCRIPTION VARCHAR(500) NOT NULL,
    STATUS_B         CHAR(1)      NOT NULL,
    CONSTRAINT UQ_CONFIG_FEATURE_TOGGLE_RULE_NAME UNIQUE (RULE_NAME)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- Regra semeada a pedido do usuário: agenda automaticamente o cancelamento
-- de produtos ONESHOT com vigência (ver ProductService.persistProducts).
-- Nasce LIGADA ('1'), conforme especificado.
INSERT INTO T_CONFIG_FEATURE_TOGGLE (CREATED_DT, MODIFIED_DT, ID, RULE_NAME, RULE_DESCRIPTION, STATUS_B)
VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 1,
        'AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT',
        'Agenda o cancelamento automatico para assinantes que possuem servico associado em compras One Shot',
        '1');

CREATE TABLE T_CONFIG_FEATURE_TOGGLE_AU
(
    AU_ID            BIGINT AUTO_INCREMENT PRIMARY KEY,
    AUDIT_DT         BIGINT      NOT NULL,
    COMMAND          VARCHAR(10) NOT NULL,
    ID               BIGINT,
    CREATED_DT       BIGINT NOT NULL,
    MODIFIED_DT      BIGINT NOT NULL,
    RULE_NAME        VARCHAR(100) NOT NULL,
    RULE_DESCRIPTION VARCHAR(500) NOT NULL,
    STATUS_B         CHAR(1) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

DELIMITER $$
CREATE TRIGGER TRG_T_CONFIG_FEATURE_TOGGLE_AU_UPD
    BEFORE UPDATE ON T_CONFIG_FEATURE_TOGGLE
    FOR EACH ROW
BEGIN
    INSERT INTO T_CONFIG_FEATURE_TOGGLE_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, RULE_NAME, RULE_DESCRIPTION, STATUS_B)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'UPDATE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.RULE_NAME, OLD.RULE_DESCRIPTION, OLD.STATUS_B);
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER TRG_T_CONFIG_FEATURE_TOGGLE_AU_DEL
    BEFORE DELETE ON T_CONFIG_FEATURE_TOGGLE
    FOR EACH ROW
BEGIN
    INSERT INTO T_CONFIG_FEATURE_TOGGLE_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, RULE_NAME, RULE_DESCRIPTION, STATUS_B)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'DELETE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.RULE_NAME, OLD.RULE_DESCRIPTION, OLD.STATUS_B);
END$$
DELIMITER ;
