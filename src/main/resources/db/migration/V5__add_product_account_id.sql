-- =============================================================================
-- V5__add_product_account_id.sql
--
-- Acrescenta T_PRODUCT.ACCOUNT_ID: até aqui, T_PRODUCT não tinha NENHUMA
-- coluna ligando um produto vendido à conta (assinante) que o comprou - a
-- associação só existia "de passagem", dentro da mesma requisição HTTP (o
-- Map<codeId, ProductEntity> usado em memória por PurchaseOrchestrationService
-- para casar account/product/billing durante o processamento). Sem
-- ACCOUNT_ID persistido, seria impossível, numa consulta futura ou no job de
-- recorrência, responder "quais produtos pertencem à conta X" com uma
-- simples consulta ao banco.
--
-- ATENÇÃO - por que isso exige resetar o banco local: T_PRODUCT já tem pelo
-- menos 1 linha (do primeiro teste end-to-end feito via curl). Como
-- ACCOUNT_ID é NOT NULL e não existe um valor correto para "adivinhar" para
-- linhas antigas de forma genérica, a migration abaixo assume a tabela
-- vazia. Antes de rodar `mvn spring-boot:run` com esta migration, resete o
-- banco de desenvolvimento (mesmo passo já usado outras vezes nesta sessão):
--   docker exec -it billing-mysql mysql -u root -proot \
--     -e "DROP DATABASE billing_db; CREATE DATABASE billing_db;"
-- (sem problema nenhum, é banco de estudo/dev, sem dados reais).
-- =============================================================================

ALTER TABLE T_PRODUCT
    ADD COLUMN ACCOUNT_ID BIGINT NOT NULL,
    ADD CONSTRAINT FK_PRODUCT_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES T_ACCOUNT (ID),
    ADD INDEX IDX_PRODUCT_ACCOUNT (ACCOUNT_ID);

-- Mantém a tabela de auditoria _AU (e as triggers que a alimentam)
-- sincronizadas com a nova coluna - ver a "pegadinha extra" já documentada
-- no README sobre esse mesmo tipo de esquecimento (item T_LOG/T_LOG_AU):
-- toda mudança estrutural na tabela original precisa ser replicada
-- manualmente na tabela _AU correspondente, não existe sincronização
-- automática entre as duas.
ALTER TABLE T_PRODUCT_AU
    ADD COLUMN ACCOUNT_ID BIGINT NULL;

-- Triggers não suportam "ALTER" no MySQL - a única forma de mudar a lista de
-- colunas que uma trigger grava é derrubá-la e recriá-la do zero.
DROP TRIGGER TRG_T_PRODUCT_AU_UPD;
DROP TRIGGER TRG_T_PRODUCT_AU_DEL;

DELIMITER $$
CREATE TRIGGER TRG_T_PRODUCT_AU_UPD
    BEFORE UPDATE ON T_PRODUCT
    FOR EACH ROW
BEGIN
    INSERT INTO T_PRODUCT_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, ACCOUNT_ID, PRODUCT_ID, NAME, TYPE, EXP_SERV_B, RECURRENCE_FREQUENCY, VALUE, CURRENCY, TRIAL_B, TRIAL_DAYS, ASSET_ID, NEXT_BILL_DT, FUTURE_BILL_DT, CYCLE_START_DT, CYCLE_END_DT, DEFAULT_PAYMENT_ID, CHANNEL, TRANSACTION_DT, STATUS)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'UPDATE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.ACCOUNT_ID, OLD.PRODUCT_ID, OLD.NAME, OLD.TYPE, OLD.EXP_SERV_B, OLD.RECURRENCE_FREQUENCY, OLD.VALUE, OLD.CURRENCY, OLD.TRIAL_B, OLD.TRIAL_DAYS, OLD.ASSET_ID, OLD.NEXT_BILL_DT, OLD.FUTURE_BILL_DT, OLD.CYCLE_START_DT, OLD.CYCLE_END_DT, OLD.DEFAULT_PAYMENT_ID, OLD.CHANNEL, OLD.TRANSACTION_DT, OLD.STATUS);
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER TRG_T_PRODUCT_AU_DEL
    BEFORE DELETE ON T_PRODUCT
    FOR EACH ROW
BEGIN
    INSERT INTO T_PRODUCT_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, ACCOUNT_ID, PRODUCT_ID, NAME, TYPE, EXP_SERV_B, RECURRENCE_FREQUENCY, VALUE, CURRENCY, TRIAL_B, TRIAL_DAYS, ASSET_ID, NEXT_BILL_DT, FUTURE_BILL_DT, CYCLE_START_DT, CYCLE_END_DT, DEFAULT_PAYMENT_ID, CHANNEL, TRANSACTION_DT, STATUS)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'DELETE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.ACCOUNT_ID, OLD.PRODUCT_ID, OLD.NAME, OLD.TYPE, OLD.EXP_SERV_B, OLD.RECURRENCE_FREQUENCY, OLD.VALUE, OLD.CURRENCY, OLD.TRIAL_B, OLD.TRIAL_DAYS, OLD.ASSET_ID, OLD.NEXT_BILL_DT, OLD.FUTURE_BILL_DT, OLD.CYCLE_START_DT, OLD.CYCLE_END_DT, OLD.DEFAULT_PAYMENT_ID, OLD.CHANNEL, OLD.TRANSACTION_DT, OLD.STATUS);
END$$
DELIMITER ;
