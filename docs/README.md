# Exemplos de uso

`exemplo-requisicao.json` é uma versão **corrigida e válida** (JSON
sintaticamente correto) do payload de exemplo do enunciado original -
ver README principal, seção "Inconsistências", item "Payload de exemplo não
é um JSON válido".

## Testando com curl

```bash
curl -X POST http://localhost:8080/api/v1/purchases \
  -H "Content-Type: application/json" \
  -d @docs/exemplo-requisicao.json
```

## Resposta esperada (formato ilustrativo)

Os valores de `id` abaixo são gerados pelo banco (auto-increment) e vão
variar a cada execução - o formato é o que importa:

```json
{
  "result": "SUCCESS",
  "code": "200",
  "reason": "Success",
  "protocol": "WEB-EXAMPLE-0001",
  "account": [ { "id": "1" } ],
  "product": [
    { "codeId": "1", "id": "1", "nextBillDate": "1791726000000", "cycleEndDate": "1791726000000", "isTrial": false, "paymentAssetId": "1" },
    { "codeId": "2", "id": "2", "nextBillDate": null, "cycleEndDate": "1791726000000", "isTrial": false, "paymentAssetId": "1" }
  ],
  "payment": [
    { "method": "CREDIT", "isDefault": true, "id": "1" },
    { "method": "PIX", "isDefault": false, "id": "2" }
  ],
  "billing": [
    { "codeId": "1", "product": "Test Streaming 1", "paymentMethod": "CREDIT", "chargedValue": 12.00, "currency": "BRL", "id": "1" },
    { "codeId": "2", "product": "Test Streaming 2", "paymentMethod": "PIX", "chargedValue": 17.85, "currency": "BRL", "id": "2" }
  ]
}
```

## Exemplo de erro (fatura com soma de taxas incorreta)

Altere, no JSON de exemplo, o `taxValue` da primeira fatura de `2` para `3`
(sem ajustar a soma de `tax[].value`) e a resposta será:

```json
{
  "result": "ERROR",
  "code": "412",
  "reason": "The sum of taxes does not match on product 1",
  "protocol": "WEB-EXAMPLE-0001",
  "account": null,
  "product": null,
  "payment": null,
  "billing": null
}
```

## Consulta de dados (`POST /api/v1/purchases/query`)

`consulta-dados.txt` é o documento de especificação original desta
segunda feature (contrato de entrada/saída, regras de negócio) - ver
README principal, seção "Consulta de dados", para as decisões tomadas
diante das inconsistências encontradas nele.

```bash
curl -X POST http://localhost:8080/api/v1/purchases/query \
  -H "Content-Type: application/json" \
  -d '{"externalId": "XXXX1234"}'
```

Ou, para consultar por produto (aceita tanto o ID formatado devolvido pela
API quanto o técnico puro):

```bash
curl -X POST http://localhost:8080/api/v1/purchases/query \
  -H "Content-Type: application/json" \
  -d '{"productId": "PROD_1", "returnBillData": true, "maxBillReturn": 5}'
```

## Cancelamento de produto (`POST /api/v1/purchases/cancel`)

Terceira feature do projeto (22/09/2026) - ver README principal, seção
"Cancelamento de produto", para o contrato completo (os 3 tipos
`IMMEDIATE`/`SCHEDULED`/`WITHDRAW_CANCELLATION`, as regras de elegibilidade
por tipo e as regras de estorno). Como o anexo original (`cancelamento.txt`)
não é versionado aqui, a fixture
`src/test/resources/cucumber/cancelamento-base.json` (usada pelos testes
funcionais/E2E) é o exemplo mais fiel de um payload que funciona de ponta a
ponta - `productId`/`refund[0].billId`/`refund[0].amount` precisam vir de
uma compra já registrada (ver `product[0].id`/`billing[0].id`/
`billing[0].chargedValue` na resposta de `POST /api/v1/purchases`).

```bash
curl -X POST http://localhost:8080/api/v1/purchases/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "WEB",
    "transactionDt": "1791726000000",
    "protocolId": "CANCEL-EXAMPLE-0001",
    "productId": "PROD_1",
    "type": "IMMEDIATE",
    "description": "Cliente desistiu da compra",
    "hasRefund": true,
    "refund": [ { "billId": "BILL_1", "amount": 12.00 } ]
  }'
```

Resposta esperada (formato ilustrativo - `productSt`/`cancellationSt`
variam conforme o `type` processado, ver README principal):

```json
{
  "result": "SUCCESS",
  "code": "200",
  "reason": "Success",
  "protocol": "CANCEL-EXAMPLE-0001",
  "productId": "PROD_1",
  "inputType": "IMMEDIATE",
  "processedType": "IMMEDIATE",
  "productSt": "CANCELLED",
  "cancellationSt": "CANCELLED",
  "cancellationDt": "1791726000000",
  "nextBillDt": null,
  "refund": [
    { "billId": "BILL_1", "amount": 12.00, "refundSt": "FULL_REFUNDED", "updatedValue": 0.00 }
  ]
}
```
