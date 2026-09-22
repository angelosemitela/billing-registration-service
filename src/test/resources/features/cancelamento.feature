# language: pt
# Feature pedida pelo usuário em 22/09/2026, a partir de uma planilha própria
# ("Teste cancelamento") com 12 cenários de erro e 4 de sucesso para
# POST /api/v1/purchases/cancel. Ver a seção correspondente do addendum do
# projeto (claude/decisoes-adendo-2026-09-22-cancelamento.md) para o relato
# completo de como cada linha da planilha foi traduzida para os passos
# abaixo, incluindo as correções que ela precisou (mesmo padrão já registrado
# em decisoes.md para registro-e-consulta-de-compra.feature) - resumo aqui:
#
#   1) product[0].isExpiriationService=true - em TODOS os cenários. Sem essa
#      mudança, o produto da massa padrão (compra-base.json tem
#      isExpiriationService=false) nasce com status "SOLD_WITHOUT_SERVICE"
#      (nem ACTIVE nem SUSPENDED), e NENHUM tipo de cancelamento IMMEDIATE
#      seria elegível (ver CancellationValidationService.validateImmediateEligibility)
#      - a planilha original não previa esse ajuste, mas ele é uma
#      pré-condição estrutural para qualquer cenário chegar perto da regra
#      que está de fato sendo testada.
#
#   2) Cenário "SCHEDULED" (linha 14 da planilha): usa product[0].type=RECURRENCE
#      (em vez de ONESHOT) além do isExpiriationService=true acima. Motivo:
#      com type=ONESHOT + isExpiriationService=true, a regra
#      AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT (ligada por padrão - ver
#      V10__create_config_feature_toggle.sql) agenda um cancelamento
#      AUTOMATICAMENTE na própria criação do produto, deixando-o com
#      cancellationStatus=SCHEDULED antes mesmo do teste rodar - e
#      CancellationValidationService.validateScheduledEligibility exige
#      cancellationStatus=NO_SCHEDULES para aceitar um NOVO agendamento.
#      Usar type=RECURRENCE (com recurrenceFrequency=MONTH, já que o campo é
#      obrigatório para esse tipo) contorna esse auto-agendamento sem
#      desligar a feature toggle globalmente (o que afetaria outras suítes).
#
#   3) Linha "TYPE" com valor "XYZ" (planilha pede reason contendo "type"):
#      um valor de enum fora do domínio nunca chega à camada de validação de
#      negócio (o Jackson falha antes, na desserialização - mesmo
#      comportamento já documentado em erros-de-validacao.feature) - a
#      mensagem de erro do Jackson cita o NOME DA CLASSE do enum, não a
#      palavra "type". Por isso esta linha usa "CancellationType" como trecho
#      esperado (mesmo critério já usado por TODAS as outras linhas de enum
#      inválido em erros-de-validacao.feature, ex: "ProductType",
#      "PaymentMethod") - mais preciso do que testar a palavra genérica
#      "type", que também apareceria em qualquer outra mensagem de erro de
#      desserialização, não seria uma asserção específica desta regra.
#
#   4) Linha "HASREFUND": a planilha repete por engano "type=XYZ" na coluna
#      de entrada (copiado da linha anterior). A ÚNICA mensagem de negócio
#      que contém a palavra "hasRefund" é "refund is required and must
#      contain at least one item when hasRefund is true" - disparada quando
#      hasRefund=true (valor padrão do payload) e o campo "refund" está
#      ausente. É essa combinação que a linha "refund | (ausente)" abaixo
#      exercita.
#
#   5) Cenário 14 (SCHEDULED) - campos PRODUCTST/CANCELLATIONST: a coluna
#      "Valor" da planilha traz "CANCELLED"/"CANCELLED" para as duas linhas,
#      mas a coluna "Observação" da MESMA planilha diz "Valor fixo ACTIVE" e
#      "Valor fixo SCHEDULED" (claramente um copia-e-cola das linhas do
#      cenário 13 anterior, que É de fato um cancelamento IMMEDIATE). O
#      código confirma a Observação: CancellationOrchestrationService.applyScheduled
#      NUNCA altera product.status (só IMMEDIATE faz isso, em applyImmediate) -
#      um produto com cancelamento agendado continua ACTIVE até o ciclo
#      efetivamente terminar. Implementado aqui como ACTIVE/SCHEDULED.
#
#   6) Cenário 15 (estorno parcial de "10"): a planilha espera
#      refund[0].updatedValue=90.00, mas billing[0].chargedValue da massa
#      padrão é 110.00 (110 - 10 = 100.00, não 90.00) - sem nenhuma outra
#      regra de negócio no meio (CancellationOrchestrationService.applyRefund
#      é uma subtração direta chargedValue - refundValue). Implementado aqui
#      com o valor correto (100.00); registrado como divergência a confirmar
#      com o usuário.
#
#   7) refundSt "FULL_REFUND"/"PART_REFUND" (planilha) -> os valores
#      realmente semeados em T_DOMAIN_BILL_REFUND_STATUS (ver
#      V12__create_product_suspension_cancellation_refund_domains.sql) são
#      "FULL_REFUNDED"/"PART_REFUNDED" (com o sufixo "ED") - corrigido aqui
#      para o valor real.

