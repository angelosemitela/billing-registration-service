package com.aalvarenga.billing.util;

/**
 * Mascara valores sensíveis para retorno em API, preservando só os últimos N
 * dígitos "visíveis" e trocando todo o resto por {@code '*'}.
 *
 * <p>Usado hoje por dois campos da consulta de dados
 * ({@code PurchaseQueryService}), cada um com sua própria quantidade de
 * dígitos visíveis (documentado no anexo do usuário e confirmado via
 * pergunta de múltipla escolha - ver doc de decisões do projeto):
 * <ul>
 *   <li>documento (CPF/CNPJ): 2 últimos dígitos visíveis;</li>
 *   <li>número de cartão: 4 últimos dígitos visíveis.</li>
 * </ul>
 *
 * <p>Em ambos os casos, a mesma regra de "caso especial" se aplica: se o
 * valor tiver um tamanho MENOR OU IGUAL à quantidade de dígitos que
 * deveriam ficar visíveis, ele é mascarado por INTEIRO (nunca revela o
 * valor todo só porque ele já é "curto") - por isso os dois campos
 * reaproveitam este único método em vez de duas cópias quase idênticas do
 * mesmo laço de mascaramento.
 */
public final class MaskingUtil {

    private MaskingUtil() {
    }

    /**
     * @param value          valor original (pode ser {@code null})
     * @param visibleSuffixLength quantidade de caracteres finais que devem
     *                            permanecer visíveis
     * @return {@code null} se {@code value} for {@code null}; caso
     * contrário, o valor com todos os caracteres trocados por {@code '*'},
     * exceto os últimos {@code visibleSuffixLength} (ou o valor 100%
     * mascarado, se {@code value.length() <= visibleSuffixLength})
     */
    public static String maskKeepingLast(String value, int visibleSuffixLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= visibleSuffixLength) {
            return "*".repeat(value.length());
        }
        int maskedLength = value.length() - visibleSuffixLength;
        return "*".repeat(maskedLength) + value.substring(maskedLength);
    }
}
