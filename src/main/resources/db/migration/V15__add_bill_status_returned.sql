-- =============================================================================
-- V15__add_bill_status_returned.sql
--
-- Acrescenta, a pedido do usuário (22/09/2026 - nova feature de cancelamento
-- de produtos, ver README/decisoes.md), o valor 8 = "Devolvida"/"RETURNED"
-- em T_DOMAIN_BILL_STATUS. Usado quando um estorno TOTAL é aplicado a uma
-- fatura (T_BILL.REFUND_STATUS muda para BILL_REFUND_FULL_REFUNDED e
-- T_BILL.STATUS acompanha esse valor, sinalizando que o dinheiro voltou
-- integralmente ao assinante) - ver CancellationOrchestrationService.
--
-- Tabela de domínio (nome contém "DOMAIN"): não é auditada, mesma exceção já
-- documentada em V1__create_domain_schema_and_seed_data.sql - só um INSERT
-- simples, sem tocar em T_DOMAIN_BILL_STATUS_AU (que nem existe).
--
-- BACKEND_VALUE já nasce preenchido no INSERT (diferente do padrão em 3
-- passos usado em V4 para as colunas que precisaram de ALTER+UPDATE+MODIFY):
-- aqui é uma linha NOVA sendo inserida, não uma coluna nova numa tabela já
-- populada, então não há necessidade do passo intermediário.
-- =============================================================================

INSERT INTO T_DOMAIN_BILL_STATUS (ID, STATUS_DESC, BACKEND_VALUE)
VALUES (8, 'Devolvida', 'RETURNED');
