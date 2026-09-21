package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.ConfigParameterEntity;
import com.aalvarenga.billing.repository.ConfigParameterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Lê os valores configuráveis em {@code T_CONFIG_PARAMETERS} (ver
 * {@link ConfigParameterRules} para os nomes de parâmetro conhecidos).
 *
 * <p>Mesmo racional/mesmas ressalvas de {@link FeatureToggleService}
 * (consulta direta ao banco a cada chamada, sem cache - suficiente para este
 * projeto de estudo; ver aquela classe para as alternativas de mercado mais
 * robustas, como cache com TTL ou uma ferramenta dedicada de configuração
 * centralizada). A diferença é o formato do dado: enquanto
 * {@code T_CONFIG_FEATURE_TOGGLE} guarda um booleano, esta tabela guarda um
 * VALOR de texto livre - por isso {@link #getValue} devolve a string crua, e
 * {@link #getLongValue} é um atalho para o caso (hoje o único existente) de
 * um parâmetro numérico/data em epoch millis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigParameterService {

    private final ConfigParameterRepository configParameterRepository;

    /**
     * @param parameterName nome exato do parâmetro (ver {@link ConfigParameterRules})
     * @return o {@code VALUE} cru, ou {@link Optional#empty()} se o parâmetro
     * não existir - tratado como "não configurado" (fail-safe: quem chama
     * decide o comportamento padrão nesse caso), com um aviso no log para
     * facilitar o diagnóstico de um nome digitado errado.
     */
    public Optional<String> getValue(String parameterName) {
        Optional<String> value = configParameterRepository.findByParameterName(parameterName)
                .map(ConfigParameterEntity::getValue);
        if (value.isEmpty()) {
            log.warn("Config parameter not found, treating as not configured: {}", parameterName);
        }
        return value;
    }

    /**
     * Atalho para um parâmetro cujo {@code VALUE} representa um número
     * inteiro (ex: uma data em epoch milissegundos). Um valor presente mas
     * não numérico (erro de configuração no banco, não da aplicação) também
     * é tratado como "não configurado", com um aviso no log - preferimos
     * degradar a regra a lançar um erro 500 por causa de um dado de
     * configuração mal formado.
     */
    public Optional<Long> getLongValue(String parameterName) {
        return getValue(parameterName).flatMap(raw -> {
            try {
                return Optional.of(Long.parseLong(raw));
            } catch (NumberFormatException ex) {
                log.warn("Config parameter {} is not a valid long value: {}", parameterName, raw);
                return Optional.empty();
            }
        });
    }
}
