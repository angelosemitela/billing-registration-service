package com.aalvarenga.billing.dto.response;

import com.aalvarenga.billing.enums.CancellationType;
import com.aalvarenga.billing.enums.ResultStatus;

import java.util.List;

/**
 * Corpo da resposta de {@code POST /api/v1/purchases/cancel}, tanto para
 * sucesso quanto para erro.
 *
 * <p>Mesmo racional de {@link PurchaseResponse} sobre manter TODOS os campos
 * sempre presentes no JSON (mesmo {@code null} em caso de erro): o anexo
 * original só mostra {@code result}/{@code code}/{@code reason} no exemplo
 * de erro, mas seguimos o padrão já estabelecido no restante do serviço,
 * onde o formato de erro é um "subconjunto nulo" do formato de sucesso, não
 * uma estrutura JSON diferente.
 *
 * @param protocol      eco do {@code protocolId} da entrada. Chamado
 *                      {@code protocol} (sem o sufixo {@code Id}) na SAÍDA
 *                      porque é exatamente assim que o anexo original
 *                      especifica o campo de resposta - mais uma pequena
 *                      inconsistência de nomenclatura do próprio anexo
 *                      (entrada {@code protocolId} x saída {@code protocol}),
 *                      documentada em {@code decisoes.md} e implementada tal
 *                      qual, sem tentar "corrigir" o contrato.
 * @param productId     ID do produto, formatado com o prefixo padrão da API
 *                      ({@code AssetIdFormatter.product}) - canônico, mesmo
 *                      que a entrada tenha informado o ID técnico puro
 *                      (ver {@code CancellationOrchestrationService}).
 * @param inputType     tipo de cancelamento exatamente como veio na entrada.
 * @param processedType tipo de cancelamento EFETIVAMENTE processado - pode
 *                      divergir de {@code inputType} (ver
 *                      {@link CancellationType}, nota sobre o downgrade
 *                      SCHEDULED -&gt; IMMEDIATE).
 * @param productSt     status do produto após o processamento, com base em
 *                      {@code T_DOMAIN_PRODUCT_STATUS.BACKEND_VALUE}.
 * @param cancellationSt status de cancelamento do produto após o
 *                      processamento, com base em
 *                      {@code T_DOMAIN_PRODUCT_CANCELLATION_STATUS.BACKEND_VALUE}.
 * @param cancellationDt data agendada/efetivada do cancelamento
 *                      ({@code T_PRODUCT.CANCELLATION_SCH_DT} já
 *                      atualizado), como String em epoch millis;
 *                      {@code null} quando o produto ficou sem nenhum
 *                      cancelamento agendado (ex: após um
 *                      {@code WITHDRAW_CANCELLATION}).
 * @param nextBillDt    {@code T_PRODUCT.NEXT_BILL_DT} - só preenchido quando
 *                      {@code processedType=WITHDRAW_CANCELLATION}
 *                      (conforme o anexo); {@code null} em qualquer outro caso.
 * @param refund        estornos processados, um item por fatura; {@code null}
 *                      quando a entrada não tinha {@code hasRefund=true}.
 */
public record CancellationResponse(
        ResultStatus result,
        String code,
        String reason,
        String protocol,
        String productId,
        CancellationType inputType,
        CancellationType processedType,
        String productSt,
        String cancellationSt,
        String cancellationDt,
        String nextBillDt,
        List<RefundResponseItem> refund
) {

    /** Fábrica de resposta de sucesso - deixa explícito, no código chamador, quais campos compõem um "sucesso". */
    public static CancellationResponse success(String protocol,
                                                String productId,
                                                CancellationType inputType,
                                                CancellationType processedType,
                                                String productSt,
                                                String cancellationSt,
                                                String cancellationDt,
                                                String nextBillDt,
                                                List<RefundResponseItem> refund) {
        return new CancellationResponse(ResultStatus.SUCCESS, "200", "Success", protocol, productId, inputType, processedType,
                productSt, cancellationSt, cancellationDt, nextBillDt, refund);
    }

    /**
     * Fábrica de resposta de erro: só ecoa o que já era conhecido ANTES da
     * falha (protocolo/produto/tipo de entrada, quando disponíveis) - tudo o
     * que dependeria de processamento bem-sucedido (status calculados,
     * datas, estornos) volta {@code null}.
     */
    public static CancellationResponse error(String code, String reason, String protocol, String productId, CancellationType inputType) {
        return new CancellationResponse(ResultStatus.ERROR, code, reason, protocol, productId, inputType, null, null, null, null, null, null);
    }
}
