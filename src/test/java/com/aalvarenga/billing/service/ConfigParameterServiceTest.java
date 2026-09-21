package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.ConfigParameterEntity;
import com.aalvarenga.billing.repository.ConfigParameterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link ConfigParameterService} - mesmo racional fail-safe de
 * {@link FeatureToggleServiceTest} (parâmetro desconhecido nunca lança
 * exceção, só é tratado como "não configurado"), aqui aplicado a um VALOR de
 * texto livre em vez de um booleano, incluindo o atalho
 * {@link ConfigParameterService#getLongValue} para o caso de um valor
 * numérico mal formado no banco.
 */
@ExtendWith(MockitoExtension.class)
class ConfigParameterServiceTest {

    @Mock
    private ConfigParameterRepository configParameterRepository;

    private ConfigParameterService service;

    @Test
    void getValue_returnsValueWhenParameterExists() {
        service = new ConfigParameterService(configParameterRepository);
        when(configParameterRepository.findByParameterName("SOME_PARAM"))
                .thenReturn(Optional.of(ConfigParameterEntity.builder().value("some-value").build()));

        assertThat(service.getValue("SOME_PARAM")).contains("some-value");
    }

    @Test
    void getValue_returnsEmptyWhenParameterIsNotFound_failSafe() {
        service = new ConfigParameterService(configParameterRepository);
        when(configParameterRepository.findByParameterName("UNKNOWN_PARAM")).thenReturn(Optional.empty());

        assertThat(service.getValue("UNKNOWN_PARAM")).isEmpty();
    }

    @Test
    void getLongValue_parsesNumericValue() {
        service = new ConfigParameterService(configParameterRepository);
        when(configParameterRepository.findByParameterName("SOME_DATE"))
                .thenReturn(Optional.of(ConfigParameterEntity.builder().value("1800000000000").build()));

        assertThat(service.getLongValue("SOME_DATE")).contains(1_800_000_000_000L);
    }

    @Test
    void getLongValue_nonNumericValue_isTreatedAsNotConfigured() {
        // Valor presente no banco mas mal formado (erro de configuração, não
        // da aplicação) - degrada para "não configurado" em vez de propagar
        // NumberFormatException (ver javadoc do método).
        service = new ConfigParameterService(configParameterRepository);
        when(configParameterRepository.findByParameterName("BROKEN_PARAM"))
                .thenReturn(Optional.of(ConfigParameterEntity.builder().value("not-a-number").build()));

        assertThat(service.getLongValue("BROKEN_PARAM")).isEmpty();
    }

    @Test
    void getLongValue_parameterNotFound_isEmpty() {
        service = new ConfigParameterService(configParameterRepository);
        when(configParameterRepository.findByParameterName("UNKNOWN_PARAM")).thenReturn(Optional.empty());

        assertThat(service.getLongValue("UNKNOWN_PARAM")).isEmpty();
    }
}
