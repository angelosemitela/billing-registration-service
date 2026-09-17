package com.aalvarenga.billing.dto.request;

import com.aalvarenga.billing.enums.CardBrand;
import com.aalvarenga.billing.enums.PaymentMethod;

import java.util.List;

/**
 * Estrutura {@code payment} da entrada - um método de pagamento associado à compra.
 *
 * @param method       CREDIT, DEBIT, PIX ou WALLET
 * @param issuer       instituição emissora (opcional)
 * @param cardNumber   número do cartão (obrigatório para CREDIT/DEBIT)
 * @param expiration   validade do cartão no formato MM/YY (obrigatório para CREDIT/DEBIT)
 * @param isMultiple   se o cartão é múltiplo (crédito + débito) (obrigatório para CREDIT/DEBIT)
 * @param isDefault    se é o método padrão da conta (exatamente 1 deve ser true)
 * @param installments número de parcelas (1 para a maioria dos métodos; 1-12 para CREDIT/WALLET)
 * @param token        tokens de tokenização gerados para este método
 * @param brand        bandeira do cartão - VISA, MASTERCARD, AMEX ou ELO
 *                     (obrigatório para CREDIT/DEBIT; acrescentado em
 *                     17/09/2026, ver README, seção "Evoluções pedidas")
 */
public record PaymentRequest(
        PaymentMethod method,
        String issuer,
        String cardNumber,
        String expiration,
        Boolean isMultiple,
        Boolean isDefault,
        Integer installments,
        List<TokenRequest> token,
        CardBrand brand
) {
}
