package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.request.RefundRequestItem;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.CancellationType;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.LogRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import com.aalvarenga.billing.util.AssetIdParser;
import com.aalvarenga.billing.util.MoneyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Concentra todas as regras de negócio do cancelamento de produto
 * ({@code POST /api/v1/purchases/cancel}) - mesmo racional de
 * {@link PurchaseValidationService}: regras condicionais, cruzadas entre
 * campos, ou que dependem de consulta ao banco não cabem bem em anotações de
 * Bean Validation isoladas.
 *
 * <p><b>Ordem de validação</b> (a mesma ordem em que os métodos privados
 * aparecem abaixo):
 * <ol>
 *   <li>{@code transactionDt} (formato, futuro, piso mínimo configurável -
 *       mesmas regras de {@code PurchaseValidationService.validateTransactionDt},
 *       propositalmente DUPLICADAS aqui em vez de reaproveitadas - ver nota
 *       no javadoc de {@link #validateTransactionDt});</li>
 *   <li>idempotência de {@code protocolId} (mesma tabela {@code T_LOG} usada
 *       pelo fluxo de compra - ver javadoc de {@link CancellationRequest});</li>
 *   <li>presença de {@code type} (um valor fora do enum já é barrado pelo
 *       Jackson antes de chegar aqui);</li>
 *   <li>resolução de {@code productId} no banco;</li>
 *   <li>{@code transactionDt} não pode ser anterior a
 *       {@code T_PRODUCT.CREATED_DT} do produto resolvido (ver
 *       {@link #validateTransactionDtAgainstProductCreation}) - só pode
 *       acontecer depois da resolução do produto, por isso vem DEPOIS do
 *       item anterior, mesmo envolvendo o mesmo campo de entrada do item 1;</li>
 *   <li>resolução/validação dos itens de {@code refund} - aqui só
 *       verificamos que cada fatura informada pertence ao {@code productId}
 *       da requisição e está em condições de ser estornada (paga, dentro do
 *       saldo estornável); QUAIS faturas devem ser estornadas é uma decisão
 *       de negócio de quem chama este serviço, não deste serviço (ver nota
 *       em {@link #validateImmediateEligibility});</li>
 *   <li>cálculo do {@code processedType} (aplica o downgrade
 *       SCHEDULED-&gt;IMMEDIATE quando cabível) e das regras de
 *       elegibilidade específicas de cada tipo.</li>
 * </ol>
 *
 * <p>Toda violação lança {@link BusinessException} com o código HTTP mais
 * adequado da família 4XX - mesmo critério já documentado em
 * {@link PurchaseValidationService}: {@code 400} para campo ausente/inválido
 * ou pré-condição de elegibilidade não atendida, {@code 404} para uma
 * referência que não existe, {@code 409} para o conflito de idempotência.
 * Nenhuma regra deste endpoint usa {@code 412} - diferente da criação de
 * compra, aqui não há nenhuma "fórmula/somatório" a fechar, só comparações
 * de estado e de teto de valor (mesmo critério já usado, por exemplo, em
 * {@code product.discountValue cannot be greater than product.productValue}).
 */
@Service
@RequiredArgsConstructor
public class CancellationValidationService {

    private final ProductRepository productRepository;
    private final BillRepository billRepository;
    private final LogRepository logRepository;
    private final ConfigParameterService configParameterService;

    public ValidatedCancellationContext validate(CancellationRequest request) {
        long transactionDateEpochMillis = validateTransactionDt(request.transactionDt());
        validateProtocolId(request.protocolId());

        if (request.type() == null) {
            throw BusinessException.badRequest("type is required");
        }

        ProductEntity product = resolveProduct(request.productId());
        validateTransactionDtAgainstProductCreation(transactionDateEpochMillis, product);

        List<ResolvedRefundItem> refunds = validateRefund(request.hasRefund(), request.refund(), product);

        CancellationType processedType = resolveProcessedType(request.type(), transactionDateEpochMillis, product);
        validateEligibility(request.type(), processedType, product);

        return new ValidatedCancellationContext(transactionDateEpochMillis, product, request.type(), processedType, refunds);
    }

    // ------------------------------------------------------------------
    // Dados gerais (transactionDt / protocolId)
    // ------------------------------------------------------------------

    /**
     * Regras IDÊNTICAS a {@code PurchaseValidationService.validateTransactionDt}
     * (mesmo formato, mesma checagem de futuro, mesmo piso configurável
     * {@code MINIMAL_TRANSACTION_DATE}) - duplicadas aqui de propósito nesta
     * v1, em vez de extrair um serviço compartilhado, para não alterar a
     * assinatura/dependências de {@code PurchaseValidationService} (e, por
     * consequência, seu conjunto de testes já existente e confirmado) só
     * para atender uma feature nova. Fica registrada em {@code decisoes.md}
     * como um candidato natural de extração numa próxima refatoração (ex:
     * um {@code TransactionDateValidationService} compartilhado pelos dois
     * fluxos), quando houver um terceiro consumidor ou uma janela dedicada
     * a esse tipo de limpeza.
     */
    private long validateTransactionDt(String transactionDt) {
        long epochMillis;
        try {
            epochMillis = Long.parseLong(transactionDt);
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("transactionDt must be a valid epoch milliseconds value");
        }
        if (epochMillis > System.currentTimeMillis()) {
            throw BusinessException.badRequest("transactionDt cannot be in the future");
        }
        configParameterService.getLongValue(ConfigParameterRules.MINIMAL_TRANSACTION_DATE)
                .filter(minimumEpochMillis -> epochMillis < minimumEpochMillis)
                .ifPresent(minimumEpochMillis -> {
                    throw BusinessException.badRequest(
                            "transactionDt cannot be earlier than the minimum accepted date (" + minimumEpochMillis + ")");
                });
        return epochMillis;
    }

    /**
     * Mesma semântica de idempotência já usada para {@code protocol} em
     * {@code PurchaseValidationService.validateProtocol}: só bloqueia
     * (409) se já existir um log de SUCESSO anterior para o mesmo valor.
     * Reaproveita a MESMA tabela {@code T_LOG} (sem discriminador de
     * endpoint) - ver javadoc de {@link CancellationRequest} para a
     * implicação disso (um {@code protocolId} de cancelamento compartilha o
     * namespace de unicidade com o {@code protocol} de compra).
     */
    private void validateProtocolId(String protocolId) {
        boolean alreadySucceeded = logRepository.findByProtocol(protocolId).stream()
                .anyMatch(log -> "SUCCESS".equals(log.getResult()));
        if (alreadySucceeded) {
            throw BusinessException.conflict("protocolId has already been processed successfully");
        }
    }

    // ------------------------------------------------------------------
    // Produto
    // ------------------------------------------------------------------

    private ProductEntity resolveProduct(String productId) {
        Long technicalId = AssetIdParser.parseProduct(productId);
        if (technicalId == null) {
            throw BusinessException.notFound("productId not found");
        }
        return productRepository.findById(technicalId)
                .orElseThrow(() -> BusinessException.notFound("productId not found"));
    }

    /**
     * Regra geral, válida para os 3 tipos de cancelamento: o {@code transactionDt}
     * informado não pode ser anterior a {@code T_PRODUCT.CREATED_DT} - um
     * cancelamento não pode, logicamente, "acontecer" antes do produto sequer
     * ter sido criado. Diferente da checagem de piso em
     * {@link #validateTransactionDt} (um piso GLOBAL, configurável via
     * {@code MINIMAL_TRANSACTION_DATE}, igual em toda requisição), esta é uma
     * checagem POR PRODUTO - só pode acontecer depois que {@link #resolveProduct}
     * já carregou a entidade do banco, por isso vive aqui e não junto da
     * validação genérica de {@code transactionDt}.
     */
    private void validateTransactionDtAgainstProductCreation(long transactionDateEpochMillis, ProductEntity product) {
        if (transactionDateEpochMillis < product.getCreatedDt()) {
            throw BusinessException.badRequest(
                    "transactionDt cannot be earlier than the product's creation date (CREATED_DT)");
        }
    }

    // ------------------------------------------------------------------
    // Estorno (refund)
    // ------------------------------------------------------------------

    /**
     * @param hasRefund     {@code true} liga o processamento de estorno; qualquer outro valor (incluindo {@code null}) o desliga
     * @param refundRequests itens de estorno da entrada; ignorados quando {@code hasRefund} não é {@code true}
     * @param product       produto já resolvido, usado para checar a relação {@code productId}&lt;-&gt;{@code billId} (Regra 2 de estorno)
     * @return lista de estornos já validados; vazia quando {@code hasRefund} não é {@code true}
     */
    private List<ResolvedRefundItem> validateRefund(Boolean hasRefund, List<RefundRequestItem> refundRequests, ProductEntity product) {
        boolean refundEnabled = Boolean.TRUE.equals(hasRefund);
        if (!refundEnabled) {
            // "Em outros casos, estrutura não é obrigatória" - também não é
            // PROCESSADA, mesmo que venha preenchida por engano: hasRefund é
            // quem decide, não a mera presença da lista.
            return List.of();
        }

        List<BillEntity> productBills = billRepository.findByProductIdInOrderByDueDtDesc(List.of(product.getId()));
        if (productBills.isEmpty()) {
            throw BusinessException.badRequest("the subscriber has no bills associated with this product to refund");
        }
        if (refundRequests == null || refundRequests.isEmpty()) {
            throw BusinessException.badRequest("refund is required and must contain at least one item when hasRefund is true");
        }

        List<ResolvedRefundItem> resolved = new ArrayList<>();
        for (RefundRequestItem item : refundRequests) {
            resolved.add(validateRefundItem(item, product));
        }
        return resolved;
    }

    private ResolvedRefundItem validateRefundItem(RefundRequestItem item, ProductEntity product) {
        Long billTechnicalId = AssetIdParser.parseBilling(item.billId());
        BillEntity bill = billTechnicalId == null ? null : billRepository.findById(billTechnicalId).orElse(null);
        if (bill == null) {
            throw BusinessException.notFound("[refund.billId] Not found: " + item.billId());
        }
        // Regra 2 de estorno: o productId da entrada tem que ser dono desta fatura.
        if (!bill.getProductId().equals(product.getId())) {
            throw BusinessException.badRequest("[refund.billId] does not belong to the informed productId: " + item.billId());
        }
        if (item.amount() == null || MoneyUtil.isNegative(item.amount()) || MoneyUtil.isZero(item.amount())) {
            throw BusinessException.badRequest("[refund.amount] is required and must be greater than zero: " + item.billId());
        }
        // Regra 1 de estorno: só fatura já PAGA (sem saldo em aberto) pode ser estornada.
        if (!MoneyUtil.isZero(bill.getBalanceValue())) {
            throw BusinessException.badRequest("[refund.billId] is not fully paid yet, refund is not allowed: " + item.billId());
        }
        // Regra 3 de estorno: amount não pode passar do saldo ainda estornável.
        BigDecimal maxRefundable = bill.getChargedValue().subtract(bill.getRefundValue());
        if (item.amount().compareTo(maxRefundable) > 0) {
            throw BusinessException.badRequest("[refund.amount] cannot exceed the bill's refundable balance: " + item.billId());
        }
        return new ResolvedRefundItem(bill, item.amount());
    }

    // ------------------------------------------------------------------
    // Elegibilidade por tipo de cancelamento
    // ------------------------------------------------------------------

    /**
     * Aplica a Regra 2 do tipo {@code SCHEDULED}: uma solicitação de
     * agendamento cujo {@code transactionDt} já é anterior ao início do
     * ciclo atual do produto chegou "atrasada" (pensando em requisições
     * assíncronas represadas em outro sistema) e deve ser tratada como
     * {@code IMMEDIATE}. Para os outros dois tipos, o processado é sempre
     * igual ao informado.
     */
    private CancellationType resolveProcessedType(CancellationType inputType, long transactionDateEpochMillis, ProductEntity product) {
        if (inputType == CancellationType.SCHEDULED && transactionDateEpochMillis < product.getCycleStartDt()) {
            return CancellationType.IMMEDIATE;
        }
        return inputType;
    }

    /**
     * Despacha para a validação de elegibilidade certa, com base no tipo
     * EFETIVAMENTE processado - exceto a Regra 1 do tipo {@code SCHEDULED}
     * ({@code isExpiriationService} obrigatório), que é uma pré-condição do
     * tipo INFORMADO (se aplica mesmo quando a requisição acaba sendo
     * rebaixada para IMMEDIATE por conta da Regra 2 - ver javadoc de
     * {@link #resolveProcessedType}).
     */
    private void validateEligibility(CancellationType inputType, CancellationType processedType, ProductEntity product) {
        if (inputType == CancellationType.SCHEDULED && !Boolean.TRUE.equals(product.getExpirationService())) {
            throw BusinessException.badRequest("scheduled cancellation is not eligible for products without an associated service (isExpiriationService=false)");
        }

        switch (processedType) {
            case IMMEDIATE -> validateImmediateEligibility(product);
            case SCHEDULED -> validateScheduledEligibility(product);
            case WITHDRAW_CANCELLATION -> validateWithdrawEligibility(product);
        }
    }

    /**
     * Regras 1 e 2 do tipo {@code IMMEDIATE}.
     *
     * <p><b>Sobre a Regra 3 do anexo</b> ("assinante ONESHOT sem serviço
     * associado deve ter as faturas estornadas"): decidido, em revisão
     * posterior à primeira entrega desta feature (ver {@code decisoes.md}),
     * que este serviço NÃO impõe mais que TODA fatura do produto apareça em
     * {@code refund} com valor cheio. Quais faturas devem ser estornadas -
     * todas, algumas, ou nenhuma - é uma decisão de negócio de quem CHAMA
     * este serviço (o sistema de origem do cancelamento), não deste
     * {@code billing-registration-service}. A única responsabilidade que
     * permanece aqui é garantir a integridade referencial de cada item de
     * {@code refund} informado: pertencer ao {@code productId} da
     * requisição, estar totalmente pago, e não exceder o saldo estornável
     * (ver {@link #validateRefundItem} - Regras 1 a 3 de estorno).
     */
    private void validateImmediateEligibility(ProductEntity product) {
        if (product.getSuspensionStatus() == DomainStatus.PRODUCT_SUSPENSION_DEFAULTER) {
            throw BusinessException.badRequest("immediate cancellation is not allowed for a defaulting subscriber (suspensionStatus=DEFAULTER)");
        }
        boolean statusEligible = product.getStatus() == DomainStatus.PRODUCT_ACTIVE || product.getStatus() == DomainStatus.PRODUCT_SUSPENDED;
        boolean cancellationEligible = product.getCancellationStatus() == DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES
                || product.getCancellationStatus() == DomainStatus.PRODUCT_CANCELLATION_SCHEDULED;
        if (!statusEligible || !cancellationEligible) {
            throw BusinessException.badRequest("immediate cancellation is only allowed for active or suspended products without an incompatible cancellation status");
        }
    }

    /** Regra 3 do tipo {@code SCHEDULED} (a Regra 1 já foi checada em {@link #validateEligibility}, a Regra 2 vira o downgrade). */
    private void validateScheduledEligibility(ProductEntity product) {
        if (product.getStatus() != DomainStatus.PRODUCT_ACTIVE || product.getCancellationStatus() != DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES) {
            throw BusinessException.badRequest("scheduled cancellation is only allowed for active products with no cancellation already scheduled");
        }
    }

    /** Regra 1 do tipo {@code WITHDRAW_CANCELLATION}. */
    private void validateWithdrawEligibility(ProductEntity product) {
        boolean eligible = product.getStatus() == DomainStatus.PRODUCT_ACTIVE
                && product.getCancellationStatus() == DomainStatus.PRODUCT_CANCELLATION_SCHEDULED
                && !Boolean.TRUE.equals(product.getAutoCancelSch());
        if (!eligible) {
            throw BusinessException.badRequest(
                    "withdrawing a cancellation is only allowed for active products with a manually scheduled cancellation");
        }
    }
}
