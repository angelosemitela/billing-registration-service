package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.enums.ResultStatus;
import com.aalvarenga.billing.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link PurchaseService}: a fachada que decide a ORDEM
 * validar -&gt; persistir -&gt; responder -&gt; logar (sempre, mesmo em erro) -
 * ver javadoc da classe. O foco aqui é garantir que os TRÊS caminhos
 * possíveis (sucesso, {@link BusinessException} de negócio, exceção
 * inesperada) terminam sempre com uma resposta HTTP e uma chamada a
 * {@link RequestLogService#log}, nunca deixando uma exceção "vazar" sem log.
 *
 * <p>{@code purchaseValidationService} e {@code purchaseOrchestrationService}
 * são mockados: suas próprias regras já têm cobertura dedicada em outros
 * testes (ex: {@code PurchaseValidationServiceTest},
 * {@code PurchaseOrchestrationServiceTest}) - aqui só interessa a ORQUESTRAÇÃO
 * do fluxo entre eles.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseValidationService purchaseValidationService;
    @Mock
    private PurchaseOrchestrationService purchaseOrchestrationService;
    @Mock
    private RequestLogService requestLogService;
    @Mock
    private JsonMapper objectMapper;

    private PurchaseService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseService(purchaseValidationService, purchaseOrchestrationService, requestLogService, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private PurchaseRequest purchaseRequest() {
        AccountRequest account = new AccountRequest(null, "João", "EXT-1", null, null, null, "joao@mail.com", true);
        ProductRequest product = new ProductRequest("1", "Produto", ProductType.ONESHOT, false, null, BigDecimal.TEN, BigDecimal.ZERO, null, "BRL", false, null);
        return new PurchaseRequest("WEB", "1800000000000", "PROTO-1", List.of(account), List.of(product), null, null);
    }

    @Test
    void process_happyPath_returns200AndLogsSuccessResult() {
        ValidatedPurchaseContext context = new ValidatedPurchaseContext(1_800_000_000_000L, null);
        PurchaseResponse successResponse = PurchaseResponse.success("PROTO-1", null, null, null, null);
        when(purchaseValidationService.validate(any())).thenReturn(context);
        when(purchaseOrchestrationService.persistAndBuildResponse(any(), any())).thenReturn(successResponse);

        ResponseEntity<PurchaseResponse> result = service.process(purchaseRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().result()).isEqualTo(ResultStatus.SUCCESS);
        verify(requestLogService).log("PROTO-1", "SUCCESS", "200", "Success", "{}", "{}");
    }

    @Test
    void process_businessException_mapsToItsOwnStatusAndMessage() {
        when(purchaseValidationService.validate(any())).thenThrow(BusinessException.conflict("protocol already processed"));

        ResponseEntity<PurchaseResponse> result = service.process(purchaseRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.getBody().result()).isEqualTo(ResultStatus.ERROR);
        assertThat(result.getBody().code()).isEqualTo("409");
        assertThat(result.getBody().reason()).isEqualTo("protocol already processed");
        verify(requestLogService).log("PROTO-1", "ERROR", "409", "protocol already processed", "{}", "{}");
    }

    @Test
    void process_unexpectedException_returns500WithGenericMessage() {
        // Uma RuntimeException não prevista (bug, indisponibilidade do banco
        // etc.) nunca pode "vazar" sem virar uma resposta HTTP - ver javadoc
        // da classe.
        when(purchaseValidationService.validate(any())).thenThrow(new IllegalStateException("boom"));

        ResponseEntity<PurchaseResponse> result = service.process(purchaseRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody().code()).isEqualTo("500");
        assertThat(result.getBody().reason()).isEqualTo("Unexpected internal error");
        verify(requestLogService).log("PROTO-1", "ERROR", "500", "Unexpected internal error", "{}", "{}");
    }

    @Test
    void process_alwaysLogsRegardlessOfOutcome() {
        when(purchaseValidationService.validate(any())).thenThrow(BusinessException.badRequest("channel is required"));

        service.process(purchaseRequest());

        verify(requestLogService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_orchestrationThrowsBusinessException_isAlsoMappedCorrectly() {
        // BusinessException pode vir tanto da validação quanto da própria
        // orquestração de persistência (ex: uma constraint do banco que
        // escapou da validação em memória) - o catch cobre os dois.
        ValidatedPurchaseContext context = new ValidatedPurchaseContext(1_800_000_000_000L, null);
        when(purchaseValidationService.validate(any())).thenReturn(context);
        when(purchaseOrchestrationService.persistAndBuildResponse(any(), any())).thenThrow(BusinessException.notFound("account not found"));

        ResponseEntity<PurchaseResponse> result = service.process(purchaseRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().reason()).isEqualTo("account not found");
    }
}
