package com.aalvarenga.billing.dto.request;

import com.aalvarenga.billing.enums.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estrutura {@code billing} da entrada - uma fatura a ser gerada para um
 * produto comprado (vinculado pelo {@code codeId}, que deve bater com o
 * {@code codeId} de algum elemento de {@code product}).
 *
 * @param codeId        identificador do produto ao qual esta fatura se refere
 * @param productValue  valor bruto do produto nesta fatura
 * @param discountValue valor de desconto aplicado nesta fatura
 * @param taxValue      soma das taxas (deve bater com a soma de {@code tax[].value})
 * @param chargedValue  valor efetivamente cobrado (productValue - discountValue + taxValue)
 * @param currency      moeda (validada contra T_DOMAIN_CURRENCY)
 * @param transactionId identificador único da transação no meio de pagamento
 * @param installments  número de parcelas
 * @param provider      provedor/adquirente que processou a cobrança
 * @param paymentMethod método de pagamento utilizado nesta fatura
 * @param tax           detalhamento das taxas que compõem {@code taxValue}
 */
public record BillingRequest(
        String codeId,
        BigDecimal productValue,
        BigDecimal discountValue,
        BigDecimal taxValue,
        BigDecimal chargedValue,
        String currency,
        String transactionId,
        Integer installments,
        String provider,
        PaymentMethod paymentMethod,
        List<TaxRequest> tax
) {
}
