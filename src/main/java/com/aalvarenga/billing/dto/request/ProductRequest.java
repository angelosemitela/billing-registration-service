package com.aalvarenga.billing.dto.request;

import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.enums.RecurrenceFrequency;

import java.math.BigDecimal;

/**
 * Estrutura {@code product} da entrada - um produto/plano comprado.
 *
 * <p><b>Nota:</b> o nome do campo {@code isExpiriationService} é mantido
 * EXATAMENTE como veio especificado no contrato de entrada (com o erro de
 * digitação "Expiriation"), pois o campo é lido do JSON por nome - mudar a
 * grafia aqui quebraria a compatibilidade com quem chama o serviço. Ver nota
 * em README/ANALISE.md.
 *
 * @param codeId               identificador do produto no sistema externo
 * @param name                 nome do produto
 * @param type                 ONESHOT ou RECURRENCE
 * @param isExpiriationService se o serviço expira ao final da vigência
 * @param recurrenceFrequency  MONTH ou ANNUAL (obrigatório apenas para RECURRENCE)
 * @param productValue         valor do produto
 * @param discountValue        valor do desconto concedido
 * @param discountCycles       número de ciclos de vigência do desconto
 * @param currency             moeda (validada contra T_DOMAIN_CURRENCY)
 * @param isTrial              se a compra é trial (cortesia)
 * @param trialDays            duração do trial em dias (obrigatório se isTrial=true)
 */
public record ProductRequest(
        String codeId,
        String name,
        ProductType type,
        Boolean isExpiriationService,
        RecurrenceFrequency recurrenceFrequency,
        BigDecimal productValue,
        BigDecimal discountValue,
        Integer discountCycles,
        String currency,
        Boolean isTrial,
        Integer trialDays
) {
}
