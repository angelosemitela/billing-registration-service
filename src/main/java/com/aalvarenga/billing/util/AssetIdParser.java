package com.aalvarenga.billing.util;

/**
 * Faz o caminho INVERSO de {@link AssetIdFormatter}: recebe um ID de entrada
 * que pode vir tanto no formato "de exibição" da própria API (ex:
 * {@code "PROD_123"}, {@code "BILL_4"} - o mesmo valor que um consumidor já
 * recebeu numa resposta anterior) quanto no ID técnico puro ({@code "123"}),
 * e devolve o {@code Long} técnico correspondente.
 *
 * <p><b>Origem</b>: até 21/09/2026, {@code PurchaseQueryValidationService}
 * tinha seu próprio método privado {@code parseProductId} com exatamente esta
 * lógica (prefixo {@code "PROD_"} + fallback para número puro). A feature de
 * cancelamento de produtos (22/09/2026) precisou da MESMA lógica, agora para
 * dois prefixos diferentes ({@code "PROD_"} e {@code "BILL_"}) - em vez de
 * copiar o método pela terceira vez (a segunda cópia já seria um sinal de
 * "extrai isso"), esta classe centraliza o parsing, do mesmo jeito que
 * {@link AssetIdFormatter} já centraliza a formatação inversa. O método
 * antigo de {@code PurchaseQueryValidationService} foi refatorado para usar
 * esta classe, sem nenhuma mudança de comportamento observável (mesmos casos
 * de teste continuam passando).
 *
 * <p>Diferente de {@link AssetIdFormatter}, que sempre formata com sucesso
 * (ou devolve {@code null} para entrada {@code null}), o parsing pode
 * legitimamente FALHAR (texto que não é um número, com ou sem prefixo) - por
 * isso os métodos aqui devolvem {@code null} nesse caso (nunca lançam
 * exceção), deixando para quem chama decidir o erro de negócio adequado
 * (normalmente {@code BusinessException.notFound}, já que um ID que não
 * parseia é, na prática, uma referência que não existe).
 */
public final class AssetIdParser {

    private static final String PRODUCT_PREFIX = "PROD_";
    private static final String BILL_PREFIX = "BILL_";

    private AssetIdParser() {
    }

    /** Aceita {@code "PROD_123"} ou {@code "123"}; {@code null} se não for um ID válido de nenhuma das duas formas. */
    public static Long parseProduct(String value) {
        return parse(value, PRODUCT_PREFIX);
    }

    /** Aceita {@code "BILL_4"} ou {@code "4"}; {@code null} se não for um ID válido de nenhuma das duas formas. */
    public static Long parseBilling(String value) {
        return parse(value, BILL_PREFIX);
    }

    private static Long parse(String value, String prefix) {
        if (value == null) {
            return null;
        }
        String technicalPart = value.startsWith(prefix) ? value.substring(prefix.length()) : value;
        try {
            return Long.parseLong(technicalPart);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
