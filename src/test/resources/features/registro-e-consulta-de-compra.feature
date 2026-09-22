# language: pt
# Ver doc de decisões do projeto ("Testes funcionais/E2E com Cucumber +
# REST Assured + Testcontainers", sessão de 18/09/2026) para o racional
# completo desta suíte. Este arquivo cobre o "caminho feliz": disparar uma
# compra, confirmar que ela foi PERSISTIDA corretamente no banco, e
# confirmar que a CONSULTA (POST /api/v1/purchases/query) devolve
# exatamente os mesmos dados - o ciclo completo pedido pelo usuário.

Funcionalidade: Ciclo completo de registro e consulta de uma compra
  Como consumidor da API de faturamento
  Quero registrar uma compra e depois consultar os dados persistidos
  Para confirmar que o que foi enviado é exatamente o que foi gravado e devolvido pela API

  Cenário: Uma compra registrada com sucesso é persistida e aparece corretamente na consulta
    Dado uma requisição de compra válida com protocolo e externalId únicos
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a conta deve existir na base de dados com o externalId gerado para o cenário
    E os produtos da compra devem existir na base de dados vinculados a essa conta
    E as faturas da compra devem existir na base de dados vinculadas a esses produtos
    Quando a conta é consultada via POST /api/v1/purchases/query pelo externalId do cenário
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E a consulta deve devolver a mesma quantidade de produtos que foram persistidos
    E a consulta deve devolver a mesma quantidade de faturas que foram persistidas

  # Cenário pedido pelo usuário em 21/09/2026: em vez de só CONFERIR QUANTIDADE
  # (cenário acima), valida CAMPO A CAMPO a consistência entre o que foi
  # enviado na criação da compra, o que a criação devolveu, e o que a
  # consulta - encadeada pelo productId que a própria criação gerou, não pelo
  # externalId - devolve depois. A tabela abaixo é a mesma matriz de
  # rastreabilidade que o usuário já tinha mapeado numa planilha própria; ver
  # decisoes.md para o relato completo, incluindo as poucas correções de nome
  # de campo que ela precisou (a consulta usa "accountId"/"paymentId"/
  # "billId", não "id" como a criação da compra, e sua lista de produtos se
  # chama "products", no plural).
  Cenário: Todos os campos de uma compra e da consulta correspondente batem ponta a ponta
    Dado uma requisição de compra válida com protocolo e externalId únicos
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    Quando o produto gerado é consultado via POST /api/v1/purchases/query com todos os retornos habilitados
    Então a resposta deve ter status HTTP 200 e result "SUCCESS"
    E os campos abaixo devem corresponder entre a requisição de compra, a resposta da compra e a resposta da consulta:
      | entidade                     | entrada.purchases               | saída.purchases          | saída.purchases/query         | valor          |
      | PRODUCT.ID                   | null                             | product[0].id            | products[0].productId         | Igual          |
      | TRANSACTIONDT                | transactionDt                   | null                     | products[0].transactionDt     | Igual          |
      | CYCLESTARTDT                 | transactionDt                   | null                     | products[0].cycleStartDt      | Igual          |
      | CHANNEL                      | channel                         | null                     | products[0].channel           | Igual          |
      | PROTOCOL                     | protocol                        | protocol                 | null                          | Igual          |
      | ACCOUNT.ID                   | null                             | account[0].id            | account[0].accountId          | Igual          |
      | ACCOUNT.EXTERNALID           | account[0].externalId           | null                     | account[0].externalId         | Igual          |
      | ACCOUNT.EMAIL                | account[0].email                | null                     | account[0].email              | Igual          |
      | ACCOUNT.ISAUTHORIZEDFALLBACK | account[0].isAuthorizedFallback | null                     | account[0].isAuthorizedFallback | Igual        |
      | ACCOUNT.DOCUMENT.TYPE        | account[0].document[0].type     | null                     | account[0].document[0].type   | Igual          |
      | ACCOUNT.DOCUMENT.DESCRIPTION | account[0].document[0].description | null                  | account[0].document[0].description | Igual     |
      | ACCOUNT.DOCUMENT.VALUE       | account[0].document[0].value    | null                     | account[0].document[0].value  | Igual          |
      | ACCOUNT.PHONE.NUMBER         | account[0].phone[0].number      | null                     | account[0].phone[0].number    | Igual          |
      | PRODUCT.CODEID               | product[0].codeId               | product[0].codeId        | null                          | Igual          |
      | PRODUCT.NAME                 | product[0].name                 | null                     | products[0].name              | Igual          |
      | PRODUCT.TYPE                 | product[0].type                 | null                     | products[0].type              | Igual          |
      | PRODUCT.ISEXPIRATIONSERVICE  | product[0].isExpiriationService | null                     | products[0].isExpirationService | Igual        |
      | PRODUCT.RECURRENCYFREQUENCY  | product[0].recurrenceFrequency  | null                     | products[0].recurrenceFrequency | Igual        |
      | PRODUCT.PRODUCTVALUE         | product[0].productValue         | null                     | products[0].value             | Igual          |
      | PRODUCT.CURRENCY             | product[0].currency             | null                     | products[0].currency          | Igual          |
      | PRODUCT.ISTRIAL              | product[0].isTrial              | product[0].isTrial       | products[0].isTrial           | Igual          |
      | PRODUCT.ISACTIVETRIAL        | product[0].isTrial              | product[0].isTrial       | products[0].isActiveTrial     | Igual          |
      | PRODUCT.TRIALDAYS            | product[0].trialDays            | null                     | products[0].trialDays         | Igual          |
      | PAYMENT.ID                   | null                             | payment[0].id            | payment[0].paymentId          | Igual          |
      | PAYMENT.METHOD               | payment[0].method               | payment[0].method        | payment[0].method             | Igual          |
      | PAYMENT.ISSUER               | payment[0].issuer               | null                     | payment[0].issuer             | Igual          |
      | PAYMENT.CARDNUMBER           | payment[0].cardNumber           | null                     | payment[0].cardNumber         | Igual          |
      | PAYMENT.EXPIRATION           | payment[0].expiration           | null                     | payment[0].expiration         | Igual          |
      | PAYMENT.ISMULTIPLE           | payment[0].isMultiple           | null                     | payment[0].isMultiple         | Igual          |
      | PAYMENT.ISDEFAULT            | payment[0].isDefault            | payment[0].isDefault     | payment[0].isDefault          | Igual          |
      | PAYMENT.INSTALLMENTS         | payment[0].installments         | null                     | payment[0].installments       | Igual          |
      | PAYMENT.BRAND                | payment[0].brand                | null                     | payment[0].brand              | Igual          |
      | BILLING.ID                   | null                             | billing[0].id            | bill[0].billId                | Igual          |
      | BILLING.CODEID               | billing[0].codeId               | billing[0].codeId        | null                          | Igual          |
      | BILLING.CYCLESTARTDT         | transactionDt                   | null                     | bill[0].cycleStartDt          | Igual          |
      | BILLING.DUEDT                | transactionDt                   | null                     | bill[0].dueDt                 | Igual          |
      | BILLING.CURRENCY             | billing[0].currency             | billing[0].currency      | bill[0].currency              | Igual          |
      | BILLING.TAXVALUE             | billing[0].taxValue             | null                     | bill[0].taxValue              | Igual          |
      | BILLING.CHARGEDVALUE         | billing[0].chargedValue         | billing[0].chargedValue  | bill[0].chargedValue          | Igual          |
      | BILLING.UPDATEDVALUE         | billing[0].chargedValue         | billing[0].chargedValue  | bill[0].updatedValue          | Igual          |
      | BILLING.INSTALLMENTS         | billing[0].installments         | null                     | bill[0].installments          | Igual          |
      | BILLING.PROVIDER             | billing[0].provider             | null                     | bill[0].provider              | Igual          |
      | BILLING.PAYMENTMETHOD        | billing[0].paymentMethod        | billing[0].paymentMethod | bill[0].paymentMethod         | Igual          |
      | BILLING.TAX.NAME             | billing[0].tax[0].name          | null                     | bill[0].tax[0].name           | Igual          |
      | BILLING.TAX.VALUE            | billing[0].tax[0].value         | null                     | bill[0].tax[0].value          | Igual          |
      | ACCOUNT.ACCOUNTST            | null                             | null                     | account[0].accountSt          | "ACTIVE"       |
      | PRODUCT.PRODUCTST            | null                             | null                     | products[0].productSt         | "ACTIVE"       |
      | PRODUCT.CANCELLATIONREQDT    | null                             | null                     | products[0].cancellationReqDt | (null)         |
      | PRODUCT.CANCELLATIONSCHDT    | null                             | null                     | products[0].cancellationSchDt | (null)         |
      | PRODUCT.CANCELLATIONEFCDT    | null                             | null                     | products[0].cancellationEfcDt | (null)         |
      | PRODUCT.CANCELLATIONST       | null                             | null                     | products[0].cancellationSt    | "NO_SCHEDULES" |
      | PRODUCT.CANCELLATIONDESC     | null                             | null                     | products[0].cancellationDesc  | (null)         |
      | PRODUCT.AUTOCANCELSCH        | null                             | null                     | products[0].autoCancelSch     | (false)        |
      | PAYMENT.PAYMENTST            | null                             | null                     | payment[0].paymentSt          | "ACTIVE"       |
      | BILLING.BILLST               | null                             | null                     | bill[0].billSt                | "PAID"         |
      | BILLING.BILLTYPE             | null                             | null                     | bill[0].billType              | "BUY"          |
      | BILLING.BALANCEVALUE         | null                             | null                     | bill[0].balanceValue          | 0.00           |
      | BILLING.REFUNDVALUE          | null                             | null                     | bill[0].refundValue           | 0.00           |
      | BILLING.REFUNDST             | null                             | null                     | bill[0].refundSt              | "NO_REFUND"    |
