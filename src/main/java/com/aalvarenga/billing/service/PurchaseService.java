package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fachada (Facade) do caso de uso "registrar uma compra": é o único ponto de
 * entrada chamado pelo controller, e é quem decide a ORDEM das operações:
 * validar -> persistir -> responder -> logar (sempre, mesmo em erro).
 *
 * <p>Centralizar esse fluxo aqui (em vez de deixar o controller fazer
 * try/catch) mantém o controller "burro" (só traduz HTTP <-> chamada de
 * método Java) e deixa este service livre de detalhes de HTTP/Servlet -
 * uma separação de responsabilidades clássica em arquiteturas em camadas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseValidationService purchaseValidationService;
    private final PurchaseOrchestrationService purchaseOrchestrationService;
    private final RequestLogService requestLogService;
    // Spring Boot 4.1 passou a usar o Jackson 3 por padrão (pacote "tools.jackson.*",
    // no lugar do antigo "com.fasterxml.jackson.*"). O bean de auto-configuração
    // agora é do tipo JsonMapper (subclasse "pronta para JSON" de ObjectMapper) -
    // ver README, seção "Inconsistências/decisões", para o contexto completo dessa
    // migração e das alternativas consideradas (Jackson 2 via módulo de compatibilidade).
    private final JsonMapper objectMapper;

    public ResponseEntity<PurchaseResponse> process(PurchaseRequest request) {
        String inputJson = toJsonSafely(request);
        PurchaseResponse response;
        HttpStatus status;

        try {
            ValidatedPurchaseContext context = purchaseValidationService.validate(request);
            response = purchaseOrchestrationService.persistAndBuildResponse(request, context);
            status = HttpStatus.OK;
        } catch (BusinessException businessException) {
            response = PurchaseResponse.error(
                    String.valueOf(businessException.getStatus().value()),
                    businessException.getMessage(),
                    request.protocol());
            status = businessException.getStatus();
        } catch (Exception unexpected) {
            // Qualquer falha não prevista (bug, indisponibilidade do banco, etc) ainda
            // precisa virar uma resposta HTTP e um registro em T_LOG - nunca deixamos a
            // exceção "vazar" sem resposta para quem chamou o serviço.
            log.error("Unexpected error while processing purchase protocol={}", request.protocol(), unexpected);
            response = PurchaseResponse.error("500", "Unexpected internal error", request.protocol());
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String outputJson = toJsonSafely(response);
        requestLogService.log(request.protocol(), response.result().name(), response.code(), response.reason(), inputJson, outputJson);

        return ResponseEntity.status(status).body(response);
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            // No Jackson 3, JacksonException é unchecked (RuntimeException) - antes,
            // no Jackson 2, JsonProcessingException era checked. Mantemos o try/catch
            // mesmo assim: é uma falha "esperada" (objeto não serializável) e não
            // queremos que ela derrube a resposta HTTP só porque o log deu errado.
            return "<unable to serialize: " + e.getMessage() + ">";
        }
    }
}
