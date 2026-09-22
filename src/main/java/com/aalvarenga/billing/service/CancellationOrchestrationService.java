package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.response.CancellationResponse;
import com.aalvarenga.billing.dto.response.RefundResponseItem;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.CancellationType;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import com.aalvarenga.billing.util.AssetIdFormatter;
import com.aalvarenga.billing.util.EpochDateUtil;
import com.aalvarenga.billing.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orquestra a PERSISTÊNCIA de um cancelamento já validado: atualiza
 * {@code T_PRODUCT} conforme o tipo efetivamente processado e aplica os
 * estornos em {@code T_BILL} (quando houver), tudo dentro de uma ÚNICA
 * transação.
 *
 * <p>A anotação {@code @Transactional} aqui cumpre exatamente a Regra Geral
 * 1 do anexo ("não poderemos ter parte das entidades processadas e partes
 * pendentes... implica o rollback total da transação") - mesmo mecanismo já
 * usado em {@link PurchaseOrchestrationService}: qualquer falha no meio do
 * caminho (ex: uma constraint do banco, uma exceção inesperada) desfaz TUDO
 * o que esta chamada já tinha gravado, nunca deixando o cancelamento
 * "pela metade" (produto cancelado mas estorno não aplicado, ou vice-versa).
 *
 * <p><b>Sobre o estorno "em um futuro próximo" (Regra Geral 2 do anexo)</b>:
 * o anexo pede para "desenvolver o fluxo de estorno pensando em disponibilizar
 * em um futuro próximo a devolução dos valores em um endpoint dedicado". A
 * decisão desta v1 foi manter a mutação de {@code T_BILL} (REFUND_VALUE/
 * REFUND_STATUS/STATUS) dentro deste MESMO orquestrador (nenhum novo
 * service dedicado a "devolução" foi criado agora, já que ainda não existe
 * nenhum evento/gatilho separado para isso) - mas isolada nos métodos
 * privados {@link #processRefunds}/{@link #applyRefund}, exatamente para
 * que uma extração futura (um {@code RefundService} próprio, chamado tanto
 * daqui quanto de um futuro endpoint dedicado de devolução) seja possível
 * sem reescrever a regra, só movendo estes métodos de arquivo.
 */
@Service
@RequiredArgsConstructor
public class CancellationOrchestrationService {

    private final ProductRepository productRepository;
    private final BillRepository billRepository;
    private final DomainStatusLookupService domainStatusLookupService;

    @Transactional
    public CancellationResponse persistAndBuildResponse(CancellationRequest request, ValidatedCancellationContext context) {
        ProductEntity product = context.product();
        CancellationType processedType = context.processedType();

        // Comportamento comum a todos os tipos (ver anexo, seção "Comportamento comum a todos os tipos").
        product.setCancellationReqDt(context.transactionDateEpochMillis());
        product.setCancellationChannel(request.channel());
        product.setCancellationDescription(request.description());

        switch (processedType) {
            case IMMEDIATE -> applyImmediate(product);
            case SCHEDULED -> applyScheduled(product);
            case WITHDRAW_CANCELLATION -> applyWithdraw(product, context.transactionDateEpochMillis());
        }
        product = productRepository.save(product);

        List<RefundResponseItem> refundItems = context.refunds().isEmpty() ? null : processRefunds(context.refunds());

        Map<Integer, String> productStatuses = domainStatusLookupService.productStatuses(Set.of(product.getStatus()));
        Map<Integer, String> cancellationStatuses = domainStatusLookupService.productCancellationStatuses(Set.of(product.getCancellationStatus()));

        return CancellationResponse.success(
                request.protocolId(),
                AssetIdFormatter.product(product.getId()),
                context.inputType(),
                processedType,
                productStatuses.get(product.getStatus()),
                cancellationStatuses.get(product.getCancellationStatus()),
                product.getCancellationSchDt() != null ? String.valueOf(product.getCancellationSchDt()) : null,
                // "nextBillDt": só para processedType=WITHDRAW_CANCELLATION - null em qualquer outro caso.
                processedType == CancellationType.WITHDRAW_CANCELLATION && product.getNextBillDt() != null
                        ? String.valueOf(product.getNextBillDt()) : null,
                refundItems
        );
    }

    /** Regra do tipo {@code IMMEDIATE} - ver anexo, bloco "Registrar" logo após as 3 regras de elegibilidade. */
    private void applyImmediate(ProductEntity product) {
        long now = EpochDateUtil.nowMillis();
        product.setCancellationSchDt(now);
        product.setCancellationStatus(DomainStatus.PRODUCT_CANCELLATION_CANCELLED);
        product.setDisableBilling(true);
        product.setCancellationEfcDt(now);
        product.setStatus(DomainStatus.PRODUCT_CANCELLED);
    }

    /** Regra do tipo {@code SCHEDULED} - ver anexo, bloco "Registrar" logo após a Regra 3. */
    private void applyScheduled(ProductEntity product) {
        product.setCancellationSchDt(product.getCycleEndDt());
        product.setCancellationStatus(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED);
        product.setDisableBilling(true);
    }

    /** Regra do tipo {@code WITHDRAW_CANCELLATION} - ver anexo, bloco "Registrar" logo após a Regra 1. */
    private void applyWithdraw(ProductEntity product, long transactionDateEpochMillis) {
        product.setCancellationSchDt(null);
        product.setCancellationStatus(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES);
        product.setDisableBilling(false);
        // Redundante com o "comportamento comum" já aplicado acima (mesmo
        // valor), mas o anexo repete esta atribuição explicitamente dentro
        // do bloco "Registrar" específico de WITHDRAW_CANCELLATION - mantido
        // aqui só por fidelidade literal ao passo a passo do anexo.
        product.setCancellationReqDt(transactionDateEpochMillis);
    }

    /**
     * Aplica cada estorno já validado (ver {@link ResolvedRefundItem}) e
     * monta o item de resposta correspondente. Ver Regras de estorno do
     * anexo, bloco "Registrar" final.
     */
    private List<RefundResponseItem> processRefunds(List<ResolvedRefundItem> refunds) {
        List<RefundResponseItem> result = new ArrayList<>();
        for (ResolvedRefundItem refund : refunds) {
            result.add(applyRefund(refund));
        }
        return result;
    }

    private RefundResponseItem applyRefund(ResolvedRefundItem refund) {
        BillEntity bill = refund.bill();
        bill.setRefundValue(bill.getRefundValue().add(refund.amount()));

        boolean fullyRefunded = MoneyUtil.equalsMoney(bill.getRefundValue(), bill.getChargedValue());
        bill.setRefundStatus(fullyRefunded ? DomainStatus.BILL_REFUND_FULL_REFUNDED : DomainStatus.BILL_REFUND_PART_REFUNDED);
        if (fullyRefunded) {
            bill.setStatus(DomainStatus.BILL_RETURNED);
        }
        bill = billRepository.save(bill);

        Map<Integer, String> refundStatuses = domainStatusLookupService.billRefundStatuses(Set.of(bill.getRefundStatus()));
        return new RefundResponseItem(
                AssetIdFormatter.billing(bill.getId()),
                refund.amount(),
                refundStatuses.get(bill.getRefundStatus()),
                bill.getChargedValue().subtract(bill.getRefundValue())
        );
    }
}
