package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.DomainAccountStatusEntity;
import com.aalvarenga.billing.entity.DomainBillRefundStatusEntity;
import com.aalvarenga.billing.entity.DomainBillStatusEntity;
import com.aalvarenga.billing.entity.DomainBillTypeEntity;
import com.aalvarenga.billing.entity.DomainDiscountStatusEntity;
import com.aalvarenga.billing.entity.DomainPaymentStatusEntity;
import com.aalvarenga.billing.entity.DomainProductCancellationStatusEntity;
import com.aalvarenga.billing.entity.DomainProductStatusEntity;
import com.aalvarenga.billing.entity.DomainProductSuspensionStatusEntity;
import com.aalvarenga.billing.repository.DomainAccountStatusRepository;
import com.aalvarenga.billing.repository.DomainBillRefundStatusRepository;
import com.aalvarenga.billing.repository.DomainBillStatusRepository;
import com.aalvarenga.billing.repository.DomainBillTypeRepository;
import com.aalvarenga.billing.repository.DomainDiscountStatusRepository;
import com.aalvarenga.billing.repository.DomainPaymentStatusRepository;
import com.aalvarenga.billing.repository.DomainProductCancellationStatusRepository;
import com.aalvarenga.billing.repository.DomainProductStatusRepository;
import com.aalvarenga.billing.repository.DomainProductSuspensionStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link DomainStatusLookupService} - a tradução do {@code STATUS}
 * numérico das tabelas principais para o {@code BACKEND_VALUE} textual das
 * tabelas de domínio.
 *
 * <p><b>Por que esta classe não tinha teste próprio até agora</b>: em todo
 * lugar onde ela é usada ({@code PurchaseQueryServiceTest},
 * {@code CancellationOrchestrationServiceTest}), ela é sempre injetada como
 * um {@code @Mock} e "stubada" (ex:
 * {@code when(domainStatusLookupService.productStatuses(any())).thenReturn(...)})
 * - ou seja, a lógica REAL desta classe (o {@code Collectors.toMap} genérico
 * que faz a tradução id -&gt; backendValue, e o comportamento quando um id
 * pedido não existe na tabela de domínio) nunca chegava a rodar em nenhum
 * teste do projeto. Como cada método é só um repositório mockável (sem
 * nenhuma dependência de banco real/Testcontainers), cobri-la aqui é uma
 * suíte 100% em memória, tão rápida quanto qualquer outro teste de unidade
 * do projeto - identificado numa varredura de cobertura pedida pelo usuário
 * em 22/09/2026 (ver adendo do documento de decisões).
 *
 * <p>Os 9 métodos públicos são deliberadamente repetitivos (mesmo padrão:
 * delegar para {@code repository.findAllById(ids)} e converter para um
 * {@code Map<Integer, String>} via o helper privado {@code toBackendValueMap}) -
 * por isso um teste por método basta para cobrir a "fiação" de cada um
 * (repositório certo, extractors de id/backendValue certos), e dois testes
 * adicionais (usando {@code accountStatuses} como representante) cobrem o
 * comportamento do helper genérico em si (lista vazia, id não encontrado).
 */
@ExtendWith(MockitoExtension.class)
class DomainStatusLookupServiceTest {

    @Mock
    private DomainAccountStatusRepository accountStatusRepository;
    @Mock
    private DomainProductStatusRepository productStatusRepository;
    @Mock
    private DomainDiscountStatusRepository discountStatusRepository;
    @Mock
    private DomainPaymentStatusRepository paymentStatusRepository;
    @Mock
    private DomainBillStatusRepository billStatusRepository;
    @Mock
    private DomainBillTypeRepository billTypeRepository;
    @Mock
    private DomainProductSuspensionStatusRepository productSuspensionStatusRepository;
    @Mock
    private DomainProductCancellationStatusRepository productCancellationStatusRepository;
    @Mock
    private DomainBillRefundStatusRepository billRefundStatusRepository;

    private DomainStatusLookupService service;

    @BeforeEach
    void setUp() {
        service = new DomainStatusLookupService(
                accountStatusRepository, productStatusRepository, discountStatusRepository,
                paymentStatusRepository, billStatusRepository, billTypeRepository,
                productSuspensionStatusRepository, productCancellationStatusRepository,
                billRefundStatusRepository);
    }

