package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.response.CancellationResponse;
import com.aalvarenga.billing.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fachada (Facade) do caso de uso "cancelar um produto" -
 * {@code POST /api/v1/purchases/cancel}. Mesma estrutura de
 * {@link PurchaseService}: valida -&gt; persiste -&gt; responde -&gt; loga em
 * {@code T_LOG} (sempre, sucesso ou erro), mantendo o controller livre de
 * qualquer lógica além de traduzir HTTP para chamada de método Java.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationService {

    private final CancellationValidationService cancellationValidationService;
    private final CancellationOrchestrationService cancellationOrchestrationService;
    private final RequestLogService requestLogService;
    private final JsonMapper objectMapper;

    public ResponseEntity<CancellationResponse> process(CancellationRequest request) {
        String inputJson = toJsonSafely(request);
        CancellationResponse response;
        HttpStatus status;

        try {
            ValidatedCancellationContext context = cancellationValidationService.validate(request);
            response = cancellationOrchestrationService.persistAndBuildResponse(request, context);
            status = HttpStatus.OK;
        } catch (BusinessException businessException) {
            response = CancellationResponse.error(
                    String.valueOf(businessException.getStatus().value()),
                    businessException.getMessage(),
                    request.protocolId(),
                    request.productId(),
                    request.type());
            status = businessException.getStatus();
        } catch (Exception unexpected) {
            log.error("Unexpected error while processing cancellation protocolId={}", request.protocolId(), unexpected);
            response = CancellationResponse.error("500", "Unexpected internal error", request.protocolId(), request.productId(), request.type());
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String outputJson = toJsonSafely(response);
        requestLogService.log(request.protocolId(), response.result().name(), response.code(), response.reason(), inputJson, outputJson);

        return ResponseEntity.status(status).body(response);
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return "<unable to serialize: " + e.getMessage() + ">";
        }
    }
}
