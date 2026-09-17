package com.aalvarenga.billing.util;

/**
 * Formata os IDs técnicos (auto-increment) das entidades para o formato de
 * saída da API: um prefixo curto e legível por tipo de entidade, seguido do
 * ID técnico (ex: {@code "ACCT_1"}, {@code "PROD_2"}, {@code "PAY_3"},
 * {@code "BILL_4"}).
 *
 * <p><b>Importante</b>: o prefixo é PURAMENTE cosmético/de apresentação -
 * existe só na resposta JSON, formatado aqui em
 * {@code PurchaseOrchestrationService}. No banco de dados, a chave primária
 * de cada tabela continua sendo um {@code BIGINT AUTO_INCREMENT} "puro", sem
 * prefixo nenhum - é o jeito mais eficiente e padrão de chave primária em
 * bancos relacionais (índice compacto, comparação numérica rápida, ocupa
 * menos espaço que texto). Se o prefixo fosse gravado dentro do próprio
 * banco, toda coluna que referencia esse ID (FK) teria que virar
 * {@code VARCHAR}, o que é mais lento para indexar e comparar - por isso a
 * formatação acontece só na borda da API, nunca dentro do schema.
 *
 * <p>Esta decisão substitui a anterior (ver README, seção "Decisões
 * confirmadas com o usuário", item 3: "todo ID retornado ao cliente é
 * simplesmente o ID técnico convertido para String") - o ID técnico
 * continua sendo a base do valor, só ganhou um prefixo por tipo de entidade
 * para ficar mais legível para quem consome a API.
 */
public final class AssetIdFormatter {

    private AssetIdFormatter() {
    }

    public static String account(Long technicalId) {
        return format("ACCT", technicalId);
    }

    public static String product(Long technicalId) {
        return format("PROD", technicalId);
    }

    public static String billing(Long technicalId) {
        return format("BILL", technicalId);
    }

    public static String payment(Long technicalId) {
        return format("PAY", technicalId);
    }

    /**
     * Sobrecarga para os poucos casos em que o ID já chega como {@code String}
     * (ex: {@code T_PRODUCT.DEFAULT_PAYMENT_ID}, que guarda o ID técnico do
     * pagamento como texto solto - ver {@code ProductEntity}).
     */
    public static String payment(String technicalId) {
        return format("PAY", technicalId);
    }

    private static String format(String prefix, Long technicalId) {
        return technicalId == null ? null : prefix + "_" + technicalId;
    }

    private static String format(String prefix, String technicalId) {
        return technicalId == null ? null : prefix + "_" + technicalId;
    }
}
