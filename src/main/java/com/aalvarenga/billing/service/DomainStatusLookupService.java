package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.DomainAccountStatusEntity;
import com.aalvarenga.billing.entity.DomainBillStatusEntity;
import com.aalvarenga.billing.entity.DomainBillTypeEntity;
import com.aalvarenga.billing.entity.DomainDiscountStatusEntity;
import com.aalvarenga.billing.entity.DomainPaymentStatusEntity;
import com.aalvarenga.billing.entity.DomainProductStatusEntity;
import com.aalvarenga.billing.repository.DomainAccountStatusRepository;
import com.aalvarenga.billing.repository.DomainBillStatusRepository;
import com.aalvarenga.billing.repository.DomainBillTypeRepository;
import com.aalvarenga.billing.repository.DomainDiscountStatusRepository;
import com.aalvarenga.billing.repository.DomainPaymentStatusRepository;
import com.aalvarenga.billing.repository.DomainProductStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Traduz o {@code STATUS}/{@code BILL_TYPE} numérico (a FK lógica gravada em
 * cada tabela principal) para o {@code BACKEND_VALUE} textual das tabelas de
 * domínio (ex: {@code 1} -&gt; {@code "ACTIVE"}) - usado exclusivamente pela
 * consulta de dados ({@code PurchaseQueryService}), já que a criação de
 * compra (POST /api/v1/purchases) nunca devolve esses valores na resposta.
 *
 * <p>Cada método recebe um LOTE de IDs (não um id por vez) e faz UMA única
 * consulta via {@code findAllById} - evita o clássico problema de N+1
 * consultas quando a resposta tem, por exemplo, 5 produtos e cada um
 * precisaria de uma consulta separada só para descobrir seu
 * {@code productSt}. Quem chama é responsável por já ter coletado o
 * conjunto de IDs distintos necessários (normalmente via
 * {@code stream().map(...).collect(Collectors.toSet())} antes de montar a
 * resposta).
 *
 * <p><b>Possibilidade para estudo futuro</b>: como estas tabelas de domínio
 * são pequenas e praticamente estáticas (só mudam com uma nova migration),
 * uma evolução natural seria cachear o resultado inteiro de cada tabela em
 * memória (ex: {@code @Cacheable} do Spring Cache, com um provedor como
 * Caffeine ou Redis) em vez de consultar o banco a cada requisição de
 * consulta - trocaria uma leitura rápida (tabela com poucas linhas, sempre
 * no buffer pool do MySQL) por uma leitura ainda mais rápida (memória do
 * processo), ao custo de ter que invalidar o cache se alguém rodar uma nova
 * migration que altere esses valores.
 */
@Service
@RequiredArgsConstructor
public class DomainStatusLookupService {

    private final DomainAccountStatusRepository accountStatusRepository;
    private final DomainProductStatusRepository productStatusRepository;
    private final DomainDiscountStatusRepository discountStatusRepository;
    private final DomainPaymentStatusRepository paymentStatusRepository;
    private final DomainBillStatusRepository billStatusRepository;
    private final DomainBillTypeRepository billTypeRepository;

    public Map<Integer, String> accountStatuses(Collection<Integer> ids) {
        return toBackendValueMap(accountStatusRepository.findAllById(ids), DomainAccountStatusEntity::getStatusId, DomainAccountStatusEntity::getBackendValue);
    }

    public Map<Integer, String> productStatuses(Collection<Integer> ids) {
        return toBackendValueMap(productStatusRepository.findAllById(ids), DomainProductStatusEntity::getStatusId, DomainProductStatusEntity::getBackendValue);
    }

    public Map<Integer, String> discountStatuses(Collection<Integer> ids) {
        return toBackendValueMap(discountStatusRepository.findAllById(ids), DomainDiscountStatusEntity::getStatusId, DomainDiscountStatusEntity::getBackendValue);
    }

    public Map<Integer, String> paymentStatuses(Collection<Integer> ids) {
        return toBackendValueMap(paymentStatusRepository.findAllById(ids), DomainPaymentStatusEntity::getId, DomainPaymentStatusEntity::getBackendValue);
    }

    public Map<Integer, String> billStatuses(Collection<Integer> ids) {
        return toBackendValueMap(billStatusRepository.findAllById(ids), DomainBillStatusEntity::getId, DomainBillStatusEntity::getBackendValue);
    }

    public Map<Integer, String> billTypes(Collection<Integer> ids) {
        return toBackendValueMap(billTypeRepository.findAllById(ids), DomainBillTypeEntity::getId, DomainBillTypeEntity::getBackendValue);
    }

    private <T> Map<Integer, String> toBackendValueMap(List<T> entities, Function<T, Integer> idExtractor, Function<T, String> backendValueExtractor) {
        return entities.stream().collect(Collectors.toMap(idExtractor, backendValueExtractor));
    }
}
