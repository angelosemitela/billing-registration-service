package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.TokenRequest;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.PaymentTokenEntity;
import com.aalvarenga.billing.enums.CardBrand;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.repository.PaymentRepository;
import com.aalvarenga.billing.repository.PaymentTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link PaymentService}: persistência da estrutura {@code payment} da
 * requisição, incluindo a regra registrada em README/ANALISE.md (pergunta 4)
 * de que pagamentos/tokens são SEMPRE persistidos quando informados, mesmo em
 * uma compra 100% trial - ver javadoc da classe.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentTokenRepository paymentTokenRepository;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, paymentTokenRepository);
    }

    private PaymentRequest payment(PaymentMethod method, Boolean isDefault, CardBrand brand, List<TokenRequest> tokens) {
        return new PaymentRequest(method, "ISSUER", "1234", "12/30", false, isDefault, 1, tokens, brand);
    }

    // Movido para fora do @BeforeEach (mesmo motivo documentado em
    // AccountServiceTest.stubAccountRepositorySave): o único teste que NÃO
    // chama paymentRepository.save (persistPayments_nullList_returnsEmptyList)
    // faria o Mockito, em modo estrito, reclamar de "stubbing desnecessário".
    private void stubPaymentRepositorySave() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            PaymentEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
    }

    @Test
    void persistPayments_nullList_returnsEmptyList() {
        List<PaymentEntity> result = service.persistPayments(null, 1L);

        assertThat(result).isEmpty();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void persistPayments_mapsAllFieldsAndKeepsInsertionOrder() {
        stubPaymentRepositorySave();
        List<PaymentEntity> result = service.persistPayments(
                List.of(payment(PaymentMethod.CREDIT, true, CardBrand.VISA, null), payment(PaymentMethod.PIX, false, null, null)), 1L);

        assertThat(result).extracting(PaymentEntity::getMethod).containsExactly("CREDIT", "PIX");
        PaymentEntity credit = result.getFirst();
        assertThat(credit.getAccountId()).isEqualTo(1L);
        assertThat(credit.getMethod()).isEqualTo("CREDIT");
        assertThat(credit.getDefaultMethod()).isTrue();
        assertThat(credit.getBrand()).isEqualTo("VISA");
        assertThat(credit.getStatus()).isEqualTo(DomainStatus.PAYMENT_ACTIVE);
    }

    @Test
    void persistPayments_brandNull_isKeptAsNullNeverDefaultedToAString() {
        // brand só é obrigatório para CREDIT/DEBIT (validado antes deste
        // service rodar) - para PIX/WALLET, null deve chegar como null na
        // entidade, nunca virar uma string como "null" por engano.
        stubPaymentRepositorySave();
        List<PaymentEntity> result = service.persistPayments(List.of(payment(PaymentMethod.PIX, true, null, null)), 1L);

        assertThat(result.getFirst().getBrand()).isNull();
    }

    @Test
    void persistPayments_twoPaymentsWithSameMethod_bothArePersistedAndBothSurviveInTheReturnedList() {
        // Regressão do bug reportado em 21/09/2026 (ver decisoes.md): quando
        // este método devolvia um Map<PaymentMethod, PaymentEntity>, o
        // segundo pagamento com o mesmo method (aqui, os dois são CREDIT)
        // SOBRESCREVIA o primeiro no mapa - mesmo os dois tendo sido
        // gravados em T_PAYMENT - e o primeiro (que era o isDefault=true)
        // desaparecia do resultado. Com a List, os dois devem sobreviver, na
        // mesma ordem da requisição.
        stubPaymentRepositorySave();
        List<PaymentEntity> result = service.persistPayments(List.of(
                payment(PaymentMethod.CREDIT, true, CardBrand.VISA, null),
                payment(PaymentMethod.CREDIT, false, CardBrand.VISA, null)), 1L);

        assertThat(result).hasSize(2);
        verify(paymentRepository, times(2)).save(any());
        assertThat(result.getFirst().getDefaultMethod()).isTrue();
        assertThat(result.get(1).getDefaultMethod()).isFalse();
    }

    @Test
    void persistPayments_withTokens_persistsEachToken() {
        stubPaymentRepositorySave();
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "tok-123", "GATEWAY-X", null);

        service.persistPayments(List.of(payment(PaymentMethod.CREDIT, true, CardBrand.VISA, List.of(token))), 1L);

        PaymentTokenEntity saved = captureSavedToken();
        assertThat(saved.getPaymentId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("ACCESS_TOKEN");
        assertThat(saved.getToken()).isEqualTo("tok-123");
        assertThat(saved.getGateway()).isEqualTo("GATEWAY-X");
        assertThat(saved.getStatus()).isEqualTo(DomainStatus.PAYMENT_TOKEN_ACTIVE);
    }

    @Test
    void persistPayments_tokenWithNullExpirationDt_isKeptAsNull() {
        stubPaymentRepositorySave();
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "tok-123", "GATEWAY-X", null);

        service.persistPayments(List.of(payment(PaymentMethod.CREDIT, true, CardBrand.VISA, List.of(token))), 1L);

        assertThat(captureSavedToken().getExpirationDt()).isNull();
    }

    @Test
    void persistPayments_tokenWithBlankExpirationDt_isKeptAsNull() {
        stubPaymentRepositorySave();
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "tok-123", "GATEWAY-X", "   ");

        service.persistPayments(List.of(payment(PaymentMethod.CREDIT, true, CardBrand.VISA, List.of(token))), 1L);

        assertThat(captureSavedToken().getExpirationDt()).isNull();
    }

    @Test
    void persistPayments_tokenWithNumericExpirationDt_parsesToLong() {
        stubPaymentRepositorySave();
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "tok-123", "GATEWAY-X", "1800000000000");

        service.persistPayments(List.of(payment(PaymentMethod.CREDIT, true, CardBrand.VISA, List.of(token))), 1L);

        assertThat(captureSavedToken().getExpirationDt()).isEqualTo(1_800_000_000_000L);
    }

    @Test
    void persistPayments_paymentWithoutTokens_savesNoTokenAtAll() {
        stubPaymentRepositorySave();
        service.persistPayments(List.of(payment(PaymentMethod.PIX, true, null, null)), 1L);

        verify(paymentTokenRepository, never()).save(any());
    }

    private PaymentTokenEntity captureSavedToken() {
        ArgumentCaptor<PaymentTokenEntity> captor = ArgumentCaptor.forClass(PaymentTokenEntity.class);
        verify(paymentTokenRepository).save(captor.capture());
        return captor.getValue();
    }
}
