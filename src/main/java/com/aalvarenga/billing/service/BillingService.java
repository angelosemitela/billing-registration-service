package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.TaxRequest;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.BillInstallmentEntity;
import com.aalvarenga.billing.entity.BillTaxEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.PaymentMethod;
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
import java.util.Optional;

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
     * @param payments pagamentos já persistidos desta compra, na ordem da
     *                 requisição (ver {@link PaymentService})
     * @return faturas efetivamente persistidas (na mesma ordem da requisição)
     */
    public List<BillEntity> persistBillings(List<BillingRequest> billings,
                                             Map<String, ProductEntity> products,
                                             List<PaymentEntity> payments) {
        List<BillEntity> persisted = new ArrayList<>();
        if (billings == null) {
            return persisted;
        }
        for (BillingRequest request : billings) {
            if (MoneyUtil.isZero(request.chargedValue())) {
                continue; // regra explícita: fatura de valor zero não é registrada
            }

            ProductEntity product = products.get(request.codeId());
            PaymentEntity payment = findPaymentByMethod(payments, request.paymentMethod());

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
                    // Vigência da fatura = mesma vigência já calculada para o
                    // produto correspondente (mesma regra, só copiada - ver
                    // V6__add_bill_cycle_and_payment_dates.sql).
                    .cycleStartDt(product.getCycleStartDt())
                    .cycleEndDt(product.getCycleEndDt())
                    // Vencimento = TRANSACTION_DT, conforme decisão registrada
                    // para o fluxo de "/api/v1/purchases" (não há hoje nenhuma
                    // regra de prazo diferente disso).
                    .dueDt(product.getTransactionDt())
                    // PAYMENT_DT só é preenchido quando o repasse é efetivado
                    // (fora do escopo desta v1) - fica null na criação.
                    .paymentDt(null)
                    // Campos acrescentados em 21/09/2026 (ver README, seção
                    // "Evoluções pedidas"): regra explícita do usuário - toda
                    // fatura nasce sem saldo em aberto e sem estorno.
                    .balanceValue(BigDecimal.ZERO)
                    .refundValue(BigDecimal.ZERO)
                    .refundStatus(DomainStatus.BILL_REFUND_NO_REFUND)
                    .build());

            persistTaxes(request.tax(), bill.getId());
            persistInstallmentsIfAny(request, bill.getId());

            persisted.add(bill);
        }
        return persisted;
    }

    /**
     * Encontra, entre os pagamentos já persistidos desta compra, aquele que
     * deve ficar vinculado a uma fatura com este {@code paymentMethod}.
     *
     * <p><strong>Limitação conhecida do schema de entrada</strong> (registrada
     * junto com a correção do bug de 21/09/2026 - ver {@code decisoes.md}):
     * {@code billing.paymentMethod} é só o ENUM do método (CREDIT/DEBIT/PIX/
     * WALLET), sem nenhum campo que aponte para um cartão ESPECÍFICO. Quando
     * a compra tem 2+ pagamentos com o MESMO method (ex: dois cartões
     * CREDIT), não há como saber, só pelo payload atual, qual dos dois esta
     * fatura deveria usar - por isso ficamos com o critério mais simples e
     * mais prático de auditar: o PRIMEIRO da lista com aquele method (mesma
     * ordem em que vieram na requisição), que é também o mesmo critério já
     * usado na validação (ver {@link com.aalvarenga.billing.util.PurchaseLookupUtils#findPaymentByMethod}).
     * Uma evolução futura mais correta seria o próprio payload de entrada
     * referenciar o pagamento explicitamente (ex: um {@code paymentIndex} ou
     * um ID de token em {@code billing}), em vez de inferir por method.
     */
    private PaymentEntity findPaymentByMethod(List<PaymentEntity> payments, PaymentMethod method) {
        String methodName = method.name();
        Optional<PaymentEntity> match = payments.stream()
                .filter(payment -> methodName.equals(payment.getMethod()))
                .findFirst();
        return match.orElse(null);
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
                    // PAYMENT_DT (repasse desta parcela) fica null na criação -
                    // ver V7__add_bill_installment_payment_date.sql.
                    .paymentDt(null)
                    .build());
        }
    }
}
