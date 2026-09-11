package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.AddressRequest;
import com.aalvarenga.billing.dto.request.DocumentRequest;
import com.aalvarenga.billing.dto.request.PhoneRequest;
import com.aalvarenga.billing.entity.AccountAddressEntity;
import com.aalvarenga.billing.entity.AccountDocumentEntity;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.AccountPhoneEntity;
import com.aalvarenga.billing.repository.AccountAddressRepository;
import com.aalvarenga.billing.repository.AccountDocumentRepository;
import com.aalvarenga.billing.repository.AccountPhoneRepository;
import com.aalvarenga.billing.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Persiste a estrutura {@code account} da requisição: cria ou atualiza a
 * conta, faz "upsert" dos documentos (por tipo) e insere endereços/telefones.
 *
 * <p>Todas as regras já foram validadas por {@link PurchaseValidationService}
 * antes deste service ser chamado - aqui só existe lógica de PERSISTÊNCIA,
 * nenhuma decisão de "isso é válido ou não" (separação de responsabilidades:
 * validar é uma coisa, gravar é outra).
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountDocumentRepository accountDocumentRepository;
    private final AccountAddressRepository accountAddressRepository;
    private final AccountPhoneRepository accountPhoneRepository;

    /**
     * Cria uma conta nova ou atualiza a existente (cadastral), conforme
     * {@code accountRequest.id()} tenha vindo nulo ou preenchido.
     *
     * @param accountRequest  dados da conta vindos da requisição
     * @param existingAccount conta já carregada pela validação (não nula quando {@code account.id} foi informado)
     */
    public AccountEntity resolveAccount(AccountRequest accountRequest, AccountEntity existingAccount) {
        if (existingAccount == null) {
            AccountEntity newAccount = AccountEntity.builder()
                    .name(accountRequest.name())
                    .externalId(accountRequest.externalId())
                    .status(DomainStatus.ACTIVE)
                    .build();
            return accountRepository.save(newAccount);
        }

        // Atualização cadastral: só sobrescreve os campos que vieram preenchidos.
        if (accountRequest.name() != null) {
            existingAccount.setName(accountRequest.name());
        }
        if (accountRequest.externalId() != null) {
            existingAccount.setExternalId(accountRequest.externalId());
        }
        return accountRepository.save(existingAccount);
    }

    /** Upsert dos documentos por TYPE: se já existir um documento daquele tipo para a conta, atualiza; senão, insere. */
    public void upsertDocuments(List<DocumentRequest> documents, Long accountId) {
        if (documents == null) {
            return;
        }
        for (DocumentRequest document : documents) {
            AccountDocumentEntity entity = accountDocumentRepository
                    .findByAccountIdAndType(accountId, document.type().name())
                    .orElseGet(() -> AccountDocumentEntity.builder()
                            .accountId(accountId)
                            .type(document.type().name())
                            .status(DomainStatus.ACTIVE)
                            .build());
            entity.setDescription(document.description());
            entity.setValue(document.value());
            entity.setCountry(document.country());
            accountDocumentRepository.save(entity);
        }
    }

    /** Endereços são sempre inseridos como novo registro (histórico completo), nunca atualizados. */
    public void insertAddresses(List<AddressRequest> addresses, Long accountId) {
        if (addresses == null) {
            return;
        }
        for (AddressRequest address : addresses) {
            accountAddressRepository.save(AccountAddressEntity.builder()
                    .accountId(accountId)
                    .type(address.type().name())
                    .description(address.description())
                    .addressName(address.addressName())
                    .number(address.number())
                    .complement(address.complement())
                    .zipCode(address.zipCode())
                    .country(address.country())
                    .status(DomainStatus.ACTIVE)
                    .build());
        }
    }

    /** Telefones são sempre inseridos como novo registro, nunca atualizados. */
    public void insertPhones(List<PhoneRequest> phones, Long accountId) {
        if (phones == null) {
            return;
        }
        for (PhoneRequest phone : phones) {
            accountPhoneRepository.save(AccountPhoneEntity.builder()
                    .accountId(accountId)
                    .number(phone.number())
                    .status(DomainStatus.ACTIVE)
                    .build());
        }
    }
}
