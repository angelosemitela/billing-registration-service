package com.aalvarenga.billing.dto.response;

import com.aalvarenga.billing.enums.ResultStatus;

import java.util.List;

/**
 * Corpo da resposta do serviço, tanto para sucesso quanto para erro.
 *
 * <p>Nenhuma anotação de exclusão de nulos (como {@code @JsonInclude(NON_NULL)})
 * é usada aqui de propósito: o enunciado exibe explicitamente
 * {@code "account": null, "product": null...} no exemplo de erro, então
 * mantemos os campos sempre presentes no JSON (mesmo quando {@code null})
 * para bater exatamente com o contrato pedido.
 */
public record PurchaseResponse(
        ResultStatus result,
        String code,
        String reason,
        String protocol,
        List<AccountResponseItem> account,
        List<ProductResponseItem> product,
        List<PaymentResponseItem> payment,
        List<BillingResponseItem> billing
) {

    /** Fábrica de resposta de sucesso - deixa explícito, no código chamador, quais campos compõem um "sucesso". */
    public static PurchaseResponse success(String protocol,
                                            List<AccountResponseItem> account,
                                            List<ProductResponseItem> product,
                                            List<PaymentResponseItem> payment,
                                            List<BillingResponseItem> billing) {
        return new PurchaseResponse(ResultStatus.SUCCESS, "200", "Success", protocol, account, product, payment, billing);
    }

    /** Fábrica de resposta de erro: todas as estruturas de dados voltam nulas, conforme contrato do enunciado. */
    public static PurchaseResponse error(String code, String reason, String protocol) {
        return new PurchaseResponse(ResultStatus.ERROR, code, reason, protocol, null, null, null, null);
    }
}
