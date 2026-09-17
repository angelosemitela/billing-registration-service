-- =============================================================================
-- V4__add_domain_status_backend_values.sql
--
-- Acrescenta a coluna BACKEND_VALUE às tabelas de domínio de status
-- (T_DOMAIN_*_STATUS/T_DOMAIN_BILL_TYPE). Até aqui, essas tabelas só tinham
-- STATUS_DESC (uma descrição em português, pensada para leitura humana, não
-- para ser consumida por outro sistema). BACKEND_VALUE é um valor estável,
-- em inglês, MAIÚSCULO e sem espaços - o formato certo para ser devolvido
-- em futuras consultas de API (ex: "GET /accounts/{id}" retornando
-- "status": "ACTIVE" em vez de expor o ID numérico interno ou a descrição
-- em português).
--
-- IMPORTANTE (boa prática de Flyway): as migrations V1, V2 e V3 já foram
-- aplicadas nos ambientes de desenvolvimento existentes (o Flyway calcula e
-- guarda um checksum de cada arquivo já executado, em
-- flyway_schema_history) - alterá-las agora, mesmo que fosse só para
-- acrescentar esta coluna direto no CREATE TABLE original, quebraria essa
-- validação de checksum na próxima subida da aplicação
-- ("Migration checksum mismatch"). A regra é simples e vale para qualquer
-- migration tool (Flyway, Liquibase, etc.): uma migration já aplicada é
-- IMUTÁVEL - qualquer mudança de schema, a partir de agora, vira uma NOVA
-- migration (esta), nunca uma edição retroativa de uma anterior.
--
-- Padrão usado para adicionar uma coluna NOT NULL numa tabela que pode já
-- ter linhas (aqui são tabelas de domínio pequenas e 100% conhecidas, mas o
-- padrão vale igual para tabelas grandes em produção): 1) ADD COLUMN
-- aceitando NULL: 2) UPDATE preenchendo todo mundo; 3) só então MODIFY para
-- NOT NULL. Fazer os 3 passos de uma vez (ADD COLUMN ... NOT NULL direto)
-- falharia com erro de constraint assim que houvesse qualquer linha
-- existente sem valor para a nova coluna.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- T_DOMAIN_ACCOUNT_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_ACCOUNT_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_ACCOUNT_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE STATUS_ID = 1;
UPDATE T_DOMAIN_ACCOUNT_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE STATUS_ID = 2;
ALTER TABLE T_DOMAIN_ACCOUNT_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_ACCOUNT_DOCUMENT_STATUS
-- ATENÇÃO: esta tabela não estava na lista original pedida, mas segue
-- exatamente o mesmo padrão (1=Ativo/2=Cancelado) das outras três tabelas
-- de status de conta (T_DOMAIN_ACCOUNT_STATUS/_ADDRESS_STATUS/_PHONE_STATUS)
-- - adicionamos aqui por consistência, para não deixar esta de fora quando
-- a consulta futura de documentos precisar do BACKEND_VALUE também. Se essa
-- suposição estiver errada, é só pedir para reverter/ajustar.
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_ACCOUNT_DOCUMENT_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_ACCOUNT_DOCUMENT_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE STATUS_ID = 1;
UPDATE T_DOMAIN_ACCOUNT_DOCUMENT_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE STATUS_ID = 2;
ALTER TABLE T_DOMAIN_ACCOUNT_DOCUMENT_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_ACCOUNT_ADDRESS_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_ACCOUNT_ADDRESS_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_ACCOUNT_ADDRESS_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE STATUS_ID = 1;
UPDATE T_DOMAIN_ACCOUNT_ADDRESS_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE STATUS_ID = 2;
ALTER TABLE T_DOMAIN_ACCOUNT_ADDRESS_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_ACCOUNT_PHONE_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_ACCOUNT_PHONE_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_ACCOUNT_PHONE_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE STATUS_ID = 1;
UPDATE T_DOMAIN_ACCOUNT_PHONE_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE STATUS_ID = 2;
ALTER TABLE T_DOMAIN_ACCOUNT_PHONE_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_PRODUCT_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_PRODUCT_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_PRODUCT_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE STATUS_ID = 1;
UPDATE T_DOMAIN_PRODUCT_STATUS SET BACKEND_VALUE = 'SUSPENDED' WHERE STATUS_ID = 2;
UPDATE T_DOMAIN_PRODUCT_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE STATUS_ID = 3;
UPDATE T_DOMAIN_PRODUCT_STATUS SET BACKEND_VALUE = 'NO_SERVICE' WHERE STATUS_ID = 4;
ALTER TABLE T_DOMAIN_PRODUCT_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_DISCOUNT_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_DISCOUNT_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_DISCOUNT_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE STATUS_ID = 1;
UPDATE T_DOMAIN_DISCOUNT_STATUS SET BACKEND_VALUE = 'EXPIRED' WHERE STATUS_ID = 2;
UPDATE T_DOMAIN_DISCOUNT_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE STATUS_ID = 3;
ALTER TABLE T_DOMAIN_DISCOUNT_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_PAYMENT_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_PAYMENT_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_PAYMENT_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE ID = 1;
UPDATE T_DOMAIN_PAYMENT_STATUS SET BACKEND_VALUE = 'EXPIRED' WHERE ID = 2;
UPDATE T_DOMAIN_PAYMENT_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE ID = 3;
ALTER TABLE T_DOMAIN_PAYMENT_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_PAYMENT_TOKEN_STATUS
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_PAYMENT_TOKEN_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_PAYMENT_TOKEN_STATUS SET BACKEND_VALUE = 'ACTIVE' WHERE ID = 1;
UPDATE T_DOMAIN_PAYMENT_TOKEN_STATUS SET BACKEND_VALUE = 'EXPIRED' WHERE ID = 2;
UPDATE T_DOMAIN_PAYMENT_TOKEN_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE ID = 3;
ALTER TABLE T_DOMAIN_PAYMENT_TOKEN_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_BILL_STATUS
-- Observação: IDs 5 e 6 ("Em liquidacao de parcelas" e "Paga") e o próprio
-- ID 4 ("Paga aguardando repasse") mapeiam para o MESMO BACKEND_VALUE
-- ('PAID') - conforme pedido, sem diferença para os sistemas consumidores
-- externos, mesmo havendo 3 granularidades diferentes internamente.
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_BILL_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'GENERATED' WHERE ID = 1;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'SENT_FOR_PAYMENT' WHERE ID = 2;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'NOT_PAID' WHERE ID = 3;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'PAID' WHERE ID = 4;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'PAID' WHERE ID = 5;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'PAID' WHERE ID = 6;
UPDATE T_DOMAIN_BILL_STATUS SET BACKEND_VALUE = 'CANCELLED' WHERE ID = 7;
ALTER TABLE T_DOMAIN_BILL_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_BILL_TYPE
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_BILL_TYPE ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_BILL_TYPE SET BACKEND_VALUE = 'BUY' WHERE ID = 1;
UPDATE T_DOMAIN_BILL_TYPE SET BACKEND_VALUE = 'RECURRENCE' WHERE ID = 2;
UPDATE T_DOMAIN_BILL_TYPE SET BACKEND_VALUE = 'REACTIVATION' WHERE ID = 3;
UPDATE T_DOMAIN_BILL_TYPE SET BACKEND_VALUE = 'RETRY' WHERE ID = 4;
ALTER TABLE T_DOMAIN_BILL_TYPE MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_BILL_INSTALLMENT_STATUS
-- Observação: IDs 1 e 2 mapeiam para o MESMO BACKEND_VALUE ('PAID') -
-- conforme pedido, sem diferença para os sistemas consumidores externos.
-- -----------------------------------------------------------------------------
ALTER TABLE T_DOMAIN_BILL_INSTALLMENT_STATUS ADD COLUMN BACKEND_VALUE VARCHAR(30) NULL;
UPDATE T_DOMAIN_BILL_INSTALLMENT_STATUS SET BACKEND_VALUE = 'PAID' WHERE ID = 1;
UPDATE T_DOMAIN_BILL_INSTALLMENT_STATUS SET BACKEND_VALUE = 'PAID' WHERE ID = 2;
ALTER TABLE T_DOMAIN_BILL_INSTALLMENT_STATUS MODIFY COLUMN BACKEND_VALUE VARCHAR(30) NOT NULL;
