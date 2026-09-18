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
      | account[0].externalId            | (vazio)           | 400            | account.externalId                    |
      | account[0].isAuthorizedFallback  | (ausente)         | 400            | isAuthorizedFallback                  |
      | product[0].productValue          | -10               | 400            | productValue                          |
      | product[0].discountValue         | 999999            | 400            | discountValue cannot be greater       |
      | product[0].currency              | XYZ               | 400            | Unknown currency code                 |
      | payment[0].isDefault             | (ausente)         | 400            | isDefault                             |
      | billing[0].taxValue              | 0                 | 412            | sum of taxes does not match           |
      | billing[0].chargedValue          | 0                 | 412            | does not match chargedValue           |
