package com.aalvarenga.billing.cucumber.support;

import io.restassured.path.json.JsonPath;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Monta o corpo JSON da requisição de CANCELAMENTO
 * ({@code POST /api/v1/purchases/cancel}) usado pelos testes funcionais/E2E
 * (feature pedida pelo usuário em 22/09/2026 - ver
 * {@code cancelamento.feature}).
 *
 * <p>Reaproveita a fixture {@code src/test/resources/cucumber/cancelamento-base.json}
 * - o payload de exemplo exatamente como fornecido pelo usuário no pedido
 * desta feature - como ponto de partida, igual
 * {@link PurchaseRequestJsonBuilder#buildBaseRequestJson()} faz para a
 * compra. A diferença chave em relação à compra é que um cancelamento SEMPRE
 * se refere a um produto/fatura JÁ EXISTENTE: não dá para gerar um payload
 * "de fábrica" sem depender de nada externo. Por isso {@link #buildBaseRequestJson}
 * recebe o {@link JsonPath} da RESPOSTA da compra recém-registrada (ver
 * {@code CancellationApiSteps}) e recupera dela exatamente os 3 valores que
 * o pedido do usuário especificou entre parênteses:
 * <ul>
 *   <li>{@code productId} &lt;- {@code product[0].id}</li>
 *   <li>{@code refund[0].billId} &lt;- {@code billing[0].id}</li>
 *   <li>{@code refund[0].amount} &lt;- {@code billing[0].chargedValue}</li>
 * </ul>
 *
 * <p><b>Reaproveitamento de {@code applyFieldOverride}</b>: depois de montado,
 * este JSON é "mutilado" campo a campo pelos cenários de erro usando
 * {@link PurchaseRequestJsonBuilder#applyFieldOverride(String, String, String)}
 * diretamente (ver {@code CancellationApiSteps}) - aquele método já é
 * agnóstico ao schema (opera em qualquer JSON, dado um caminho e um valor),
 * então não haveria ganho em duplicá-lo aqui. Não promovemos, nesta v1, essa
 * mecânica de mutação para uma classe utilitária própria compartilhada
 * (ex: um {@code JsonFieldOverrideSupport}) - critério idêntico ao já
 * registrado em {@code CancellationValidationService} para a duplicação de
 * {@code validateTransactionDt}: com só 2 consumidores hoje (compra e
 * cancelamento), a extração fica registrada como candidato natural para
 * quando um terceiro consumidor aparecer, em vez de feita preventivamente.
 */
@Component
public class CancellationRequestJsonBuilder {

    private static final String FIXTURE_RESOURCE = "/cucumber/cancelamento-base.json";

    /**
     * Gera um corpo de requisição de cancelamento válido, com
     * {@code protocolId} único e {@code productId}/{@code refund[0].billId}/
     * {@code refund[0].amount} recuperados da resposta da compra informada.
     *
     * @param purchaseResponse resposta (200/SUCCESS) de
     *                          {@code POST /api/v1/purchases} da compra que
     *                          este cancelamento vai se referir
     */
    public GeneratedCancellationRequest buildBaseRequestJson(JsonPath purchaseResponse) {
        String protocolId = UniqueTestData.uniqueValue("CANCEL");
        String productId = purchaseResponse.getString("product[0].id");
        String billId = purchaseResponse.getString("billing[0].id");
        String chargedValue = purchaseResponse.getString("billing[0].chargedValue");

        String json = readTemplateResource()
                .replace("__TRANSACTION_DT__", UniqueTestData.nowAsEpochMillis())
                .replace("__PROTOCOL__", protocolId)
                .replace("__PRODUCT_ID__", productId)
                .replace("__BILL_ID__", billId)
                .replace("__CHARGED_VALUE__", chargedValue);

        return new GeneratedCancellationRequest(json, protocolId, productId);
    }

    private String readTemplateResource() {
        try (InputStream inputStream = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Fixture não encontrada no classpath: " + FIXTURE_RESOURCE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a fixture de requisição base (" + FIXTURE_RESOURCE + ")", e);
        }
    }
}
