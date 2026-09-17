package com.aalvarenga.billing.service;

import com.aalvarenga.billing.config.BillingProperties;
import com.aalvarenga.billing.entity.LogEntity;
import com.aalvarenga.billing.repository.LogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Cobre {@link RequestLogService}, em especial a regra de truncamento de
 * campos antes de gravar em {@code T_LOG} (ver javadoc da classe e da
 * entidade {@link LogEntity} sobre o motivo do truncamento existir). Não
 * testamos aqui o comportamento transacional de
 * {@code @Transactional(propagation = REQUIRES_NEW)} - isso é responsabilidade
 * do container Spring em runtime, não é algo que um teste de unidade com
 * Mockito consiga exercitar (ver README, seção "Testcontainers", para o
 * caminho recomendado para testes de integração que cobririam isso).
 */
@ExtendWith(MockitoExtension.class)
class RequestLogServiceTest {

    @Mock
    private LogRepository logRepository;

    private RequestLogService service;

    @BeforeEach
    void setUp() {
        // maxPayloadLength = 16000 nos testes "normais" (mesmo valor usado em
        // produção, ver application.yml) - só o teste de truncamento de
        // input/output usa uma instância própria com um limite bem menor.
        BillingProperties properties = new BillingProperties(new BillingProperties.Log(16000));
        service = new RequestLogService(logRepository, properties);
    }

    @Test
    void savesLogEntityWithAllFieldsWhenWithinLimits() {
        service.log("PROTO-1", "SUCCESS", "200", "Success", "{\"in\":1}", "{\"out\":1}");

        LogEntity saved = captureSavedEntity();
        assertThat(saved.getProtocol()).isEqualTo("PROTO-1");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getCode()).isEqualTo("200");
        assertThat(saved.getReason()).isEqualTo("Success");
        assertThat(saved.getInput()).isEqualTo("{\"in\":1}");
        assertThat(saved.getOutput()).isEqualTo("{\"out\":1}");
    }

    @Test
    void truncatesReasonAt500Characters() {
        String longReason = "x".repeat(600);

        service.log("PROTO-2", "ERROR", "400", longReason, "in", "out");

        LogEntity saved = captureSavedEntity();
        assertThat(saved.getReason()).hasSize(500);
        assertThat(saved.getReason()).isEqualTo("x".repeat(500));
    }

    @Test
    void truncatesInputAndOutputAtConfiguredMaxPayloadLength() {
        // Limite bem menor que o de produção, só para o teste não precisar
        // gerar strings gigantes para provar a regra de truncamento.
        BillingProperties shortLimit = new BillingProperties(new BillingProperties.Log(5));
        RequestLogService serviceWithShortLimit = new RequestLogService(logRepository, shortLimit);

        serviceWithShortLimit.log("PROTO-3", "SUCCESS", "200", "ok", "1234567890", "abcdefghij");

        LogEntity saved = captureSavedEntity();
        assertThat(saved.getInput()).isEqualTo("12345");
        assertThat(saved.getOutput()).isEqualTo("abcde");
    }

    @Test
    void nullInputAndOutputBecomeEmptyStringInsteadOfNull() {
        // T_LOG.INPUT/OUTPUT são NOT NULL no schema (ver migration V2) - o
        // truncamento trata null como string vazia, nunca deixa passar null
        // para o INSERT.
        service.log("PROTO-4", "ERROR", "400", "some error", null, null);

        LogEntity saved = captureSavedEntity();
        assertThat(saved.getInput()).isEmpty();
        assertThat(saved.getOutput()).isEmpty();
    }

    @Test
    void nullReasonBecomesEmptyStringInsteadOfNull() {
        service.log("PROTO-5", "ERROR", "400", null, "in", "out");

        LogEntity saved = captureSavedEntity();
        assertThat(saved.getReason()).isEmpty();
    }

    private LogEntity captureSavedEntity() {
        ArgumentCaptor<LogEntity> captor = ArgumentCaptor.forClass(LogEntity.class);
        verify(logRepository).save(captor.capture());
        return captor.getValue();
    }
}
