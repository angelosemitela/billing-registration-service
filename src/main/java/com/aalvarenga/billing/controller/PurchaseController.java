package com.aalvarenga.billing.controller;

import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
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
 * delega TO.DO o resto (validação de negócio, persistência, log, tratamento
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
}
