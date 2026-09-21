# language: pt
# Ver doc de decisões do projeto ("Testes funcionais/E2E com Cucumber +
# REST Assured + Testcontainers", sessão de 18/09/2026) para o racional
# completo. Este arquivo cobre o pedido "validar todos os erros possíveis
# via API": cada regra de PurchaseValidationService que já tem um teste de
# UNIDADE (ver PurchaseValidationServiceTest) ganha aqui, adicionalmente, uma
# confirmação de PONTA A PONTA (HTTP real -> validação -> resposta HTTP real)
# como UMA LINHA na tabela de exemplos abaixo - sem escrever um cenário Java
# novo por regra. Isso é o que torna esta suíte fácil de MANTER À MEDIDA QUE
# ESCALA: uma regra de validação nova em PurchaseValidationService normalmente
# só precisa de uma linha nova aqui, não de código novo.
#
# A mesma tabela também cobre, a partir de 18/09/2026, erros que NÃO vêm de
# PurchaseValidationService - ex: um JSON estruturalmente inválido (valor de
# enum fora do domínio, tipo errado etc.) nunca chega a essa camada, pois o
# Jackson falha ao DESSERIALIZAR o corpo antes disso, e quem responde é o
# GlobalExceptionHandler ("Malformed request body: ..."). Do ponto de vista
# do teste (POST -> status HTTP -> trecho do "reason") não importa em qual
# camada o erro nasceu - por isso cabe na mesma tabela, sem infraestrutura
# nova.
#
# A tabela abaixo NÃO tenta ser exaustiva ainda (ratchet: começa com uma
# amostra representativa de cada categoria de erro - 400 por campo ausente/
# formato inválido/valor fora do domínio, 412 por fórmula que não fecha -
# e cresce aos poucos, igual já aconteceu com o piso de cobertura do
# JaCoCo/limiar do SpotBugs). Ver PurchaseValidationService para a lista
# completa de regras ainda sem uma linha correspondente aqui.

