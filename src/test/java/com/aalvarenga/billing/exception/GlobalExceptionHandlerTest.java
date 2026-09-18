package com.aalvarenga.billing.exception;

import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.dto.response.QueryResponse;
import com.aalvarenga.billing.enums.ResultStatus;
import com.aalvarenga.billing.service.RequestLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link GlobalExceptionHandler} - o {@code @RestControllerAdvice} que
 * traduz falhas ANTES do controller conseguir chamar o service (JSON
 * malformado, Bean Validation) para o formato de resposta padronizado do
 * enunciado.
 *
 * <p>{@link HttpMessageNotReadableException} e
 * {@link MethodArgumentNotValidException} não têm construtores simples de
 * usar em teste (exigem objetos internos do Spring MVC como
 * {@code HttpInputMessage}/{@code MethodParameter}), então mockamos as
 * próprias exceções em vez de instanciá-las de verdade - o que basta aqui,
 * já que só usamos {@code getMessage()}/{@code getCause()}/
 * {@code getBindingResult()} delas.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private RequestLogService requestLogService;
    @Mock
    private JsonMapper objectMapper;
    @Mock
    private HttpServletRequest httpServletRequest;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(requestLogService, objectMapper);
        // Padrão: URI de "/api/v1/purchases" (criação de compra) - os testes
        // que exercitam especificamente a consulta sobrescrevem este stub.
        lenient().when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/purchases");
    }

    @Test
    void handleMalformedJson_returnsBadRequestWithRootMessage() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Unexpected token");
        when(ex.getCause()).thenReturn(null);

        ResponseEntity<Object> response = handler.handleMalformedJson(ex, httpServletRequest);
        PurchaseResponse body = (PurchaseResponse) response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body).isNotNull();
        assertThat(body.result()).isEqualTo(ResultStatus.ERROR);
        assertThat(body.code()).isEqualTo("400");
        assertThat(body.reason()).contains("Unexpected token");
        // Nenhum protocolo disponível nesse ponto (o Jackson nem conseguiu
        // montar o PurchaseRequest) - ver javadoc do método original.
        assertThat(body.protocol()).isNull();
    }

    @Test
    void handleMalformedJson_walksCauseChainToFindRootMessage() {
        // A exceção "de fora" (ex) não tem mensagem própria útil - a causa
        // raiz real (a mais profunda da cadeia) é o que importa.
        RuntimeException rootCause = new RuntimeException("actual root cause message");
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getCause()).thenReturn(rootCause);

        ResponseEntity<Object> response = handler.handleMalformedJson(ex, httpServletRequest);
        PurchaseResponse body = (PurchaseResponse) response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.reason()).contains("actual root cause message");
    }

    @Test
    void handleMalformedJson_fallsBackToDefaultMessageWhenRootMessageIsNull() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn(null);
        when(ex.getCause()).thenReturn(null);

        ResponseEntity<Object> response = handler.handleMalformedJson(ex, httpServletRequest);
        PurchaseResponse body = (PurchaseResponse) response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.reason()).contains("invalid payload");
    }

    @Test
    void handleMalformedJson_onQueryEndpoint_returnsQueryResponseShapeInstead() {
        // Mesma exceção, mas a URI é a da consulta de dados - o corpo do
        // erro precisa vir no formato de QueryResponse (sem "protocol",
        // com "products"/"bill" em vez de "product"/"billing"), não no de
        // PurchaseResponse - ver javadoc de handleMalformedJson.
        when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/purchases/query");
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        when(ex.getMessage()).thenReturn("Unexpected token");
        when(ex.getCause()).thenReturn(null);

        ResponseEntity<Object> response = handler.handleMalformedJson(ex, httpServletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(QueryResponse.class);
        QueryResponse body = (QueryResponse) response.getBody();
        assertThat(body.result()).isEqualTo(ResultStatus.ERROR);
        assertThat(body.reason()).contains("Unexpected token");
        assertThat(body.account()).isNull();
    }

    @Test
    void handleBeanValidation_withRecoverableProtocol_logsTheAttempt() {
        PurchaseRequest purchaseRequest = new PurchaseRequest(
                "WEB", "123", "PROTO-1", List.of(), List.of(), List.of(), List.of());

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("purchaseRequest", "channel", "channel is required");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(bindingResult.getTarget()).thenReturn(purchaseRequest);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"json\":true}");

        ResponseEntity<PurchaseResponse> response = handler.handleBeanValidation(ex);
        PurchaseResponse body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body).isNotNull();
        assertThat(body.reason()).isEqualTo("channel: channel is required");
        assertThat(body.protocol()).isEqualTo("PROTO-1");
        // Diferente do JSON malformado, aqui o objeto FOI construído com
        // sucesso, então a tentativa deve ser logada em T_LOG (ver javadoc).
        verify(requestLogService).log("PROTO-1", "ERROR", "400", "channel: channel is required",
                "{\"json\":true}", "{\"json\":true}");
    }

    @Test
    void handleBeanValidation_withoutRecoverableTarget_doesNotLog() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("purchaseRequest", "protocol", "protocol is required");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        // Target não é um PurchaseRequest (ou é null) - não há protocolo
        // para recuperar, então não devemos tentar logar.
        when(bindingResult.getTarget()).thenReturn(null);

        ResponseEntity<PurchaseResponse> response = handler.handleBeanValidation(ex);
        PurchaseResponse body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.protocol()).isNull();
        assertThat(body.reason()).isEqualTo("protocol: protocol is required");
        verify(requestLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleBeanValidation_usesDefaultReasonWhenNoFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of());
        when(bindingResult.getTarget()).thenReturn(null);

        ResponseEntity<PurchaseResponse> response = handler.handleBeanValidation(ex);
        PurchaseResponse body = response.getBody();

        assertThat(body).isNotNull();
        assertThat(body.reason()).isEqualTo("Invalid request body");
    }

    @Test
    void handleBeanValidation_whenSerializationFails_fallsBackToErrorPlaceholder() {
        PurchaseRequest purchaseRequest = new PurchaseRequest(
                "WEB", "123", "PROTO-2", List.of(), List.of(), List.of(), List.of());

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("purchaseRequest", "channel", "channel is required");
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        when(bindingResult.getTarget()).thenReturn(purchaseRequest);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("boom"));

        handler.handleBeanValidation(ex);

        verify(requestLogService).log(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.contains("<unable to serialize: boom>"),
                org.mockito.ArgumentMatchers.contains("<unable to serialize: boom>"));
    }
}
