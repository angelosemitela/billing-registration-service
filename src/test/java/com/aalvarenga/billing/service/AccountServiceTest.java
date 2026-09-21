package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.AddressRequest;
import com.aalvarenga.billing.dto.request.DocumentRequest;
import com.aalvarenga.billing.dto.request.PhoneRequest;
import com.aalvarenga.billing.entity.AccountDocumentEntity;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.enums.AddressType;
import com.aalvarenga.billing.enums.DocumentType;
import com.aalvarenga.billing.repository.AccountAddressRepository;
import com.aalvarenga.billing.repository.AccountDocumentRepository;
import com.aalvarenga.billing.repository.AccountPhoneRepository;
import com.aalvarenga.billing.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link AccountService}: persistência da estrutura {@code account} da
 * requisição, já validada por {@code PurchaseValidationService} (ver javadoc
 * da classe - aqui não existe nenhuma decisão de negócio, só gravação).
 *
 * <p>Todos os repositórios usados são interfaces do Spring Data
 * ({@code AccountRepository} e afins) - nenhum deles toca um banco real, por
 * isso a suíte inteira roda com Mockito puro, sem precisar de Testcontainers
 * (ver README, seção "Testcontainers", para o que de fato exige um banco
 * real neste projeto).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountDocumentRepository accountDocumentRepository;
    @Mock
    private AccountAddressRepository accountAddressRepository;
    @Mock
    private AccountPhoneRepository accountPhoneRepository;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, accountDocumentRepository, accountAddressRepository, accountPhoneRepository);
    }

    private AccountRequest accountRequest(String id, String name, String externalId) {
        return new AccountRequest(id, name, externalId, null, null, null, "new@mail.com", true);
    }

    // Stub do "eco" de accountRepository.save(...) movido para fora do
    // @BeforeEach (que roda para TODOS os testes da classe) e chamado só nos
    // testes de resolveAccount(...) que de fato invocam esse repositório -
    // o Mockito, em modo estrito (padrão do MockitoExtension), falha com
    // UnnecessaryStubbingException quando um stub declarado no setUp não é
    // usado por um teste (ex: os testes de upsertDocuments/insertAddresses/
    // insertPhones nunca chamam accountRepository.save).
    private void stubAccountRepositorySave() {
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void resolveAccount_existingAccountIsNull_createsNewAccountAsActive() {
        stubAccountRepositorySave();

        AccountEntity result = service.resolveAccount(accountRequest(null, "João", "EXT-1"), null);

        assertThat(result.getName()).isEqualTo("João");
        assertThat(result.getExternalId()).isEqualTo("EXT-1");
        assertThat(result.getEmail()).isEqualTo("new@mail.com");
        assertThat(result.getAuthorizedFallback()).isTrue();
        assertThat(result.getStatus()).isEqualTo(DomainStatus.ACTIVE);
    }

    @Test
    void resolveAccount_existingAccountWithNameAndExternalIdInformed_overwritesBoth() {
        stubAccountRepositorySave();
        AccountEntity existing = AccountEntity.builder().name("Old Name").externalId("OLD-EXT").email("old@mail.com").authorizedFallback(false).build();

        AccountEntity result = service.resolveAccount(accountRequest("1", "Novo Nome", "NEW-EXT"), existing);

        assertThat(result.getName()).isEqualTo("Novo Nome");
        assertThat(result.getExternalId()).isEqualTo("NEW-EXT");
    }

    @Test
    void resolveAccount_existingAccountWithNameAndExternalIdNull_keepsPreviousValues() {
        // Atualização parcial: name/externalId só são sobrescritos quando vêm
        // preenchidos na requisição (ver javadoc de AccountService.resolveAccount).
        stubAccountRepositorySave();
        AccountEntity existing = AccountEntity.builder().name("Old Name").externalId("OLD-EXT").email("old@mail.com").authorizedFallback(false).build();

        AccountEntity result = service.resolveAccount(accountRequest("1", null, null), existing);

        assertThat(result.getName()).isEqualTo("Old Name");
        assertThat(result.getExternalId()).isEqualTo("OLD-EXT");
    }

    @Test
    void resolveAccount_existingAccount_alwaysOverwritesEmailAndAuthorizedFallback() {
        // email/isAuthorizedFallback são obrigatórios em TODA requisição - por
        // isso, diferente de name/externalId, nunca precisam de checagem de
        // null e sempre sobrescrevem o valor anterior.
        stubAccountRepositorySave();
        AccountEntity existing = AccountEntity.builder().name("Old Name").externalId("OLD-EXT").email("old@mail.com").authorizedFallback(false).build();

        AccountEntity result = service.resolveAccount(accountRequest("1", null, null), existing);

        assertThat(result.getEmail()).isEqualTo("new@mail.com");
        assertThat(result.getAuthorizedFallback()).isTrue();
    }

    @Test
    void upsertDocuments_noExistingDocumentOfThatType_insertsNewOne() {
        when(accountDocumentRepository.findByAccountIdAndType(1L, "CPF")).thenReturn(Optional.empty());
        when(accountDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertDocuments(List.of(new DocumentRequest(DocumentType.CPF, "doc", "12345678900", "BR")), 1L);

        AccountDocumentEntity saved = captureSavedDocument();
        assertThat(saved.getAccountId()).isEqualTo(1L);
        assertThat(saved.getType()).isEqualTo("CPF");
        assertThat(saved.getValue()).isEqualTo("12345678900");
        assertThat(saved.getCountry()).isEqualTo("BR");
        assertThat(saved.getStatus()).isEqualTo(DomainStatus.ACTIVE);
    }

    @Test
    void upsertDocuments_existingDocumentOfThatType_updatesInPlaceInsteadOfInserting() {
        AccountDocumentEntity existing = AccountDocumentEntity.builder().accountId(1L).type("CPF").value("OLD-VALUE").status(DomainStatus.ACTIVE).build();
        when(accountDocumentRepository.findByAccountIdAndType(1L, "CPF")).thenReturn(Optional.of(existing));
        when(accountDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.upsertDocuments(List.of(new DocumentRequest(DocumentType.CPF, "doc atualizado", "NEW-VALUE", "BR")), 1L);

        AccountDocumentEntity saved = captureSavedDocument();
        // Mesma instância (existing) reaproveitada com os campos atualizados -
        // é isso que caracteriza um UPDATE em vez de um INSERT novo.
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getValue()).isEqualTo("NEW-VALUE");
        assertThat(saved.getDescription()).isEqualTo("doc atualizado");
    }

    @Test
    void upsertDocuments_nullList_doesNothing() {
        service.upsertDocuments(null, 1L);

        verify(accountDocumentRepository, never()).save(any());
    }

    @Test
    void insertAddresses_alwaysInsertsANewRecordPerAddress() {
        when(accountAddressRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AddressRequest address = new AddressRequest(AddressType.RESIDENCIAL, "Casa", "Rua A", "100", null, "00000-000", "BR");

        service.insertAddresses(List.of(address), 1L);

        verify(accountAddressRepository).save(any());
    }

    @Test
    void insertAddresses_nullList_doesNothing() {
        service.insertAddresses(null, 1L);

        verify(accountAddressRepository, never()).save(any());
    }

    @Test
    void insertPhones_alwaysInsertsANewRecordPerPhone() {
        when(accountPhoneRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.insertPhones(List.of(new PhoneRequest("11999999999")), 1L);

        verify(accountPhoneRepository).save(any());
    }

    @Test
    void insertPhones_nullList_doesNothing() {
        service.insertPhones(null, 1L);

        verify(accountPhoneRepository, never()).save(any());
    }

    @Test
    void insertPhones_emptyList_doesNothing() {
        service.insertPhones(Collections.emptyList(), 1L);

        verify(accountPhoneRepository, never()).save(any());
    }

    private AccountDocumentEntity captureSavedDocument() {
        ArgumentCaptor<AccountDocumentEntity> captor = ArgumentCaptor.forClass(AccountDocumentEntity.class);
        verify(accountDocumentRepository).save(captor.capture());
        return captor.getValue();
    }
}