Funcionalidade: Cancelamento de produto (POST /api/v1/purchases/cancel)
  Como consumidor da API de faturamento
  Quero cancelar (imediatamente, agendado, ou desistir de um agendamento) um produto já comprado, com estorno opcional
  Para confirmar que a API valida corretamente a entrada e processa o cancelamento conforme as regras de CancellationValidationService/CancellationOrchestrationService

  Esquema do Cenário: Uma requisição de cancelamento inválida é rejeitada com o status e o motivo corretos
    Dado uma requisição de compra válida com protocolo e externalId únicos
    E o campo "product[0].isExpiriationService" da requisição é definido como "true"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a massa de cancelamento é preparada a partir da compra recém-registrada
    E o campo "<campo>" da requisição de cancelamento é definido como "<valor>"
    Quando o cancelamento é solicitado via POST /api/v1/purchases/cancel
    Então a resposta de cancelamento deve ter status HTTP <statusEsperado> e result "ERROR"
    E a razão da resposta de cancelamento deve conter "<trechoEsperado>"

    Exemplos:
      | campo             | valor                 | statusEsperado | trechoEsperado   |
      | transactionDt     | (nulo)                | 400            | transactionDt    |
      | transactionDt     | 1                     | 400            | transactionDt    |
      | transactionDt     | 1767236400001         | 400            | transactionDt    |
      | type              | (nulo)                | 400            | type             |
      | type              | XYZ                   | 400            | CancellationType |
      | type              | WITHDRAW_CANCELLATION | 400            | cancellation     |
      | refund            | (ausente)             | 400            | hasRefund        |
      | refund[0].billId  | (nulo)                | 404            | refund.billId    |
      | refund[0].billId  | 12345                 | 404            | refund.billId    |
      | refund[0].amount  | (nulo)                | 400            | refund.amount    |
      | refund[0].amount  | 0                     | 400            | refund.amount    |
      | refund[0].amount  | -1                    | 400            | refund.amount    |
      | refund[0].amount  | -99999                | 400            | refund.amount    |

  Cenário: Sucesso - cancelamento IMMEDIATE com estorno total da fatura
    Dado uma requisição de compra válida com protocolo e externalId únicos
    E o campo "product[0].isExpiriationService" da requisição é definido como "true"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a massa de cancelamento é preparada a partir da compra recém-registrada
    Quando o cancelamento é solicitado via POST /api/v1/purchases/cancel
    Então a resposta de cancelamento deve ter status HTTP 200 e result "SUCCESS"
    E os campos abaixo devem corresponder entre a requisição de cancelamento e a resposta de cancelamento:
      | entidade             | entrada.purchases/cancel | saída.purchases/cancel | valor         |
      | RESULT               | null                     | result                 | "SUCCESS"     |
      | CODE                 | null                     | code                   | 200           |
      | REASON               | null                     | reason                 | "Success"     |
      | PROTOCOL             | protocolId               | protocol               | Igual         |
      | PRODUCTID            | productId                | productId              | Igual         |
      | TYPE                 | type                     | inputType              | Igual         |
      | PROCESSEDTYPE        | type                     | processedType          | Igual         |
      | REFUND.BILLID        | refund[0].billId         | refund[0].billId       | Igual         |
      | REFUND.AMOUNT        | refund[0].amount         | refund[0].amount       | Igual         |
      | PRODUCTST            | null                     | productSt              | "CANCELLED"   |
      | CANCELLATIONST       | null                     | cancellationSt         | "CANCELLED"   |
      | NEXTBILLDT           | null                     | nextBillDt             | (null)        |
      | REFUND.REFUNDST      | null                     | refund[0].refundSt     | "FULL_REFUNDED" |
      | REFUND.UPDATEDVALUE  | null                     | refund[0].updatedValue | 0.00          |

  # Ver nota (2) e (5) no cabeçalho do arquivo: product[0].type=RECURRENCE
  # (em vez de ONESHOT) evita o auto-agendamento de cancelamento que
  # impediria este SCHEDULED de ser aceito; productSt continua ACTIVE (não
  # CANCELLED) porque um agendamento não cancela o produto de imediato.
  Cenário: Sucesso - cancelamento SCHEDULED sem downgrade para IMMEDIATE
    Dado uma requisição de compra válida com protocolo e externalId únicos
    E o campo "product[0].isExpiriationService" da requisição é definido como "true"
    E o campo "product[0].type" da requisição é definido como "RECURRENCE"
    E o campo "product[0].recurrenceFrequency" da requisição é definido como "MONTH"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a massa de cancelamento é preparada a partir da compra recém-registrada
    E o campo "type" da requisição de cancelamento é definido como "SCHEDULED"
    Quando o cancelamento é solicitado via POST /api/v1/purchases/cancel
    Então a resposta de cancelamento deve ter status HTTP 200 e result "SUCCESS"
    E os campos abaixo devem corresponder entre a requisição de cancelamento e a resposta de cancelamento:
      | entidade             | entrada.purchases/cancel | saída.purchases/cancel | valor         |
      | RESULT               | null                     | result                 | "SUCCESS"     |
      | CODE                 | null                     | code                   | 200           |
      | REASON               | null                     | reason                 | "Success"     |
      | PROTOCOL             | protocolId               | protocol               | Igual         |
      | PRODUCTID            | productId                | productId              | Igual         |
      | TYPE                 | type                     | inputType              | Igual         |
      | PROCESSEDTYPE        | type                     | processedType          | Igual         |
      | REFUND.BILLID        | refund[0].billId         | refund[0].billId       | Igual         |
      | REFUND.AMOUNT        | refund[0].amount         | refund[0].amount       | Igual         |
      | PRODUCTST            | null                     | productSt              | "ACTIVE"      |
      | CANCELLATIONST       | null                     | cancellationSt         | "SCHEDULED"   |
      | NEXTBILLDT           | null                     | nextBillDt             | (null)        |
      | REFUND.REFUNDST      | null                     | refund[0].refundSt     | "FULL_REFUNDED" |
      | REFUND.UPDATEDVALUE  | null                     | refund[0].updatedValue | 0.00          |

  # Ver nota (6)/(7) no cabeçalho do arquivo: updatedValue correto é 100.00
  # (110.00 de chargedValue - 10.00 de estorno), e o status é "PART_REFUNDED"
  # (não "PART_REFUND", que não existe em T_DOMAIN_BILL_REFUND_STATUS).
  Cenário: Sucesso - cancelamento IMMEDIATE com estorno PARCIAL da fatura
    Dado uma requisição de compra válida com protocolo e externalId únicos
    E o campo "product[0].isExpiriationService" da requisição é definido como "true"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a massa de cancelamento é preparada a partir da compra recém-registrada
    E o campo "refund[0].amount" da requisição de cancelamento é definido como "10"
    Quando o cancelamento é solicitado via POST /api/v1/purchases/cancel
    Então a resposta de cancelamento deve ter status HTTP 200 e result "SUCCESS"
    E os campos abaixo devem corresponder entre a requisição de cancelamento e a resposta de cancelamento:
      | entidade             | entrada.purchases/cancel | saída.purchases/cancel | valor         |
      | RESULT               | null                     | result                 | "SUCCESS"     |
      | CODE                 | null                     | code                   | 200           |
      | REASON               | null                     | reason                 | "Success"     |
      | PROTOCOL             | protocolId               | protocol               | Igual         |
      | PRODUCTID            | productId                | productId              | Igual         |
      | TYPE                 | type                     | inputType              | Igual         |
      | PROCESSEDTYPE        | type                     | processedType          | Igual         |
      | REFUND.BILLID        | refund[0].billId         | refund[0].billId       | Igual         |
      | REFUND.AMOUNT        | refund[0].amount         | refund[0].amount       | Igual         |
      | PRODUCTST            | null                     | productSt              | "CANCELLED"   |
      | CANCELLATIONST       | null                     | cancellationSt         | "CANCELLED"   |
      | NEXTBILLDT           | null                     | nextBillDt             | (null)        |
      | REFUND.REFUNDST      | null                     | refund[0].refundSt     | "PART_REFUNDED" |
      | REFUND.UPDATEDVALUE  | null                     | refund[0].updatedValue | 100.00        |

  Cenário: Sucesso - cancelamento IMMEDIATE sem estorno (hasRefund=false)
    Dado uma requisição de compra válida com protocolo e externalId únicos
    E o campo "product[0].isExpiriationService" da requisição é definido como "true"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a massa de cancelamento é preparada a partir da compra recém-registrada
    E o campo "hasRefund" da requisição de cancelamento é definido como "false"
    Quando o cancelamento é solicitado via POST /api/v1/purchases/cancel
    Então a resposta de cancelamento deve ter status HTTP 200 e result "SUCCESS"
    E os campos abaixo devem corresponder entre a requisição de cancelamento e a resposta de cancelamento:
      | entidade             | entrada.purchases/cancel | saída.purchases/cancel | valor       |
      | RESULT               | null                     | result                 | "SUCCESS"   |
      | CODE                 | null                     | code                   | 200         |
      | REASON               | null                     | reason                 | "Success"   |
      | PROTOCOL             | protocolId               | protocol               | Igual       |
      | PRODUCTID            | productId                | productId              | Igual       |
      | TYPE                 | type                     | inputType              | Igual       |
      | PROCESSEDTYPE        | type                     | processedType          | Igual       |
      | REFUND               | null                     | refund                 | (null)      |
      | PRODUCTST            | null                     | productSt              | "CANCELLED" |
      | CANCELLATIONST       | null                     | cancellationSt         | "CANCELLED" |
      | NEXTBILLDT           | null                     | nextBillDt             | (null)      |
