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
