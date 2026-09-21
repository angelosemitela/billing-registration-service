package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.TaxRequest;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.BillInstallmentEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.repository.BillInstallmentRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.BillTaxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link BillingService}: persistência da estrutura {@code billing} da
 * requisição, com foco na regra mais importante da classe - "se
 * {@code chargedValue} for 0, não registrar o faturamento" (ver javadoc) - e
 * no rateio condicional de parcelas (só quando {@code installments > 1}).
 *
 * <p>Usamos o {@link InstallmentSplitService} REAL (não mockado) porque sua
 * regra de rateio já tem cobertura própria em
 * {@code InstallmentSplitServiceTest} - o que importa aqui é só conferir que
 * {@link BillingService} chama o rateio quando (e apenas quando) deveria.
 */
@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillRepository billRepository;
    @Mock
    private BillTaxRepository billTaxRepository;
    @Mock
    private BillInstallmentRepository billInstallmentRepository;

    private BillingService service;

    private static final Long PRODUCT_ID = 10L;
    private static final Long PAYMENT_ID = 20L;

    @BeforeEach
    void setUp() {
        service = new BillingService(billRepository, billTaxRepository, billInstallmentRepository, new InstallmentSplitService());
    }

    // Movido para fora do @BeforeEach (mesmo motivo documentado em
    // AccountServiceTest.stubAccountRepositorySave): os dois testes que
    // nunca chegam a chamar billRepository.save (lista nula e chargedValue
    // zero, que retornam cedo) fariam o Mockito, em modo estrito, reclamar
    // de "stubbing desnecessário".
    private void stubBillRepositorySave() {
        when(billRepository.save(any())).thenAnswer(invocation -> {
            BillEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });
    }

    private ProductEntity product() {
        return ProductEntity.builder().id(PRODUCT_ID).cycleStartDt(1L).cycleEndDt(2L).transactionDt(3L).build();
    }

    private PaymentEntity payment() {
        // method precisa bater com o PaymentMethod.CREDIT fixo do helper
        // billing(...) abaixo - desde a correção de 21/09/2026,
        // BillingService.findPaymentByMethod compara pelo method GRAVADO na
        // entidade (não mais por uma chave de Map externa), então um
        // PaymentEntity "solto" sem method não seria mais encontrado.
        return PaymentEntity.builder().id(PAYMENT_ID).method("CREDIT").build();
    }

    private BillingRequest billing(String codeId, BigDecimal chargedValue, int installments, List<TaxRequest> taxes) {
        return new BillingRequest(codeId, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, chargedValue,
                "BRL", "TXN-1", installments, "PROVIDER-X", PaymentMethod.CREDIT, taxes);
    }

    @Test
    void persistBillings_nullList_returnsEmptyList() {
        List<BillEntity> result = service.persistBillings(null, Map.of(), List.of());

        assertThat(result).isEmpty();
        verify(billRepository, never()).save(any());
    }

    @Test
    void persistBillings_chargedValueZero_isSkippedEntirely() {
        List<BillingRequest> billings = List.of(billing("1", BigDecimal.ZERO, 1, null));

        List<BillEntity> result = service.persistBillings(billings, Map.of("1", product()), List.of(payment()));

        assertThat(result).isEmpty();
        verify(billRepository, never()).save(any());
    }

    @Test
    void persistBillings_chargedValuePositive_isPersistedWithFieldsCopiedFromProduct() {
        stubBillRepositorySave();
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 1, null));

        List<BillEntity> result = service.persistBillings(billings, Map.of("1", product()), List.of(payment()));

        assertThat(result).hasSize(1);
        BillEntity bill = result.getFirst();
        assertThat(bill.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(bill.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(bill.getCycleStartDt()).isEqualTo(1L);
        assertThat(bill.getCycleEndDt()).isEqualTo(2L);
        assertThat(bill.getDueDt()).isEqualTo(3L);
        assertThat(bill.getStatus()).isEqualTo(DomainStatus.BILL_PAID_AWAITING_TRANSFER);
        assertThat(bill.getBillType()).isEqualTo(DomainStatus.BILL_TYPE_PURCHASE);
    }

    @Test
    void persistBillings_newBill_alwaysStartsWithZeroBalanceAndRefundAndNoRefundStatus() {
        // Regra explícita acrescentada em 21/09/2026 (V14 - ver README, seção
        // "Evoluções pedidas"): toda fatura nasce sem saldo em aberto e sem estorno.
        stubBillRepositorySave();
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 1, null));

        BillEntity bill = service.persistBillings(billings, Map.of("1", product()), List.of(payment())).getFirst();

        assertThat(bill.getBalanceValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getRefundValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getRefundStatus()).isEqualTo(DomainStatus.BILL_REFUND_NO_REFUND);
    }

    @Test
    void persistBillings_paymentMethodNotFoundInList_paymentIdIsNull() {
        // Caso raro (billing sem nenhum payment persistido correspondente) -
        // o serviço não deve lançar NPE, só gravar paymentId nulo.
        stubBillRepositorySave();
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 1, null));

        BillEntity bill = service.persistBillings(billings, Map.of("1", product()), List.of()).getFirst();

        assertThat(bill.getPaymentId()).isNull();
    }

    @Test
    void persistBillings_withTaxes_persistsEachOneLinkedToTheBill() {
        stubBillRepositorySave();
        List<TaxRequest> taxes = List.of(new TaxRequest("ISS", new BigDecimal("5.00")), new TaxRequest("ICMS", new BigDecimal("3.00")));
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 1, taxes));

        service.persistBillings(billings, Map.of("1", product()), List.of(payment()));

        verify(billTaxRepository, times(2)).save(any());
    }

    @Test
    void persistBillings_installmentsOne_neverGeneratesInstallmentRows() {
        stubBillRepositorySave();
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 1, null));

        service.persistBillings(billings, Map.of("1", product()), List.of(payment()));

        verify(billInstallmentRepository, never()).save(any());
    }

    @Test
    void persistBillings_installmentsAboveOne_generatesOneRowPerInstallment() {
        stubBillRepositorySave();
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 3, null));

        service.persistBillings(billings, Map.of("1", product()), List.of(payment()));

        ArgumentCaptor<BillInstallmentEntity> captor = ArgumentCaptor.forClass(BillInstallmentEntity.class);
        verify(billInstallmentRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(BillInstallmentEntity::getInstallment).containsExactly(1, 2, 3);
        assertThat(captor.getAllValues()).allSatisfy(installment -> assertThat(installment.getTotalInstallment()).isEqualTo(3));
    }

    @Test
    void persistBillings_twoPaymentsWithSameMethod_linksToTheFirstOneInRequestOrder() {
        // Regressão do bug reportado em 21/09/2026 (ver decisoes.md) + a
        // decisão de desempate documentada no javadoc de
        // BillingService.findPaymentByMethod: quando 2+ pagamentos
        // compartilham o mesmo method, a fatura fica vinculada ao PRIMEIRO
        // deles na ordem da requisição - nunca ao último, e nunca lança NPE.
        stubBillRepositorySave();
        // billing(...) usa PaymentMethod.CREDIT fixo (ver helper acima) - os
        // dois pagamentos precisam ter method="CREDIT" para os dois serem
        // candidatos válidos ao vínculo.
        PaymentEntity first = PaymentEntity.builder().id(PAYMENT_ID).method("CREDIT").build();
        PaymentEntity second = PaymentEntity.builder().id(PAYMENT_ID + 1).method("CREDIT").build();
        List<BillingRequest> billings = List.of(billing("1", new BigDecimal("100.00"), 1, null));

        BillEntity bill = service.persistBillings(billings, Map.of("1", product()), List.of(first, second)).getFirst();

        assertThat(bill.getPaymentId()).isEqualTo(first.getId());
    }
}
