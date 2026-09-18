package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PurchaseQueryRequest;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.AccountRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Valida {@code PurchaseQueryRequest} e resolve, no banco, a conta (e o
 * produto, quando aplicável) referenciados pela consulta - ver
 * {@link ValidatedQueryContext} para o resultado reaproveitado por
 * {@link PurchaseQueryService}.
 *
 * <p>Assim como {@link PurchaseValidationService}, esta classe TAMBÉM faz
 * buscas no banco (não é só validação "estrutural") - é o mesmo padrão já
 * usado ali para {@code account.id}: resolver a entidade referenciada faz
 * parte de "validar que a referência existe", e evita que
 * {@link PurchaseQueryService} precise buscar a mesma entidade de novo.
 */
@Service
@RequiredArgsConstructor
public class PurchaseQueryValidationService {

    /** Prefixo usado em TODOS os IDs de produto devolvidos pela API (ver {@code AssetIdFormatter.product}). */
    private static final String PRODUCT_ID_PREFIX = "PROD_";

    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;

    public ValidatedQueryContext validate(PurchaseQueryRequest request) {
        boolean hasExternalId = hasText(request.externalId());
        boolean hasProductId = hasText(request.productId());
        if (!hasExternalId && !hasProductId) {
            throw BusinessException.badRequest("can't find a externalId or productId");
        }

        boolean returnProductData = defaultTrue(request.returnProductData());
        boolean returnPaymentData = defaultTrue(request.returnPaymentData());
        boolean returnBillData = defaultTrue(request.returnBillData());
        int maxBillReturn = defaultZeroIfInvalid(request.maxBillReturn());

        // Decisão de projeto (não coberta pelo anexo original, que trata os
        // dois campos como alternativas mutuamente exclusivas mas nunca diz
        // o que fazer se AMBOS forem enviados): quando os dois vierem
        // preenchidos, "productId" tem prioridade, por ser o identificador
        // mais RESTRITIVO (escopo de um único produto) - "externalId" é o
        // nível mais geral (toda a conta), então usá-lo aqui devolveria mais
        // dados do que o chamador provavelmente queria. Revisamos essa regra
        // (inicialmente era o oposto - externalId prioritário) depois que o
        // usuário observou, testando manualmente, que o resultado "mais
        // geral" não era o esperado quando os dois IDs eram informados
        // juntos.
        if (hasProductId) {
            Long technicalProductId = parseProductId(request.productId());
            ProductEntity product = productRepository.findById(technicalProductId)
                    .orElseThrow(() -> BusinessException.notFound("[productId] Not found"));
            AccountEntity account = accountRepository.findById(product.getAccountId())
                    .orElseThrow(() -> BusinessException.notFound("[productId] Not found"));
            return new ValidatedQueryContext(account, product, returnProductData, returnPaymentData, returnBillData, maxBillReturn);
        }

        AccountEntity account = accountRepository.findByExternalId(request.externalId())
                .orElseThrow(() -> BusinessException.notFound("[externalId] Not found"));
        return new ValidatedQueryContext(account, null, returnProductData, returnPaymentData, returnBillData, maxBillReturn);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Regra do anexo: ausente, nulo ou qualquer outro valor -&gt; considerar {@code true}. Só {@code false} explícito desativa. */
    private boolean defaultTrue(Boolean value) {
        return !Boolean.FALSE.equals(value);
    }

    /** Regra do anexo: ausente, nulo ou negativo -&gt; considerar {@code 0} (sem limite). */
    private int defaultZeroIfInvalid(Integer value) {
        return (value == null || value < 0) ? 0 : value;
    }

    /**
     * Aceita tanto o formato devolvido pela própria API ({@code "PROD_123"},
     * o mesmo valor que um consumidor já recebeu antes em
     * {@code ProductResponseItem.id}/{@code QueryProductItem.productId}) quanto
     * o ID técnico puro ({@code "123"}), por robustez - decisão registrada
     * no doc de decisões do projeto, já que o anexo original não deixa
     * claro qual dos dois formatos o campo de entrada {@code productId}
     * deveria aceitar.
     */
    private Long parseProductId(String productId) {
        String technicalPart = productId.startsWith(PRODUCT_ID_PREFIX)
                ? productId.substring(PRODUCT_ID_PREFIX.length())
                : productId;
        try {
            return Long.parseLong(technicalPart);
        } catch (NumberFormatException ex) {
            throw BusinessException.notFound("[productId] Not found");
        }
    }
}
