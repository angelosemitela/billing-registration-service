package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.ConfigFeatureToggleEntity;
import com.aalvarenga.billing.repository.ConfigFeatureToggleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Cobre o comportamento de {@link FeatureToggleService}, em especial o
 * caminho *fail-safe* (regra desconhecida = tratada como desligada) - é
 * exatamente esse caminho que evita que um nome de regra digitado errado
 * derrube a aplicação com uma exceção em vez de simplesmente manter o
 * comportamento condicional desligado.
 */
@ExtendWith(MockitoExtension.class)
class FeatureToggleServiceTest {

    @Mock
    private ConfigFeatureToggleRepository configFeatureToggleRepository;

    private FeatureToggleService service;

    @Test
    void returnsTrueWhenRuleExistsAndIsEnabled() {
        service = new FeatureToggleService(configFeatureToggleRepository);
        when(configFeatureToggleRepository.findByRuleName("SOME_RULE"))
                .thenReturn(Optional.of(ConfigFeatureToggleEntity.builder().status(true).build()));

        assertThat(service.isEnabled("SOME_RULE")).isTrue();
    }

    @Test
    void returnsFalseWhenRuleExistsButIsDisabled() {
        service = new FeatureToggleService(configFeatureToggleRepository);
        when(configFeatureToggleRepository.findByRuleName("SOME_RULE"))
                .thenReturn(Optional.of(ConfigFeatureToggleEntity.builder().status(false).build()));

        assertThat(service.isEnabled("SOME_RULE")).isFalse();
    }

    @Test
    void returnsFalseWhenRuleNameIsNotFound_failSafe() {
        // Nenhuma regra cadastrada com este nome (ex: erro de digitação) - o
        // comportamento condicional correspondente deve ficar DESLIGADO, nunca
        // lançar exceção.
        service = new FeatureToggleService(configFeatureToggleRepository);
        when(configFeatureToggleRepository.findByRuleName("UNKNOWN_RULE"))
                .thenReturn(Optional.empty());

        assertThat(service.isEnabled("UNKNOWN_RULE")).isFalse();
    }
}
