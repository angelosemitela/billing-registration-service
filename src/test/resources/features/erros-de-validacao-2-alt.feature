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
    E o campo "<campo1>" da requisição é definido como "<valor1>"
    E o campo "<campo2>" da requisição é definido como "<valor2>"
    Quando a compra é registrada via POST /api/v1/purchases
    Então a resposta deve ter status HTTP <statusEsperado> e result "ERROR"
    E a razão da resposta deve conter "<trechoEsperado>"

    Exemplos:
      | campo1                  | valor1             | campo2                              |  valor2     | statusEsperado | trechoEsperado                      |
      | product[0].type         | RECURRENCE         | product[0].isExpiriationService     |  false      | 400            | product.isExpiriationService        |
      | product[0].isTrial      | true               | product[0].trialDays                |  0          | 400            | product.trialDays                   |
      | product[0].isTrial      | true               | product[0].trialDays                |  teste      | 400            | Integer                             |
      | product[0].isTrial      | true               | product[0].trialDays                |  1          | 412            | billing.chargedValue must be zero   |
