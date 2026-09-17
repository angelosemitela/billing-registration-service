package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.TokenRequest;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.PaymentTokenEntity;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.repository.PaymentRepository;
import com.aalvarenga.billing.repository.PaymentTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persiste a estrutura {@code payment} da requisição.
 *
 * <p>Conforme decisão registrada em README/ANALISE.md (pergunta 4 feita ao
 * usuário): os métodos de pagamento e seus tokens são SEMPRE persistidos
 * quando informados - mesmo em uma compra 100% trial (chargedValue = 0) -
 * porque serão necessários para cobranças futuras de recorrência. O que muda
 * é apenas se a estrutura "payment" aparece ou não na RESPOSTA (ver
 * {@link PurchaseOrchestrationService}).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTokenRepository paymentTokenRepository;

    /**
     * Persiste todos os métodos de pagamento (e seus tokens) da requisição.
     *
     * @return mapa {@link PaymentMethod} -> entidade persistida, na ordem em
     * que vieram na requisição (usado depois para vincular faturas ao método
     * de pagamento usado e para achar o pagamento "default").
     */
    public Map<PaymentMethod, PaymentEntity> persistPayments(List<PaymentRequest> payments, Long accountId) {
        Map<PaymentMethod, PaymentEntity> persisted = new LinkedHashMap<>();
        if (payments == null) {
            return persisted;
        }
        for (PaymentRequest request : payments) {
            PaymentEntity entity = paymentRepository.save(PaymentEntity.builder()
                    .accountId(accountId)
                    .method(request.method().name())
                    .issuer(request.issuer())
                    .cardNumber(request.cardNumber())
                    .expiration(request.expiration())
                    .multiple(request.isMultiple())
                    .defaultMethod(request.isDefault())
                    .installments(String.valueOf(request.installments()))
                    .status(DomainStatus.PAYMENT_ACTIVE)
                    .brand(request.brand() != null ? request.brand().name() : null)
                    .build());

            persistTokens(request.token(), entity.getId());
            persisted.put(request.method(), entity);
        }
        return persisted;
    }

    private void persistTokens(List<TokenRequest> tokens, Long paymentId) {
        if (tokens == null) {
            return;
        }
        for (TokenRequest token : tokens) {
            Long expirationDt = (token.expirationDate() == null || token.expirationDate().isBlank())
                    ? null
                    : Long.parseLong(token.expirationDate());

            paymentTokenRepository.save(PaymentTokenEntity.builder()
                    .paymentId(paymentId)
                    .name(token.name())
                    .token(token.id())
                    .gateway(token.gateway())
                    .expirationDt(expirationDt)
                    .status(DomainStatus.PAYMENT_TOKEN_ACTIVE)
                    .build());
        }
    }
}
