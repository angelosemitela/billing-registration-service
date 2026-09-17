package com.aalvarenga.billing.service;

import com.aalvarenga.billing.repository.ConfigFeatureToggleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Lê o estado (ligado/desligado) das regras configuráveis em
 * {@code T_CONFIG_FEATURE_TOGGLE} (ver {@link FeatureToggleRules} para os
 * nomes de regra conhecidos).
 *
 * <p>Este é o mecanismo mais simples possível de "feature toggle": uma
 * consulta direta ao banco a cada chamada, sem nenhum cache. Para este
 * projeto de estudo/portfólio isso é suficiente (a tabela tem poucas linhas
 * e a consulta é por uma coluna {@code UNIQUE}), mas vale registrar, para
 * quem for evoluir isso, as alternativas mais robustas usadas em produção:
 *
 * <ul>
 *   <li><b>Cache em memória com TTL curto</b> (ex: {@code @Cacheable} do
 *   Spring + Caffeine): evita 1 consulta ao banco por requisição, ao custo de
 *   uma mudança de toggle levar alguns segundos para "propagar";</li>
 *   <li><b>Ferramentas dedicadas de feature flag</b> (Togglz, FF4J, ou um
 *   serviço externo como Unleash/LaunchDarkly/Split): trazem prontos
 *   conceitos como % de rollout gradual, segmentação por usuário, auditoria
 *   de quem mudou o quê e painel administrativo - todos ausentes aqui;</li>
 *   <li><b>Configuração centralizada</b> (Spring Cloud Config, Consul, um
 *   ConfigMap do Kubernetes recarregado via {@code @RefreshScope}): faz mais
 *   sentido quando o toggle é o mesmo para TODAS as instâncias e não precisa
 *   de granularidade por registro, o que não é bem o caso aqui (a regra
 *   consultada é por-requisição, não por-instância da aplicação).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureToggleService {

    private final ConfigFeatureToggleRepository configFeatureToggleRepository;

    /**
     * @param ruleName nome exato da regra (ver {@link FeatureToggleRules})
     * @return {@code true} somente se a regra existir E estiver com
     * {@code STATUS_B = 1}. Uma regra inexistente é tratada como desativada
     * (fail-safe: na dúvida, o comportamento novo/condicional fica desligado),
     * com um aviso no log para facilitar o diagnóstico de um nome digitado
     * errado.
     */
    public boolean isEnabled(String ruleName) {
        return configFeatureToggleRepository.findByRuleName(ruleName)
                .map(toggle -> Boolean.TRUE.equals(toggle.getStatus()))
                .orElseGet(() -> {
                    log.warn("Feature toggle rule not found, treating as disabled: {}", ruleName);
                    return false;
                });
    }
}
