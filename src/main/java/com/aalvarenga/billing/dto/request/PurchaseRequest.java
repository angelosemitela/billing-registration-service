package com.aalvarenga.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Corpo (body) da requisição de registro de compra/faturamento -
 * {@code POST /api/v1/purchases}.
 *
 * <p>Usamos um {@code record} (recurso do Java desde a versão 16, e livre
 * para usarmos com Java 25) em vez de uma classe tradicional com
 * getters/setters: um record é imutável por natureza (todos os campos são
 * {@code final}), o que é uma ótima característica para um DTO de entrada -
 * uma vez desserializado o JSON, ninguém deveria conseguir alterar os dados
 * da requisição no meio do processamento.
 *
 * <p>Aqui aplicamos apenas validações "estruturais" simples via Bean
 * Validation ({@code @NotNull}, {@code @NotBlank}...), que o Spring executa
 * automaticamente graças ao {@code @Valid} no controller. As dezenas de
 * regras de negócio CRUZADAS (ex: "se X então Y é obrigatório") ficam
 * centralizadas em
 * {@link com.aalvarenga.billing.service.PurchaseValidationService}, pois
 * elas precisam devolver mensagens de erro específicas em inglês e códigos
 * HTTP 4XX diferentes (400/404/409/412) - algo que a Bean Validation sozinha
 * não expressa bem.
 *
 * @param channel       canal da venda (livre, obrigatório)
 * @param transactionDt data/hora da transação, em epoch milissegundos,
 *                      representada como String (mesmo formato usado nos
 *                      exemplos de payload do enunciado). Renomeado de
 *                      {@code transactionDate} para {@code transactionDt}
 *                      em 17/09/2026 para seguir o mesmo padrão de sufixo
 *                      {@code Dt} usado em todos os outros campos de data
 *                      da entrada/saída deste serviço - ver README, seção
 *                      "Evoluções pedidas".
 * @param protocol      identificador único da requisição, usado para
 *                      checagem de idempotência (ver RequestLogService)
 * @param account       estrutura da conta do assinante (o enunciado modela
 *                      como lista, mas esta implementação assume 1 único
 *                      elemento - ver README/ANALISE.md)
 * @param product       lista de produtos comprados (1 ou mais)
 * @param payment       lista de métodos de pagamento (opcional; obrigatória
 *                      apenas quando há cobrança ou produto RECURRENCE)
 * @param billing       lista de faturas a serem geradas (opcional)
 */
public record PurchaseRequest(

        @NotBlank(message = "channel is required")
        String channel,

        @NotBlank(message = "transactionDt is required")
        String transactionDt,

        @NotBlank(message = "protocol is required")
        String protocol,

        @NotEmpty(message = "account is required")
        List<@NotNull AccountRequest> account,

        @NotEmpty(message = "product is required")
        List<@NotNull ProductRequest> product,

        List<@NotNull PaymentRequest> payment,

        List<@NotNull BillingRequest> billing
) {
}