    @Test
    void accountStatuses_translatesStatusIdToBackendValue() {
        when(accountStatusRepository.findAllById(Set.of(1, 2))).thenReturn(List.of(
                new DomainAccountStatusEntity(1, "Ativo", "ACTIVE"),
                new DomainAccountStatusEntity(2, "Cancelado", "CANCELLED")));

        Map<Integer, String> result = service.accountStatuses(Set.of(1, 2));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(1, "ACTIVE", 2, "CANCELLED"));
    }

    @Test
    void productStatuses_translatesStatusIdToBackendValue() {
        when(productStatusRepository.findAllById(Set.of(1))).thenReturn(List.of(
                new DomainProductStatusEntity(1, "Ativo", "ACTIVE")));

        Map<Integer, String> result = service.productStatuses(Set.of(1));

        assertThat(result).containsExactly(Map.entry(1, "ACTIVE"));
    }

    @Test
    void discountStatuses_translatesStatusIdToBackendValue() {
        when(discountStatusRepository.findAllById(Set.of(3))).thenReturn(List.of(
                new DomainDiscountStatusEntity(3, "Cancelado", "CANCELLED")));

        Map<Integer, String> result = service.discountStatuses(Set.of(3));

        assertThat(result).containsExactly(Map.entry(3, "CANCELLED"));
    }

    @Test
    void paymentStatuses_translatesIdToBackendValue() {
        // Diferente das tabelas acima (PK STATUS_ID), esta usa PK "ID" -
        // DomainStatusLookupService.paymentStatuses usa o extractor
        // DomainPaymentStatusEntity::getId (não getStatusId) - este teste
        // garante que o extractor certo foi ligado ao repositório certo.
        when(paymentStatusRepository.findAllById(Set.of(1))).thenReturn(List.of(
                new DomainPaymentStatusEntity(1, "Ativo", "ACTIVE")));

        Map<Integer, String> result = service.paymentStatuses(Set.of(1));

        assertThat(result).containsExactly(Map.entry(1, "ACTIVE"));
    }

    @Test
    void billStatuses_translatesIdToBackendValue() {
        when(billStatusRepository.findAllById(Set.of(4))).thenReturn(List.of(
                new DomainBillStatusEntity(4, "Paga aguardando repasse", "TO_BE_TRANSFERRED")));

        Map<Integer, String> result = service.billStatuses(Set.of(4));

        assertThat(result).containsExactly(Map.entry(4, "TO_BE_TRANSFERRED"));
    }

    @Test
    void billTypes_translatesIdToBackendValue() {
        when(billTypeRepository.findAllById(Set.of(1))).thenReturn(List.of(
                new DomainBillTypeEntity(1, "Compra", "BUY")));

        Map<Integer, String> result = service.billTypes(Set.of(1));

        assertThat(result).containsExactly(Map.entry(1, "BUY"));
    }

    @Test
    void productSuspensionStatuses_translatesIdToBackendValue() {
        when(productSuspensionStatusRepository.findAllById(Set.of(1))).thenReturn(List.of(
                new DomainProductSuspensionStatusEntity(1, "Adimplente", "COMPLIENT")));

        Map<Integer, String> result = service.productSuspensionStatuses(Set.of(1));

        assertThat(result).containsExactly(Map.entry(1, "COMPLIENT"));
    }

    @Test
    void productCancellationStatuses_translatesIdToBackendValue() {
        when(productCancellationStatusRepository.findAllById(Set.of(2))).thenReturn(List.of(
                new DomainProductCancellationStatusEntity(2, "Agendado", "SCHEDULED")));

        Map<Integer, String> result = service.productCancellationStatuses(Set.of(2));

        assertThat(result).containsExactly(Map.entry(2, "SCHEDULED"));
    }

    @Test
    void billRefundStatuses_translatesIdToBackendValue() {
        when(billRefundStatusRepository.findAllById(Set.of(2))).thenReturn(List.of(
                new DomainBillRefundStatusEntity(2, "Parcialmente estornada", "PART_REFUNDED")));

        Map<Integer, String> result = service.billRefundStatuses(Set.of(2));

        assertThat(result).containsExactly(Map.entry(2, "PART_REFUNDED"));
    }

    // ------------------------------------------------------------------
    // Comportamento do helper genérico compartilhado (toBackendValueMap) -
    // usando accountStatuses como representante, já que os 9 métodos
    // públicos delegam para o mesmo helper privado.
    // ------------------------------------------------------------------

    @Test
    void whenRequestedIdIsNotFoundInTheDomainTable_itIsSimplyOmittedFromTheResultingMap() {
        // Pediu 2 ids, mas o repositório só devolve 1 (o outro não existe na
        // tabela de domínio, ex: um STATUS gravado por engano/dado legado) -
        // o mapa resultante não deve lançar exceção nem inventar uma entrada,
        // só reflete o que o repositório de fato encontrou.
        when(accountStatusRepository.findAllById(Set.of(1, 999))).thenReturn(List.of(
                new DomainAccountStatusEntity(1, "Ativo", "ACTIVE")));

        Map<Integer, String> result = service.accountStatuses(Set.of(1, 999));

        assertThat(result).containsExactly(Map.entry(1, "ACTIVE"));
        assertThat(result).doesNotContainKey(999);
    }

    @Test
    void whenRequestedIdsCollectionIsEmpty_returnsAnEmptyMap() {
        when(accountStatusRepository.findAllById(Set.<Integer>of())).thenReturn(List.of());

        Map<Integer, String> result = service.accountStatuses(Set.of());

        assertThat(result).isEmpty();
    }
}
