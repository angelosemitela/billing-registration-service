package com.aalvarenga.billing.exception;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.CancellationResponse;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.dto.response.QueryResponse;
import com.aalvarenga.billing.service.RequestLogService;
import jakarta.servlet.http.HttpServletRequest;
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
     * construir o objeto de entrada, então não temos como recuperar o
     * "protocol" da requisição para gravar em T_LOG - essa é uma limitação
     * documentada em README/ANALISE.md.
     *
     * <p><b>Por que {@code ResponseEntity<Object>} e não um tipo fixo</b>:
     * este {@code @RestControllerAdvice} é GLOBAL - o Spring despacha
     * {@link HttpMessageNotReadableException} para cá não importa qual
     * controller/endpoint a lançou, então um único método precisa saber
     * responder {@code POST /api/v1/purchases} (contrato
     * {@link PurchaseResponse}, com {@code protocol}), {@code POST
     * /api/v1/purchases/query} (contrato {@link QueryResponse}, sem
     * {@code protocol}, com {@code products}/{@code bill} no lugar de
     * {@code product}/{@code billing}) e, desde 22/09/2026, {@code POST
     * /api/v1/purchases/cancel} (contrato {@link CancellationResponse}).
     * Dá pra registrar um {@code @ExceptionHandler} por endpoint só
     * diferenciando por {@code produces}/tipo de mídia, o que aqui não se
     * aplica (os três endpoints produzem {@code application/json}) - então
     * decidimos pelo {@code request URI}, que está sempre disponível
     * independente de qual DTO o Jackson tentou (e falhou) construir.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        String reason = "Malformed request body: " + rootMessage(ex);
        Object response;
        if (isQueryEndpoint(request)) {
            response = QueryResponse.error("400", reason);
        } else if (isCancelEndpoint(request)) {
            response = CancellationResponse.error("400", reason, null, null, null);
        } else {
            response = PurchaseResponse.error("400", reason, null);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private boolean isQueryEndpoint(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/query");
    }

    private boolean isCancelEndpoint(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/cancel");
    }

    /**
     * Falha de Bean Validation (ex: {@code channel} ausente). Diferente do
     * caso acima, aqui o objeto de entrada FOI construído com sucesso antes
     * da validação falhar, então conseguimos recuperar o protocolo original
     * e logar a tentativa normalmente.
     *
     * <p>Este handler cobre os dois endpoints que usam {@code @Valid} no
     * controller: {@code POST /api/v1/purchases} ({@link PurchaseRequest})
     * e, desde 22/09/2026, {@code POST /api/v1/purchases/cancel}
     * ({@link CancellationRequest}) - o corpo da consulta de dados
     * ({@code PurchaseQueryRequest}) não tem nenhuma anotação de Bean
     * Validation de propósito (ver javadoc da classe), então
     * {@link MethodArgumentNotValidException} nunca é lançada para
     * {@code POST /api/v1/purchases/query}. Diferenciamos pelo TIPO do
     * objeto que falhou a validação (via pattern matching de
     * {@code instanceof}) em vez de pelo {@code request URI} (diferente de
     * {@link #handleMalformedJson}) porque aqui já temos o objeto em mãos -
     * checar o tipo dele é mais direto do que precisar injetar
     * {@code HttpServletRequest} só para isso.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleBeanValidation(MethodArgumentNotValidException ex) {
        String reason = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request body");

        Object target = ex.getBindingResult().getTarget();
        if (target instanceof CancellationRequest cancellationRequest) {
            return handleCancellationBeanValidation(cancellationRequest, reason);
        }

        String protocol = null;
        String inputJson = null;
        if (target instanceof PurchaseRequest purchaseRequest) {
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

    private ResponseEntity<Object> handleCancellationBeanValidation(CancellationRequest cancellationRequest, String reason) {
        String protocol = cancellationRequest.protocolId();
        CancellationResponse response = CancellationResponse.error("400", reason, protocol, cancellationRequest.productId(), cancellationRequest.type());

        if (protocol != null) {
            requestLogService.log(protocol, response.result().name(), response.code(), response.reason(),
                    toJsonSafely(cancellationRequest), toJsonSafely(response));
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
