package com.aalvarenga.billing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilitários para comparação de valores monetários.
 *
 * <p>Nunca comparamos {@link BigDecimal} com {@code equals()} (dois valores
 * "iguais" matematicamente, como {@code 10.5} e {@code 10.50}, têm escalas
 * diferentes e {@code equals()} os consideraria diferentes) nem usamos
 * {@code double}/{@code float} para dinheiro (erros de ponto flutuante
 * poderiam fazer um "10.10 + 2.90" não bater exatamente com "13.00"). Toda
 * comparação de valores financeiros do projeto passa por aqui.
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /** Compara dois valores monetários ignorando escala (2 casas decimais, arredondamento bancário). */
    public static boolean equalsMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return normalize(a).compareTo(normalize(b)) == 0;
    }

    /** {@code true} se {@code value} for estritamente negativo. */
    public static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }

    /** {@code true} se {@code value} for exatamente zero. */
    public static boolean isZero(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
