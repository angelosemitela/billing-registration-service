package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.response.CancellationResponse;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.CancellationType;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link CancellationService}: a mesma fachada validar -&gt; persistir
 * -&gt; responder -&gt; logar (sempre) já testada em {@code PurchaseServiceTest},
 * agora para o fluxo de cancelamento. {@code cancellationValidationService} e
 * {@code cancellationOrchestrationService} são mockados - suas próprias
 * regras já têm cobertura dedicada em {@code CancellationValidationServiceTest}
 * e {@code CancellationOrchestrationServiceTest}; aqui só interessa a
 * ORQUESTRAÇÃO do fluxo entre eles (os três caminhos possíveis: sucesso,
 * {@link BusinessException} de negócio, exceção inesperada).
 */
@ExtendWith(MockitoExtension.class)
class CancellationServiceTest {

    @Mock
    private CancellationValidationService cancellationValidationService;
    @Mock
    private CancellationOrchestrationService cancellationOrchestrationService;
    @Mock
    private RequestLogService requestLogService;
    @Mock
    private JsonMapper objectMapper;

    private CancellationService service;

    @BeforeEach
    void setUp() {
        service = new CancellationService(cancellationValidationService, cancellationOrchestrationService, requestLogService, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private CancellationRequest request() {
        return new CancellationRequest("WEB", "1700000000000", "PROTO-CANCEL-1", "PROD_1", CancellationType.IMMEDIATE, "motivo", false, null);
    }

    @Test
    void process_happyPath_returns200AndLogsSuccessResult() {
        ProductEntity product = ProductEntity.builder().id(1L).build();
        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.IMMEDIATE, CancellationType.IMMEDIATE, List.of());
        CancellationResponse successResponse = CancellationResponse.success(
                "PROTO-CANCEL-1", "PROD_1", CancellationType.IMMEDIATE, CancellationType.IMMEDIATE,
                "CANCELLED", "CANCELLED", "1700000000000", null, null);
        when(cancellationValidationService.validate(any())).thenReturn(context);
        when(cancellationOrchestrationService.persistAndBuildResponse(any(), any())).thenReturn(successResponse);

        ResponseEntity<CancellationResponse> result = service.process(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().result()).isEqualTo(ResultStatus.SUCCESS);
        verify(requestLogService).log("PROTO-CANCEL-1", "SUCCESS", "200", "Success", "{}", "{}");
    }

    @Test
    void process_businessException_mapsToItsOwnStatusAndMessage() {
        when(cancellationValidationService.validate(any())).thenThrow(BusinessException.conflict("protocol already processed"));

        ResponseEntity<CancellationResponse> result = service.process(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(result.getBody().result()).isEqualTo(ResultStatus.ERROR);
        assertThat(result.getBody().code()).isEqualTo("409");
        assertThat(result.getBody().reason()).isEqualTo("protocol already processed");
        verify(requestLogService).log("PROTO-CANCEL-1", "ERROR", "409", "protocol already processed", "{}", "{}");
    }

    @Test
    void process_unexpectedException_returns500WithGenericMessage() {
        // Uma RuntimeException não prevista nunca pode "vazar" sem virar uma
        // resposta HTTP - mesma garantia já testada em PurchaseServiceTest.
        when(cancellationValidationService.validate(any())).thenThrow(new IllegalStateException("boom"));

        ResponseEntity<CancellationResponse> result = service.process(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody().code()).isEqualTo("500");
        assertThat(result.getBody().reason()).isEqualTo("Unexpected internal error");
        verify(requestLogService).log("PROTO-CANCEL-1", "ERROR", "500", "Unexpected internal error", "{}", "{}");
    }

    @Test
    void process_alwaysLogsRegardlessOfOutcome() {
        when(cancellationValidationService.validate(any())).thenThrow(BusinessException.badRequest("channel is required"));

        service.process(request());

        verify(requestLogService).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void process_orchestrationThrowsBusinessException_isAlsoMappedCorrectly() {
        // BusinessException pode vir tanto da validação quanto da própria
        // orquestração de persistência - o catch cobre os dois.
        ProductEntity product = ProductEntity.builder().id(1L).build();
        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.IMMEDIATE, CancellationType.IMMEDIATE, List.of());
        when(cancellationValidationService.validate(any())).thenReturn(context);
        when(cancellationOrchestrationService.persistAndBuildResponse(any(), any())).thenThrow(BusinessException.notFound("productId not found"));

        ResponseEntity<CancellationResponse> result = service.process(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().reason()).isEqualTo("productId not found");
    }
}
