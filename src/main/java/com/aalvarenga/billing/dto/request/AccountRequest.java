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
 * <p>{@code email} e {@code isAuthorizedFallback} (acrescentados em
 * 17/09/2026 - ver README, seção "Evoluções pedidas") são diferentes: são
 * obrigatórios em TODA requisição, independente de {@code id} ter vindo
 * preenchido ou não.
 *
 * @param id                   id de uma conta já existente; {@code null} = criar conta nova
 * @param name                 nome do titular (obrigatório se id=null)
 * @param externalId           identificador externo único (obrigatório se id=null)
 * @param document             documentos do titular (obrigatório ao menos 1 se id=null)
 * @param address              endereços do titular (opcional)
 * @param phone                telefones do titular (opcional)
 * @param email                e-mail associado à conta (texto livre, sempre obrigatório)
 * @param isAuthorizedFallback se o assinante autoriza cobrança em método
 *                             alternativo quando o principal falha (ex: sem
 *                             sucesso no CREDIT, tenta no DEBIT) - sempre
 *                             obrigatório
 */
public record AccountRequest(
        String id,
        String name,
        String externalId,
        List<DocumentRequest> document,
        List<AddressRequest> address,
        List<PhoneRequest> phone,
        String email,
        Boolean isAuthorizedFallback
) {
}
