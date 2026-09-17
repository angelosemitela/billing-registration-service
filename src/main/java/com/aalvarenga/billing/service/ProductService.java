package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.entity.DiscountEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.enums.RecurrenceFrequency;
import com.aalvarenga.billing.repository.DiscountRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persiste a estrutura {@code product} da requisição, incluindo todo o
 * cálculo de datas de recorrência (delegado a {@link RecurrenceCalculatorService})
 * e a criação do desconto associado (quando houver).
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final DiscountRepository discountRepository;
    private final RecurrenceCalculatorService recurrenceCalculatorService;
    private final FeatureToggleService featureToggleService;

    /**
     * Persiste todos os produtos da requisição.
     *
     * @param products                 produtos da requisição, já validados
     * @param transactionDateEpochMillis {@code transactionDate} já convertida
     * @param channel                  canal da venda ({@code channel} da entrada)
     * @param defaultPayment           pagamento marcado como {@code isDefault=true}, se houver (já persistido)
     * @param accountId                ID técnico da conta (já persistida) à qual estes produtos pertencem
     * @return mapa {@code codeId} -> entidade persistida, na ordem da requisição
     */
    public Map<String, ProductEntity> persistProducts(List<ProductRequest> products,
                                                        long transactionDateEpochMillis,
                                                        String channel,
                                                        PaymentEntity defaultPayment,
                                                        Long accountId) {
        Map<String, ProductEntity> persisted = new LinkedHashMap<>();
        // Consultado 1 única vez por requisição (não a cada produto do loop):
        // é a mesma regra para todos os produtos desta compra, então não há
        // motivo para repetir a consulta ao banco a cada iteração.
        boolean autoScheduleCancelEnabled = featureToggleService.isEnabled(
                FeatureToggleRules.AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT);

        for (ProductRequest request : products) {
            boolean isRecurrence = request.type() == ProductType.RECURRENCE;
            boolean isTrial = Boolean.TRUE.equals(request.isTrial());
            boolean expires = Boolean.TRUE.equals(request.isExpiriationService());

            // Frequência "efetiva": usa a informada quando presente; para ONESHOT sem
            // recurrenceFrequency (campo não obrigatório neste caso), assumimos MONTH
            // como padrão apenas para o cálculo do fim de vigência (ver README/ANALISE.md).
            RecurrenceFrequency effectiveFrequency = request.recurrenceFrequency() != null
                    ? request.recurrenceFrequency() : RecurrenceFrequency.MONTH;

            Long nextBillDt = isRecurrence
                    ? computeNextCycleDate(transactionDateEpochMillis, isTrial, request.trialDays(), effectiveFrequency)
                    : null; // "type=ONESHOT será sempre null"

            Long cycleEndDt = expires
                    ? computeNextCycleDate(transactionDateEpochMillis, isTrial, request.trialDays(), effectiveFrequency)
                    : null; // "Só será exibido null para produtos com isExpiriationService = false"

            Long futureBillDt = nextBillDt != null
                    ? recurrenceCalculatorService.advanceCycles(nextBillDt, 1, effectiveFrequency)
                    : null;

            // "Todo assinante com type=ONESHOT deverá preencher com 1(true).
            // Para todos os outros casos deverá preencher com 0" - sempre
            // calculado, nunca vem da entrada (ver README, seção "Evoluções pedidas").
            boolean disableBilling = request.type() == ProductType.ONESHOT;

            // Regra condicionada ao feature toggle AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT
            // (ver FeatureToggleService/FeatureToggleRules): com o toggle ligado, um
            // ONESHOT com vigência (isExpiriationService=true) agenda seu próprio
            // cancelamento automático para a data em que essa vigência termina
            // (cycleEndDt, calculado acima). Em qualquer outro caso - toggle
            // desligado, produto RECURRENCE, ou ONESHOT sem vigência - os dois
            // campos ficam null.
            boolean isOneShotWithExpiration = request.type() == ProductType.ONESHOT && expires;
            Long cancellationReqDt = (autoScheduleCancelEnabled && isOneShotWithExpiration)
                    ? transactionDateEpochMillis : null;
            Long cancellationSchDt = (autoScheduleCancelEnabled && isOneShotWithExpiration)
                    ? cycleEndDt : null;

            ProductEntity entity = ProductEntity.builder()
                    .accountId(accountId)
                    .productId(request.codeId())
                    .name(request.name())
                    .type(request.type().name())
                    .expirationService(expires)
                    .recurrenceFrequency(request.recurrenceFrequency() != null ? request.recurrenceFrequency().name() : null)
                    .value(request.productValue())
                    .currency(request.currency())
                    .trial(isTrial)
                    .trialDays(request.trialDays())
                    .nextBillDt(nextBillDt)
                    .futureBillDt(futureBillDt)
                    .cycleStartDt(transactionDateEpochMillis)
                    .cycleEndDt(cycleEndDt)
                    .defaultPaymentId(defaultPayment != null ? String.valueOf(defaultPayment.getId()) : null)
                    .channel(channel)
                    .transactionDt(transactionDateEpochMillis)
                    .status(expires ? DomainStatus.PRODUCT_ACTIVE : DomainStatus.PRODUCT_SOLD_WITHOUT_SERVICE)
                    .disableBilling(disableBilling)
                    .cancellationReqDt(cancellationReqDt)
                    .cancellationSchDt(cancellationSchDt)
                    .build();

            entity = productRepository.save(entity);
            // Decisão registrada com o usuário (pergunta 3): ASSET_ID = próprio ID técnico da tabela.
            entity.setAssetId(String.valueOf(entity.getId()));
            entity = productRepository.save(entity);

            persistDiscountIfAny(request, entity, transactionDateEpochMillis, effectiveFrequency);

            persisted.put(request.codeId(), entity);
        }
        return persisted;
    }

    private Long computeNextCycleDate(long transactionDateEpochMillis, boolean isTrial, Integer trialDays, RecurrenceFrequency frequency) {
        if (isTrial) {
            return recurrenceCalculatorService.calculateTrialEndDate(transactionDateEpochMillis, trialDays);
        }
        return recurrenceCalculatorService.advanceCycles(transactionDateEpochMillis, 1, frequency);
    }

    private void persistDiscountIfAny(ProductRequest request, ProductEntity product, long transactionDateEpochMillis, RecurrenceFrequency effectiveFrequency) {
        if (request.discountValue() == null || request.discountValue().compareTo(BigDecimal.ZERO) <= 0) {
            return; // "se não houver descontos associados não precisará registrar em tabela"
        }
        long endDt = recurrenceCalculatorService.advanceCycles(transactionDateEpochMillis, request.discountCycles(), effectiveFrequency);
        discountRepository.save(DiscountEntity.builder()
                .productId(product.getId())
                .startDt(transactionDateEpochMillis)
                .endDt(endDt)
                .value(request.discountValue())
                .status(DomainStatus.DISCOUNT_ACTIVE)
                .build());
    }
}
