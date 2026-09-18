package com.aalvarenga.billing.dto.response;

import com.aalvarenga.billing.enums.ResultStatus;

import java.util.List;

/**
 * Corpo da resposta da consulta de dados ({@code POST /api/v1/purchases/query}),
 * tanto para sucesso quanto para erro.
 *
 * <p>Mesma decisão de design de {@link PurchaseResponse}: nenhum
 * {@code @JsonInclude(NON_NULL)}, para que os campos apareçam explicitamente
 * como {@code null} no JSON de erro (mesmo padrão do anexo original).
 *
 * <p>Os nomes dos campos aqui seguem EXATAMENTE o anexo do usuário
 * ({@code account}/{@code products}/{@code payment}/{@code bill} - note a
 * inconsistência de plural entre eles, mantida de propósito por ser
 * exatamente o contrato pedido, assim como o enunciado original já tinha
 * inconsistências semelhantes entre "product"/"account" em
 * {@link PurchaseResponse}).
 */
public record QueryResponse(
        ResultStatus result,
        String code,
        String reason,
        List<QueryAccountItem> account,
        List<QueryProductItem> products,
        List<QueryPaymentItem> payment,
        List<QueryBillItem> bill
) {

    public static QueryResponse success(List<QueryAccountItem> account,
                                         List<QueryProductItem> products,
                                         List<QueryPaymentItem> payment,
                                         List<QueryBillItem> bill) {
        return new QueryResponse(ResultStatus.SUCCESS, "200", "Success", account, products, payment, bill);
    }

    public static QueryResponse error(String code, String reason) {
        return new QueryResponse(ResultStatus.ERROR, code, reason, null, null, null, null);
    }
}
