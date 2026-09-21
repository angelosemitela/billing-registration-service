-- =============================================================================
-- V12__create_product_suspension_cancellation_refund_domains.sql
--
-- Cria, a pedido do usuário (21/09/2026 - ver README, seção "Evoluções
-- pedidas"), as 3 tabelas de domínio (não auditadas - mesma exceção
-- registrada em V1 para tabelas com "DOMAIN" no nome) que suportam os novos
-- campos de T_PRODUCT/T_BILL criados em V13/V14:
--
--   * T_DOMAIN_PRODUCT_SUSPENSION_STATUS -> T_PRODUCT.SUSPENSION_STATUS
--   * T_DOMAIN_PRODUCT_CANCELLATION_STATUS -> T_PRODUCT.CANCELLATION_STATUS
--   * T_DOMAIN_BILL_REFUND_STATUS -> T_BILL.REFUND_STATUS
--
-- Já nascem com a coluna BACKEND_VALUE (diferente de T_DOMAIN_PRODUCT_STATUS
-- e companhia, que só ganharam essa coluna depois, em V4) - por serem
-- tabelas NOVAS, não há necessidade do passo extra de ALTER/UPDATE/MODIFY:
-- o valor certo já nasce junto com a linha.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- T_DOMAIN_PRODUCT_SUSPENSION_STATUS
-- Ver DomainStatus.PRODUCT_SUSPENSION_COMPLIANT.
-- -----------------------------------------------------------------------------
CREATE TABLE T_DOMAIN_PRODUCT_SUSPENSION_STATUS
(
    ID           INT PRIMARY KEY,
    STATUS_DESC  VARCHAR(100) NOT NULL,
    BACKEND_VALUE VARCHAR(30) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO T_DOMAIN_PRODUCT_SUSPENSION_STATUS (ID, STATUS_DESC, BACKEND_VALUE)
VALUES (1, 'Adimplente', 'COMPLIENT'),
       (2, 'Inadimplente', 'DEFAULTER');

-- -----------------------------------------------------------------------------
-- T_DOMAIN_PRODUCT_CANCELLATION_STATUS
-- Ver DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES / PRODUCT_CANCELLATION_SCHEDULED.
-- -----------------------------------------------------------------------------
CREATE TABLE T_DOMAIN_PRODUCT_CANCELLATION_STATUS
(
    ID           INT PRIMARY KEY,
    STATUS_DESC  VARCHAR(100) NOT NULL,
    BACKEND_VALUE VARCHAR(30) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO T_DOMAIN_PRODUCT_CANCELLATION_STATUS (ID, STATUS_DESC, BACKEND_VALUE)
VALUES (1, 'Sem cancelamentos previstos', 'NO_SCHEDULES'),
       (2, 'Cancelamento agendado', 'SCHEDULED'),
       (3, 'Cancelado', 'CANCELLED');

-- -----------------------------------------------------------------------------
-- T_DOMAIN_BILL_REFUND_STATUS
-- Ver DomainStatus.BILL_REFUND_NO_REFUND.
-- -----------------------------------------------------------------------------
CREATE TABLE T_DOMAIN_BILL_REFUND_STATUS
(
    ID           INT PRIMARY KEY,
    STATUS_DESC  VARCHAR(100) NOT NULL,
    BACKEND_VALUE VARCHAR(30) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO T_DOMAIN_BILL_REFUND_STATUS (ID, STATUS_DESC, BACKEND_VALUE)
VALUES (1, 'Sem estornos', 'NO_REFUND'),
       (2, 'Estornado parcialmente', 'PART_REFUNDED'),
       (3, 'Estornado totalmente', 'FULL_REFUNDED');
