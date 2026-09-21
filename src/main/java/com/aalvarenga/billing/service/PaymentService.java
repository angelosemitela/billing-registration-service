package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.TokenRequest;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.PaymentTokenEntity;
import com.aalvarenga.billing.repository.PaymentRepository;
import com.aalvarenga.billing.repository.PaymentTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
     * <p><strong>Correção de bug (21/09/2026)</strong>: este método RETORNAVA
     * um {@code Map<PaymentMethod, PaymentEntity>}, indexado só pelo
     * {@code method} (CREDIT/DEBIT/PIX/WALLET). Como o schema de entrada
     * permite MAIS DE UM pagamento com o mesmo {@code method} na mesma
     * compra (ex: dois cartões CREDIT, um deles marcado como
     * {@code isDefault}), o {@code Map.put} do segundo pagamento
     * SOBRESCREVIA silenciosamente a entrada do primeiro - ambos eram
     * gravados corretamente em {@code T_PAYMENT}, mas só o ÚLTIMO de cada
     * método sobrevivia no mapa devolvido, quebrando tudo que dependia dele
     * (resolução do pagamento "default", montagem da resposta, vínculo de
     * fatura a pagamento). Ver {@code decisoes.md} para o relato completo
     * (bug reportado com evidências de banco/requisição/resposta). Por isso
     * agora devolvemos uma {@link List}, na mesma ordem de entrada, SEM
     * perder nenhum pagamento persistido - quem usa o resultado decide como
     * localizar um pagamento específico (ver {@link PurchaseOrchestrationService}
     * e {@link BillingService}).
     *
     * @return lista de entidades persistidas, na ordem em que vieram na
     * requisição.
     */
    public List<PaymentEntity> persistPayments(List<PaymentRequest> payments, Long accountId) {
        List<PaymentEntity> persisted = new ArrayList<>();
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
            persisted.add(entity);
        }
        return persisted;
    }

    private void persistTokens(List<TokenRequest> tokens, Long paymentId) {
        if (tokens == null) {
            return;
        }
        for (TokenRequest token : tokens) {
            Long expirationDt = (token.expirationDt() == null || token.expirationDt().isBlank())
                    ? null
                    : Long.parseLong(token.expirationDt());

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
