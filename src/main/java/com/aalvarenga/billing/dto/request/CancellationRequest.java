package com.aalvarenga.billing.dto.request;

import com.aalvarenga.billing.enums.CancellationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Corpo (body) da requisição de cancelamento de produto -
 * {@code POST /api/v1/purchases/cancel}.
 *
 * <p>Mesmo racional de {@link PurchaseRequest} sobre o uso de um
 * {@code record} imutável e sobre validar aqui apenas o que é
 * ESTRUTURAL (Bean Validation via {@code @NotBlank}); toda regra de negócio
 * cruzada (elegibilidade por tipo, formato/existência de {@code productId},
 * regras de estorno) fica em
 * {@link com.aalvarenga.billing.service.CancellationValidationService}.
 *
 * <p><b>Nota de nomenclatura</b> (documentada em {@code decisoes.md}): o
 * campo de idempotência aqui se chama {@code protocolId} (diferente de
 * {@code protocol}, usado em {@link PurchaseRequest} para o mesmo
 * propósito) - grafia literal do anexo fornecido para esta feature. Os dois
 * campos, apesar do nome diferente, compartilham a MESMA coluna
 * {@code T_LOG.PROTOCOL} para a checagem de duplicidade (o schema de
 * {@code T_LOG} não distingue de qual endpoint veio cada requisição) - ou
 * seja, um {@code protocolId} de cancelamento não pode colidir com um
 * {@code protocol} de compra já processado com sucesso, e vice-versa.
 *
 * @param channel     canal de origem do cancelamento (livre, obrigatório)
 * @param transactionDt data/hora da transação, em epoch milissegundos como
 *                    String (mesmo formato/mesmas regras de
 *                    {@link PurchaseRequest#transactionDt()}: não pode ser
 *                    futura nem anterior ao piso configurável
 *                    {@code MINIMAL_TRANSACTION_DATE})
 * @param protocolId  identificador único da requisição, usado para checagem
 *                    de idempotência (ver nota acima)
 * @param productId   produto a ser cancelado. Aceita tanto o formato
 *                    devolvido pela própria API ({@code "PROD_1"}) quanto o
 *                    ID técnico puro ({@code "1"}) - ver {@code AssetIdParser}.
 * @param type        modalidade de cancelamento solicitada - ver
 *                    {@link CancellationType}. Um valor fora do enum já é
 *                    rejeitado pelo Jackson antes de chegar à camada de
 *                    validação (mesmo tratamento de todo outro enum de
 *                    entrada deste serviço); a AUSÊNCIA do campo é checada
 *                    em {@code CancellationValidationService}.
 * @param description justificativa do cancelamento (livre, opcional)
 * @param hasRefund   {@code true} se esta requisição também deve aplicar um
 *                    estorno (ver {@code refund}). Ausente/{@code null} é
 *                    tratado como {@code false} - único valor que ativa o
 *                    processamento de estorno é {@code true} explícito.
 * @param refund      estornos a aplicar, um item por fatura. Obrigatório
 *                    (pelo menos 1 item) quando {@code hasRefund=true};
 *                    ignorado em qualquer outro caso.
 */
public record CancellationRequest(

        @NotBlank(message = "channel is required")
        String channel,

        @NotBlank(message = "transactionDt is required")
        String transactionDt,

        @NotBlank(message = "protocolId is required")
        String protocolId,

        @NotBlank(message = "productId is required")
        String productId,

        CancellationType type,

        String description,

        Boolean hasRefund,

        List<@NotNull RefundRequestItem> refund
) {
}
