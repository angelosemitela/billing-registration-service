package com.aalvarenga.billing.exception;

import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.service.RequestLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * Captura, em um único lugar, todas as exceções que podem "escapar" do
 * fluxo normal do controller e as traduz para o formato de resposta
 * padronizado do enunciado ({@code result/code/reason/protocol/...}).
 *
 * <p>A grande maioria das falhas de negócio já é tratada dentro de
 * {@link com.aalvarenga.billing.service.PurchaseService} (que captura
 * {@link BusinessException} e monta a resposta na hora, para poder logar em
 * T_LOG). Este {@code @RestControllerAdvice} cobre os casos que acontecem
 * ANTES do controller sequer conseguir invocar o service - erros de
 * desserialização do JSON (ex: um valor de enum inválido, uma vírgula a
 * mais) e falhas de Bean Validation ({@code @NotBlank} etc. nos DTOs).
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final RequestLogService requestLogService;
    // Ver comentário equivalente em PurchaseService: Spring Boot 4.1 usa Jackson 3
    // por padrão, cujo bean auto-configurado é do tipo JsonMapper.
    private final JsonMapper objectMapper;

    /**
     * JSON malformado ou com um valor fora do enum esperado (ex:
     * {@code "type": "INVALID"}). Nesse ponto o Jackson nem conseguiu
     * construir o objeto {@link PurchaseRequest}, então não temos como
     * recuperar o "protocol" da requisição para gravar em T_LOG - essa é
     * uma limitação documentada em README/ANALISE.md.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<PurchaseResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        PurchaseResponse response = PurchaseResponse.error("400", "Malformed request body: " + rootMessage(ex), null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Falha de Bean Validation (ex: {@code channel} ausente). Diferente do
     * caso acima, aqui o objeto {@link PurchaseRequest} FOI construído com
     * sucesso antes da validação falhar, então conseguimos recuperar o
     * protocolo original e logar a tentativa normalmente.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PurchaseResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        String reason = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request body");

        String protocol = null;
        String inputJson = null;
        if (ex.getBindingResult().getTarget() instanceof PurchaseRequest purchaseRequest) {
            protocol = purchaseRequest.protocol();
            inputJson = toJsonSafely(purchaseRequest);
        }

        PurchaseResponse response = PurchaseResponse.error("400", reason, protocol);

        if (protocol != null) {
            requestLogService.log(protocol, response.result().name(), response.code(), response.reason(),
                    inputJson, toJsonSafely(response));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return Objects.requireNonNullElse(root.getMessage(), "invalid payload");
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "<unable to serialize: " + e.getMessage() + ">";
        }
    }
}
