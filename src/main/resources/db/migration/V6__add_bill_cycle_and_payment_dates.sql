-- =============================================================================
-- V6__add_bill_cycle_and_payment_dates.sql
--
-- Acrescenta a T_BILL as datas de vigência e de pagamento da fatura:
--
--   * CYCLE_START_DT / CYCLE_END_DT: vigência do ciclo ao qual esta fatura
--     se refere - mesma regra já usada em T_PRODUCT (CYCLE_START_DT/
--     CYCLE_END_DT), simplesmente copiada do produto correspondente no
--     momento da criação da fatura (ver BillingService.persistBillings).
--     CYCLE_END_DT fica NULL quando o produto não tem controle de vigência
--     (isExpiriationService = false), espelhando o mesmo comportamento de
--     T_PRODUCT.CYCLE_END_DT.
--   * DUE_DT: data de vencimento do pagamento. No fluxo de criação via
--     "POST /api/v1/purchases", usamos o mesmo valor de TRANSACTION_DT (não
--     há, hoje, nenhuma regra de prazo de vencimento diferente disso -
--     futuras evoluções podem querer somar N dias, mas isso fica fora do
--     escopo desta v1).
--   * PAYMENT_DT: data em que o repasse do pagamento foi de fato
--     efetivado. Sempre NULL quando a fatura é criada por
--     "POST /api/v1/purchases" - será preenchido futuramente por um
--     processo que muda o STATUS da fatura para "6 - Paga" (ver
--     DomainStatus/T_DOMAIN_BILL_STATUS).
--
-- Mesma ressalva do V5: T_BILL não fica NOT NULL "de graça" numa tabela que
-- já pode ter linha(s) do teste anterior - resete o banco local
-- (DROP DATABASE billing_db; CREATE DATABASE billing_db;) antes de rodar
-- esta migration.
-- =============================================================================

ALTER TABLE T_BILL
    ADD COLUMN CYCLE_START_DT BIGINT NOT NULL,
    ADD COLUMN CYCLE_END_DT BIGINT NULL,
    ADD COLUMN DUE_DT BIGINT NOT NULL,
    ADD COLUMN PAYMENT_DT BIGINT NULL;

-- Tabela de auditoria + triggers, sincronizadas com a nova estrutura (ver
-- comentário equivalente em V5__add_product_account_id.sql).
ALTER TABLE T_BILL_AU
    ADD COLUMN CYCLE_START_DT BIGINT NULL,
    ADD COLUMN CYCLE_END_DT BIGINT NULL,
    ADD COLUMN DUE_DT BIGINT NULL,
    ADD COLUMN PAYMENT_DT BIGINT NULL;

DROP TRIGGER TRG_T_BILL_AU_UPD;
DROP TRIGGER TRG_T_BILL_AU_DEL;

DELIMITER $$
CREATE TRIGGER TRG_T_BILL_AU_UPD
    BEFORE UPDATE ON T_BILL
    FOR EACH ROW
BEGIN
    INSERT INTO T_BILL_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, CODE_ID, PRODUCT_ID, PAYMENT_ID, PRODUCT_VALUE, DISCOUNT_VALUE, TAX_VALUE, CHARGED_VALUE, CURRENCY, TRANSACTION_ID, INSTALLMENTS, PROVIDER, PAYMENT_METHOD, STATUS, BILL_TYPE, CYCLE_START_DT, CYCLE_END_DT, DUE_DT, PAYMENT_DT)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'UPDATE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.CODE_ID, OLD.PRODUCT_ID, OLD.PAYMENT_ID, OLD.PRODUCT_VALUE, OLD.DISCOUNT_VALUE, OLD.TAX_VALUE, OLD.CHARGED_VALUE, OLD.CURRENCY, OLD.TRANSACTION_ID, OLD.INSTALLMENTS, OLD.PROVIDER, OLD.PAYMENT_METHOD, OLD.STATUS, OLD.BILL_TYPE, OLD.CYCLE_START_DT, OLD.CYCLE_END_DT, OLD.DUE_DT, OLD.PAYMENT_DT);
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER TRG_T_BILL_AU_DEL
    BEFORE DELETE ON T_BILL
    FOR EACH ROW
BEGIN
    INSERT INTO T_BILL_AU (AUDIT_DT, COMMAND, ID, CREATED_DT, MODIFIED_DT, CODE_ID, PRODUCT_ID, PAYMENT_ID, PRODUCT_VALUE, DISCOUNT_VALUE, TAX_VALUE, CHARGED_VALUE, CURRENCY, TRANSACTION_ID, INSTALLMENTS, PROVIDER, PAYMENT_METHOD, STATUS, BILL_TYPE, CYCLE_START_DT, CYCLE_END_DT, DUE_DT, PAYMENT_DT)
    VALUES (CAST(UNIX_TIMESTAMP(NOW(3)) * 1000 AS UNSIGNED), 'DELETE', OLD.ID, OLD.CREATED_DT, OLD.MODIFIED_DT, OLD.CODE_ID, OLD.PRODUCT_ID, OLD.PAYMENT_ID, OLD.PRODUCT_VALUE, OLD.DISCOUNT_VALUE, OLD.TAX_VALUE, OLD.CHARGED_VALUE, OLD.CURRENCY, OLD.TRANSACTION_ID, OLD.INSTALLMENTS, OLD.PROVIDER, OLD.PAYMENT_METHOD, OLD.STATUS, OLD.BILL_TYPE, OLD.CYCLE_START_DT, OLD.CYCLE_END_DT, OLD.DUE_DT, OLD.PAYMENT_DT);
END$$
DELIMITER ;
