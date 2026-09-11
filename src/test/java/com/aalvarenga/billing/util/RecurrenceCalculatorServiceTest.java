package com.aalvarenga.billing.util;

import com.aalvarenga.billing.enums.RecurrenceFrequency;
import com.aalvarenga.billing.service.RecurrenceCalculatorService;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa os três exemplos numéricos citados explicitamente no enunciado do
 * projeto para a regra de próxima recorrência - a parte mais delicada de
 * todo o serviço (data de calendário + fuso horário + estouro de mês).
 */
class RecurrenceCalculatorServiceTest {

    private final RecurrenceCalculatorService calculator = new RecurrenceCalculatorService();

    private long epochOf(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, EpochDateUtil.BUSINESS_ZONE)
                .toInstant().toEpochMilli();
    }

    @Test
    void monthlyRecurrence_sameDayNextMonth() {
        // "Compra as 10/09/2026 as 10:38 UTC-03:00 a data de recorrência será em 10/10/2026 as 00:00 UTC-03:00"
        long transactionDate = epochOf(2026, 9, 10, 10, 38);
        long expected = epochOf(2026, 10, 10, 0, 0);

        long result = calculator.advanceCycles(transactionDate, 1, RecurrenceFrequency.MONTH);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void monthlyRecurrence_dayDoesNotExistInTargetMonth_rollsToFirstDayOfFollowingMonth() {
        // "Compra as 30/01/2027 as 10:38 UTC-03:00 a data de recorrência será as 01/03/2027 as 00:00 UTC-03:00"
        long transactionDate = epochOf(2027, 1, 30, 10, 38);
        long expected = epochOf(2027, 3, 1, 0, 0);

        long result = calculator.advanceCycles(transactionDate, 1, RecurrenceFrequency.MONTH);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void annualRecurrence_sameDayNextYear() {
        // "Compra as 10/09/2026 as 10:38 UTC-03:00 a data de recorrência será em 10/09/2027 as 00:00 UTC-03:00"
        long transactionDate = epochOf(2026, 9, 10, 10, 38);
        long expected = epochOf(2027, 9, 10, 0, 0);

        long result = calculator.advanceCycles(transactionDate, 1, RecurrenceFrequency.ANNUAL);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void trialEndDate_alwaysRollsToMidnightOfTheFollowingDay() {
        // "Compra as 10/09/2026 as 10:38 UTC-03:00 e trialDays=7 a data de recorrência será em 18/09/2026 as 00:00 UTC-03:00"
        long transactionDate = epochOf(2026, 9, 10, 10, 38);
        long expected = epochOf(2026, 9, 18, 0, 0);

        long result = calculator.calculateTrialEndDate(transactionDate, 7);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void futureBillDate_isOneMoreMonthAfterNextBillDate() {
        // "Se a próxima recorrência for 10/10/2026 as 00:00 UTC-03:00 e RECURRENCE_FREQUENCY MONTH,
        //  o future bill deverá ser 10/11/2026 as 00:00 UTC-03:00"
        long nextBillDate = epochOf(2026, 10, 10, 0, 0);
        long expected = epochOf(2026, 11, 10, 0, 0);

        long result = calculator.advanceCycles(nextBillDate, 1, RecurrenceFrequency.MONTH);

        assertThat(result).isEqualTo(expected);
    }
}