Funcionalidade: Validação de erros de negócio no registro de uma compra
  Como consumidor da API de faturamento
  Quero receber o status HTTP e o motivo corretos
  Para cada violação prevista das regras de negócio de PurchaseValidationService

  Esquema do Cenário: Uma requisição inválida é rejeitada com o status e o motivo corretos
    Dado uma requisição de compra válida com protocolo e externalId únicos
    E o campo "<campo>" da requisição é definido como "<valor>"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP <statusEsperado> e result "ERROR"
    E a razão da resposta deve conter "<trechoEsperado>"

    Exemplos:
      | campo                            | valor             | statusEsperado | trechoEsperado                        |
      | account[0].email                 | (vazio)           | 400            | account.email                         |
      | transactionDt                    | 2000000000000000  | 400            | transactionDt cannot be in the future |
      | transactionDt                    | 1                 | 400            | minimum accepted date                 |
      | channel                          | (vazio)           | 400            | channel: channel is required          |
      | account[0].id                    | 1234567890        | 404            | account.id not found                  |
      | account[0].name                  | (vazio)           | 400            | account.name                          |
      | account[0].externalId            | (vazio)           | 400            | account.externalId                    |
      | account[0].isAuthorizedFallback  | (ausente)         | 400            | isAuthorizedFallback                  |
      | account[0].document[0].type      | (vazio)           | 400            | DocumentType                          |
      | account[0].document[0].type      | XYZ               | 400            | DocumentType                          |
      | account[0].document[0].value     | (vazio)           | 400            | account.document.value                |
      | account[0].document[0].country   | (vazio)           | 400            | account.document.country              |
      | account[0].document[0].country   | XYZ               | 400            | Unknown country code                  |
      | account[0].address[0].type       | (vazio)           | 400            | AddressType                           |
      | account[0].address[0].type       | XYZ               | 400            | AddressType                           |
      | account[0].address[0].description| (vazio)           | 400            | account.address.description           |
      | account[0].address[0].addressName| (vazio)           | 400            | account.address.addressName           |
      | account[0].address[0].number     | (vazio)           | 400            | account.address.number                |
      | account[0].address[0].zipCode    | (vazio)           | 400            | account.address.zipCode               |
      | account[0].address[0].country    | (vazio)           | 400            | account.address.country               |
      | account[0].address[0].country    | XYZ               | 400            | Unknown country code                  |
      | account[0].phone[0].number       | (vazio)           | 400            | account.phone.number                  |
      | account[0].phone[0].number       | XYZ               | 400            | account.phone.number                  |
      | product[0].codeId                | (vazio)           | 400            | product.codeId                        |
      | product[0].name                  | (vazio)           | 400            | product.name                          |
      | product[0].type                  | (vazio)           | 400            | ProductType                           |
      | product[0].type                  | XYZ               | 400            | ProductType                           |
      | product[0].isExpiriationService  | (ausente)         | 400            | product.isExpiriationService          |
      | product[0].isExpiriationService  | false             | 400            | product.isExpiriationService          |
      | product[0].recurrenceFrequency   | (vazio)           | 400            | RecurrenceFrequency                   |
      | product[0].recurrenceFrequency   | XYZ               | 400            | RecurrenceFrequency                   |
      | product[0].productValue          | (vazio)           | 400            | product.productValue                  |
      | product[0].productValue          | -10               | 400            | productValue                          |
      | product[0].discountValue         | (vazio)           | 400            | product.discountValue                 |
      | product[0].discountValue         | -10               | 400            | product.discountValue                 |
      | product[0].discountValue         | 999999            | 400            | discountValue cannot be greater       |
      | product[0].isTrial               | true              | 400            | product.trialDays                     |
      | product[0].currency              | (vazio)           | 400            | product.currency                      |
      | product[0].currency              | XYZ               | 400            | Unknown currency code                 |
      | payment[0].method                | (vazio)           | 400            | PaymentMethod                         |
      | payment[0].method                | XYZ               | 400            | PaymentMethod                         |
      | payment[0].cardNumber            | (vazio)           | 400            | payment.cardNumber                    |
      | payment[0].expiration            | (vazio)           | 400            | payment.expiration                    |
      | payment[0].expiration            | XYZ               | 400            | payment.expiration                    |
      | payment[0].expiration            | 13/31             | 400            | payment.expiration                    |
      | payment[0].expiration            | 12/22             | 400            | payment.expiration                    |
      | payment[0].isMultiple            | (vazio)           | 400            | payment.isMultiple                    |
      | payment[0].isMultiple            | XYZ               | 400            | payment                               |
      | payment[0].isDefault             | (ausente)         | 400            | isDefault                             |
      | payment[0].isDefault             | false             | 400            | isDefault                             |
      | payment[0].installments          | (vazio)           | 400            | installments                          |
      | payment[0].installments          | XYZ               | 400            | Integer                               |
      | payment[0].installments          | -1                | 400            | installments                          |
      | payment[0].installments          | 0                 | 400            | installments                          |
      | payment[0].installments          | 13                | 400            | installments                          |
      | payment[0].token[0].name         | (vazio)           | 400            | payment.token.name                    |
      | payment[0].token[0].id           | (vazio)           | 400            | payment.token.id                      |
      | payment[0].token[0].gateway      | (vazio)           | 400            | payment.token.gateway                 |
      | payment[0].token[0].expirationDt | 1                 | 400            | expirationDt cannot be in the past    |
      | billing[0].codeId                | (vazio)           | 400            | billing.codeId                        |
      | billing[0].productValue          | (vazio)           | 400            | billing.productValue                  |
      | billing[0].productValue          | -1                | 400            | billing.productValue                  |
      | billing[0].productValue          | 0                 | 412            | productValue                          |
      | billing[0].discountValue         | (vazio)           | 400            | billing.discountValue                 |
      | billing[0].discountValue         | -1                | 400            | billing.discountValue                 |
      | billing[0].discountValue         | 1                 | 412            | discountValue                         |
      | billing[0].taxValue              | (vazio)           | 400            | billing.taxValue                      |
      | billing[0].taxValue              | -1                | 400            | billing.taxValue                      |
      | billing[0].taxValue              | 0                 | 412            | sum of taxes does not match           |
      | billing[0].chargedValue          | (vazio)           | 400            | billing.chargedValue                  |
      | billing[0].chargedValue          | -1                | 400            | billing.chargedValue                  |
      | billing[0].chargedValue          | 0                 | 412            | does not match chargedValue           |
      | billing[0].currency              | (vazio)           | 400            | billing.currency                      |
      | billing[0].currency              | XYZ               | 400            | Unknown currency code                 |
      | billing[0].transactionId         | (vazio)           | 400            | billing.transactionId                 |
      | billing[0].installments          | (vazio)           | 400            | installments                          |
      | billing[0].installments          | XYZ               | 400            | Integer                               |
      | billing[0].installments          | -1                | 400            | installments                          |
      | billing[0].installments          | 0                 | 400            | installments                          |
      | billing[0].installments          | 13                | 400            | installments                          |
      | billing[0].provider              | (vazio)           | 400            | billing.provider                      |
      | billing[0].paymentMethod         | (vazio)           | 400            | PaymentMethod                         |
      | billing[0].paymentMethod         | XYZ               | 400            | PaymentMethod                         |
      | billing[0].paymentMethod         | PIX               | 400            | billing.paymentMethod                 |
      | billing[0].tax[0].value          | 0                 | 412            | sum of taxes does not match           |
      | billing[0].tax[0].name           | (vazio)           | 400            | billing.tax.name                      |
