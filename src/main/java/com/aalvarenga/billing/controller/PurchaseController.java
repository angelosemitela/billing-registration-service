package com.aalvarenga.billing.controller;

import com.aalvarenga.billing.dto.request.PurchaseQueryRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.dto.response.QueryResponse;
import com.aalvarenga.billing.service.PurchaseQueryService;
import com.aalvarenga.billing.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de entrada do serviço.
 *
 * <p>O controller é intencionalmente "burro": ele só recebe o JSON, deixa o
 * Spring desserializá-lo e validá-lo estruturalmente ({@code @Valid}), e
 * delega TODO o resto (validação de negócio, persistência, log, tratamento
 * de erro) para {@link PurchaseService}. Isso mantém a camada web livre de
 * lógica de negócio - uma futura migração para, por exemplo, uma fila de
 * mensagens (Kafka) no lugar de REST, reaproveitaria o {@code PurchaseService}
 * praticamente sem alterações (ver README, seção "Evoluções futuras").
 */
@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchaseQueryService purchaseQueryService;

    /**
     * Registra uma compra/faturamento.
     *
     * <p>Sempre responde 200 OK com {@code result=SUCCESS} quando tudo dá
     * certo. Em caso de erro de negócio, responde com o código HTTP da
     * família 4XX mais adequado (400/404/409/412) e {@code result=ERROR} -
     * nunca lança uma exceção não tratada para quem chama o serviço.
     */
    @PostMapping
    public ResponseEntity<PurchaseResponse> registerPurchase(@Valid @RequestBody PurchaseRequest request) {
        return purchaseService.process(request);
    }

    /**
     * Consulta dados já persistidos (conta/produtos/pagamentos/faturas), por
     * {@code externalId} da conta ou por {@code productId}.
     *
     * <p><b>Por que {@code POST}, sendo uma leitura?</b> A spec HTTP
     * desaconselha corpo em requisições {@code GET} (muitos clientes/proxies
     * nem repassam), e o filtro de entrada tem 6 campos opcionais, incluindo
     * booleans com valor padrão {@code true} - dá pra modelar como query
     * params, mas fica bem menos legível do que um JSON. É o mesmo padrão
     * usado por APIs de busca complexa no mercado (ex: o endpoint
     * {@code _search} do Elasticsearch/OpenSearch também é {@code POST}
     * com corpo, apesar de ser uma leitura) - ver README, seção "Evoluções
     * futuras", para a alternativa (GET com query params) considerada e não
     * escolhida.
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> queryPurchaseData(@RequestBody PurchaseQueryRequest request) {
        return purchaseQueryService.process(request);
    }
}
