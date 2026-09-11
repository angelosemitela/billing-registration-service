package com.aalvarenga.billing.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

/**
 * Validação da data de expiração de cartão (formato {@code MM/YY}), conforme
 * regra do enunciado: "considerando os dígitos finais do YY compreendendo
 * entre os anos 2000 e 2099. Validaremos a regra para nunca registrarmos
 * cartões com expiração fora do padrão. Exemplos de erro: 13/29 (não existe
 * mês 13), 12/24 (cartão já expirado)".
 */
public final class CardExpirationUtil {

    private CardExpirationUtil() {
    }

    /**
     * Faz o parse de uma expiração no formato {@code MM/YY}.
     *
     * @return o {@link YearMonth} correspondente (ano completo, ex: 2029 para "29"),
     * ou {@link Optional#empty()} se o formato for inválido (não numérico, mês fora de 1-12, etc).
     */
    public static Optional<YearMonth> parse(String expiration) {
        if (expiration == null || !expiration.matches("\\d{2}/\\d{2}")) {
            return Optional.empty();
        }
        String[] parts = expiration.split("/");
        int month = Integer.parseInt(parts[0]);
        int twoDigitYear = Integer.parseInt(parts[1]);
        if (month < 1 || month > 12) {
            return Optional.empty();
        }
        int fullYear = 2000 + twoDigitYear; // instrução do enunciado: sempre entre 2000 e 2099
        return Optional.of(YearMonth.of(fullYear, month));
    }

    /** {@code true} se o mês/ano de expiração já ficou no passado em relação a {@code referenceDate}. */
    public static boolean isExpired(YearMonth expiration, LocalDate referenceDate) {
        return expiration.isBefore(YearMonth.from(referenceDate));
    }
}
