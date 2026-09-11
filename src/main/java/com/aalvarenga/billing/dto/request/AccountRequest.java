package com.aalvarenga.billing.dto.request;

import java.util.List;

/**
 * Estrutura {@code account} da entrada.
 *
 * <p>Todos os campos aqui são opcionais do ponto de vista do Bean Validation
 * porque a obrigatoriedade é CONDICIONAL: {@code name}/{@code externalId} só
 * são obrigatórios quando {@code id} é nulo (conta nova). Essa regra é
 * verificada em {@code PurchaseValidationService.validateAccount}.
 *
 * @param id         id de uma conta já existente; {@code null} = criar conta nova
 * @param name       nome do titular (obrigatório se id=null)
 * @param externalId identificador externo único (obrigatório se id=null)
 * @param document   documentos do titular (obrigatório ao menos 1 se id=null)
 * @param address    endereços do titular (opcional)
 * @param phone      telefones do titular (opcional)
 */
public record AccountRequest(
        String id,
        String name,
        String externalId,
        List<DocumentRequest> document,
        List<AddressRequest> address,
        List<PhoneRequest> phone
) {
}
