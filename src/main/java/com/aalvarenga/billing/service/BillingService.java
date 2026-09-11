package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.TaxRequest;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.BillInstallmentEntity;
import com.aalvarenga.billing.entity.BillTaxEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.repository.BillInstallmentRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.BillTaxRepository;
import com.aalvarenga.billing.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persiste a estrutura {@code billing} da requisição.
 *
 * <p>Regra central (explícita no enunciado): "Se o valor chargedValue for
 * igual a 0, não registrar o faturamento" - ou seja, faturas de valor zero
 * (tipicamente produtos 100% trial) simplesmente não geram registro em
 * {@code T_BILL} nem aparecem na resposta.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BillRepository billRepository;
    private final BillTaxRepository billTaxRepository;
    private final BillInstallmentRepository billInstallmentRepository;
    private final InstallmentSplitService installmentSplitService;

    /**
     * Persiste as faturas com {@code chargedValue > 0}.
     *
     * @param billings faturas da requisição, já validadas
     * @param products mapa {@code codeId} -> produto já persistido (ver {@link ProductService})
     * @param payments mapa {@link com.aalvarenga.billing.enums.PaymentMethod} -> pagamento já persistido (ver {@link PaymentService})
     * @return faturas efetivamente persistidas (na mesma ordem da requisição)
     */
    public List<BillEntity> persistBillings(List<BillingRequest> billings,
                                             Map<String, ProductEntity> products,
                                             Map<com.aalvarenga.billing.enums.PaymentMethod, PaymentEntity> payments) {
        List<BillEntity> persisted = new ArrayList<>();
        if (billings == null) {
            return persisted;
        }
        for (BillingRequest request : billings) {
            if (MoneyUtil.isZero(request.chargedValue())) {
                continue; // regra explícita: fatura de valor zero não é registrada
            }

            ProductEntity product = products.get(request.codeId());
            // O pagamento vinculado a esta fatura é aquele cujo METHOD bate com
            // billing.paymentMethod (já garantido pela validação - ver
            // PurchaseValidationService.validateBillings).
            PaymentEntity payment = payments.get(request.paymentMethod());

            BillEntity bill = billRepository.save(BillEntity.builder()
                    .codeId(request.codeId())
                    .productId(product.getId())
                    .paymentId(payment != null ? payment.getId() : null)
                    .productValue(request.productValue())
                    .discountValue(request.discountValue())
                    .taxValue(request.taxValue())
                    .chargedValue(request.chargedValue())
                    .currency(request.currency())
                    .transactionId(request.transactionId())
                    .installments(String.valueOf(request.installments()))
                    .provider(request.provider())
                    .paymentMethod(request.paymentMethod().name())
                    .status(DomainStatus.BILL_PAID_AWAITING_TRANSFER)
                    .billType(DomainStatus.BILL_TYPE_PURCHASE)
                    .build());

            persistTaxes(request.tax(), bill.getId());
            persistInstallmentsIfAny(request, bill.getId());

            persisted.add(bill);
        }
        return persisted;
    }

    private void persistTaxes(List<TaxRequest> taxes, Long billId) {
        if (taxes == null) {
            return;
        }
        for (TaxRequest tax : taxes) {
            billTaxRepository.save(BillTaxEntity.builder()
                    .billId(billId)
                    .name(tax.name())
                    .value(tax.value())
                    .build());
        }
    }

    private void persistInstallmentsIfAny(BillingRequest request, Long billId) {
        int totalInstallments = request.installments();
        if (totalInstallments <= 1) {
            return; // "considerar somente quando installments > 1"
        }

        List<BigDecimal> productSplit = installmentSplitService.split(request.productValue(), totalInstallments);
        List<BigDecimal> discountSplit = installmentSplitService.split(request.discountValue(), totalInstallments);
        List<BigDecimal> taxSplit = installmentSplitService.split(request.taxValue(), totalInstallments);

        for (int i = 0; i < totalInstallments; i++) {
            billInstallmentRepository.save(BillInstallmentEntity.builder()
                    .billId(billId)
                    .installment(i + 1)
                    .totalInstallment(totalInstallments)
                    .productValue(productSplit.get(i))
                    .discountValue(discountSplit.get(i))
                    .taxValue(taxSplit.get(i))
                    .status(DomainStatus.BILL_INSTALLMENT_AWAITING_TRANSFER)
                    .build());
        }
    }
}
