package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PurchaseQueryRequest;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.AccountRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link PurchaseQueryValidationService}: exigência de
 * {@code externalId} OU {@code productId}, resolução da conta em cada
 * caminho, e os valores padrão das flags/limite (ver javadoc da própria
 * classe e de {@link PurchaseQueryRequest} para o detalhe de cada regra).
 */
@ExtendWith(MockitoExtension.class)
class PurchaseQueryValidationServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ProductRepository productRepository;

    private PurchaseQueryValidationService service;

    // Construído em @BeforeEach, e não como inicializador de campo: os
    // campos @Mock só são preenchidos pelo MockitoExtension DEPOIS que o
    // objeto de teste é construído - um inicializador de campo aqui
    // capturaria accountRepository/productRepository ainda como null,
    // causando NullPointerException em todo teste que chega a chamar um
    // método do repositório (foi exatamente o bug encontrado ao rodar
    // `mvn test`: 10 de 13 testes falhavam por esse motivo).
    @BeforeEach
    void setUp() {
        service = new PurchaseQueryValidationService(accountRepository, productRepository);
    }

    private AccountEntity account(Long id) {
        return AccountEntity.builder().id(id).externalId("EXT-" + id).build();
    }

    @Test
    void rejectsWhenNeitherExternalIdNorProductIdIsInformed() {
        PurchaseQueryRequest request = new PurchaseQueryRequest(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("can't find a externalId or productId");
        verifyNoInteractions(accountRepository, productRepository);
    }

    @Test
    void rejectsBlankIdsTheSameAsAbsent() {
        PurchaseQueryRequest request = new PurchaseQueryRequest("   ", "", null, null, null, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("can't find a externalId or productId");
    }

    @Test
    void resolvesAccountByExternalId() {
        AccountEntity account = account(1L);
        when(accountRepository.findByExternalId("EXT-1")).thenReturn(Optional.of(account));
        PurchaseQueryRequest request = new PurchaseQueryRequest("EXT-1", null, null, null, null, null);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.account()).isEqualTo(account);
        assertThat(context.productFilter()).isNull();
    }

    @Test
    void notFoundWhenExternalIdDoesNotExist() {
        when(accountRepository.findByExternalId("GHOST")).thenReturn(Optional.empty());
        PurchaseQueryRequest request = new PurchaseQueryRequest("GHOST", null, null, null, null, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("[externalId] Not found");
    }

    @Test
    void resolvesAccountThroughProductId_acceptingThePrefixedFormat() {
        AccountEntity account = account(9L);
        ProductEntity product = ProductEntity.builder().id(2L).accountId(9L).build();
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(accountRepository.findById(9L)).thenReturn(Optional.of(account));
        PurchaseQueryRequest request = new PurchaseQueryRequest(null, "PROD_2", null, null, null, null);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.account()).isEqualTo(account);
        assertThat(context.productFilter()).isEqualTo(product);
    }

    @Test
    void resolvesAccountThroughProductId_acceptingTheRawTechnicalId() {
        AccountEntity account = account(9L);
        ProductEntity product = ProductEntity.builder().id(2L).accountId(9L).build();
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(accountRepository.findById(9L)).thenReturn(Optional.of(account));
        PurchaseQueryRequest request = new PurchaseQueryRequest(null, "2", null, null, null, null);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.productFilter()).isEqualTo(product);
    }

    @Test
    void notFoundWhenProductIdIsNotNumeric() {
        PurchaseQueryRequest request = new PurchaseQueryRequest(null, "PROD_abc", null, null, null, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("[productId] Not found");
        verifyNoInteractions(productRepository);
    }

    @Test
    void notFoundWhenProductIdDoesNotExist() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        PurchaseQueryRequest request = new PurchaseQueryRequest(null, "PROD_999", null, null, null, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("[productId] Not found");
    }

    @Test
    void productIdTakesPriorityWhenBothAreInformed() {
        // productId é o identificador mais RESTRITIVO (escopo de um único
        // produto) - regra ajustada depois que o usuário observou, testando
        // manualmente, que usar externalId (o mais geral) devolvia mais
        // dados do que o esperado quando os dois IDs eram informados juntos.
        AccountEntity account = account(9L);
        ProductEntity product = ProductEntity.builder().id(2L).accountId(9L).build();
        when(productRepository.findById(2L)).thenReturn(Optional.of(product));
        when(accountRepository.findById(9L)).thenReturn(Optional.of(account));
        PurchaseQueryRequest request = new PurchaseQueryRequest("EXT-1", "PROD_2", null, null, null, null);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.account()).isEqualTo(account);
        assertThat(context.productFilter()).isEqualTo(product);
        // externalId foi ignorado - a conta veio de accountRepository.findById
        // (via o produto), nunca de findByExternalId.
        verify(accountRepository, never()).findByExternalId(any());
    }

    @Test
    void defaultsReturnFlagsToTrue_whenAbsentOrAnyNonFalseValue() {
        when(accountRepository.findByExternalId("EXT-1")).thenReturn(Optional.of(account(1L)));
        PurchaseQueryRequest request = new PurchaseQueryRequest("EXT-1", null, null, Boolean.TRUE, null, null);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.returnProductData()).isTrue();
        assertThat(context.returnPaymentData()).isTrue();
        assertThat(context.returnBillData()).isTrue();
    }

    @Test
    void onlyExplicitFalseDisablesAReturnFlag() {
        when(accountRepository.findByExternalId("EXT-1")).thenReturn(Optional.of(account(1L)));
        PurchaseQueryRequest request = new PurchaseQueryRequest("EXT-1", null, Boolean.FALSE, null, Boolean.FALSE, null);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.returnProductData()).isFalse();
        assertThat(context.returnPaymentData()).isTrue();
        assertThat(context.returnBillData()).isFalse();
    }

    @Test
    void defaultsMaxBillReturnToZero_whenAbsentOrNegative() {
        when(accountRepository.findByExternalId("EXT-1")).thenReturn(Optional.of(account(1L)));

        ValidatedQueryContext absent = service.validate(new PurchaseQueryRequest("EXT-1", null, null, null, null, null));
        ValidatedQueryContext negative = service.validate(new PurchaseQueryRequest("EXT-1", null, null, null, null, -5));

        assertThat(absent.maxBillReturn()).isZero();
        assertThat(negative.maxBillReturn()).isZero();
    }

    @Test
    void keepsMaxBillReturnWhenPositive() {
        when(accountRepository.findByExternalId("EXT-1")).thenReturn(Optional.of(account(1L)));
        PurchaseQueryRequest request = new PurchaseQueryRequest("EXT-1", null, null, null, null, 3);

        ValidatedQueryContext context = service.validate(request);

        assertThat(context.maxBillReturn()).isEqualTo(3);
    }
}
