package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.enums.RecurrenceFrequency;
import com.aalvarenga.billing.repository.DiscountRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cobre as regras mais novas de {@link ProductService} (evolução de
 * 17/09/2026 - ver README, seção "Evoluções pedidas em 17/09/2026"):
 * {@code DISABLE_BILLING_B} (sempre derivada de {@code type}) e o par
 * {@code CANCELLATION_REQ_DT}/{@code CANCELLATION_SCH_DT}, que só são
 * preenchidos quando a feature toggle {@code AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT}
 * está ligada E o produto é {@code ONESHOT} com {@code isExpiriationService = true}.
 *
 * <p>Usamos a {@link RecurrenceCalculatorService} REAL (não mockada) porque
 * suas regras de cálculo já têm cobertura própria em
 * {@link com.aalvarenga.billing.util.RecurrenceCalculatorServiceTest} - aqui
 * o que importa é conferir que {@code CANCELLATION_SCH_DT} recebe
 * exatamente o mesmo valor que {@code CYCLE_END_DT}, e não recalcular a
 * regra de data de novo.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private DiscountRepository discountRepository;
    @Mock
    private FeatureToggleService featureToggleService;

    private ProductService service;

    private static final long TRANSACTION_DT = 1_800_000_000_000L;

    @BeforeEach
    void setUp() {
        // RecurrenceCalculatorService real: é lógica pura, já testada à parte -
        // não há motivo para mocká-la aqui (ver javadoc da classe).
        service = new ProductService(productRepository, discountRepository, new RecurrenceCalculatorService(), featureToggleService);
        // "save" ecoa a própria entidade recebida, como o Hibernate faria para
        // uma entidade nova sem colunas geradas pelo banco além do ID (que não
        // usamos nas asserções abaixo).
        when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProductRequest product(ProductType type, boolean expires) {
        return new ProductRequest(
                "1", "Produto Teste", type, expires, RecurrenceFrequency.MONTH,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
    }

    @Test
    void oneShotWithExpiration_toggleOn_fillsCancellationDatesAndDisablesBilling() {
        when(featureToggleService.isEnabled(FeatureToggleRules.AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT)).thenReturn(true);

        Map<String, ProductEntity> result = service.persistProducts(
                List.of(product(ProductType.ONESHOT, true)), TRANSACTION_DT, "WEB", null, 1L);

        ProductEntity entity = result.get("1");
        assertThat(entity.getDisableBilling()).isTrue();
        assertThat(entity.getCancellationReqDt()).isEqualTo(TRANSACTION_DT);
        // Mesma data calculada para o fim de vigência do produto (CYCLE_END_DT) -
        // é exatamente essa a regra pedida ("valor de CYCLE_END_DT gerado pelo serviço").
        assertThat(entity.getCancellationSchDt()).isEqualTo(entity.getCycleEndDt());
        assertThat(entity.getCancellationSchDt()).isNotNull();
        // Campos acrescentados em 21/09/2026 (ver README, seção "Evoluções
        // pedidas") - "cenário 2" (cancelamento agendado): mesma condição
        // booleana de cancellationReqDt/cancellationSchDt acima, nunca uma
        // derivação própria.
        assertThat(entity.getSuspensionStatus()).isEqualTo(DomainStatus.PRODUCT_SUSPENSION_COMPLIANT);
        assertThat(entity.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED);
        assertThat(entity.getCancellationChannel()).isEqualTo("BRS");
        assertThat(entity.getCancellationDescription()).isNotBlank();
        assertThat(entity.getAutoCancelSch()).isTrue();
        assertThat(entity.getCancellationEfcDt()).isNull();
    }

    @Test
    void oneShotWithExpiration_toggleOff_leavesCancellationDatesNull() {
        when(featureToggleService.isEnabled(FeatureToggleRules.AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT)).thenReturn(false);

        Map<String, ProductEntity> result = service.persistProducts(
                List.of(product(ProductType.ONESHOT, true)), TRANSACTION_DT, "WEB", null, 1L);

        ProductEntity entity = result.get("1");
        // DISABLE_BILLING_B não depende do toggle - continua "1" mesmo com a
        // regra de cancelamento desligada.
        assertThat(entity.getDisableBilling()).isTrue();
        assertThat(entity.getCancellationReqDt()).isNull();
        assertThat(entity.getCancellationSchDt()).isNull();
        // "Cenário 1" (sem agendamento) - ver README, seção "Evoluções pedidas" de 21/09/2026.
        assertThat(entity.getSuspensionStatus()).isEqualTo(DomainStatus.PRODUCT_SUSPENSION_COMPLIANT);
        assertThat(entity.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES);
        assertThat(entity.getCancellationChannel()).isNull();
        assertThat(entity.getCancellationDescription()).isNull();
        assertThat(entity.getAutoCancelSch()).isFalse();
    }

    @Test
    void oneShotWithoutExpiration_toggleOn_leavesCancellationDatesNull() {
        when(featureToggleService.isEnabled(FeatureToggleRules.AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT)).thenReturn(true);

        Map<String, ProductEntity> result = service.persistProducts(
                List.of(product(ProductType.ONESHOT, false)), TRANSACTION_DT, "WEB", null, 1L);

        ProductEntity entity = result.get("1");
        assertThat(entity.getDisableBilling()).isTrue();
        assertThat(entity.getCancellationReqDt()).isNull();
        assertThat(entity.getCancellationSchDt()).isNull();
        assertThat(entity.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES);
        assertThat(entity.getAutoCancelSch()).isFalse();
    }

    @Test
    void recurrenceProduct_toggleOn_neverDisablesBillingNorSchedulesCancellation() {
        when(featureToggleService.isEnabled(FeatureToggleRules.AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT)).thenReturn(true);

        Map<String, ProductEntity> result = service.persistProducts(
                List.of(product(ProductType.RECURRENCE, true)), TRANSACTION_DT, "WEB", null, 1L);

        ProductEntity entity = result.get("1");
        assertThat(entity.getDisableBilling()).isFalse();
        assertThat(entity.getCancellationReqDt()).isNull();
        assertThat(entity.getCancellationSchDt()).isNull();
        assertThat(entity.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES);
        assertThat(entity.getAutoCancelSch()).isFalse();
    }
}
