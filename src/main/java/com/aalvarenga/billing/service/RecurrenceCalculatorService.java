package com.aalvarenga.billing.service;

import com.aalvarenga.billing.enums.RecurrenceFrequency;
import com.aalvarenga.billing.util.EpochDateUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;

/**
 * Centraliza TODO o cálculo de datas de recorrência do projeto: próxima
 * cobrança, fim de vigência, próxima-próxima cobrança e fim de vigência de
 * desconto. Todas essas regras, no enunciado, remetem umas às outras ("segue
 * a mesma regra de nextBillDate") - concentrá-las aqui em vez de duplicar a
 * lógica em cada service é o que garante "reaproveitamento de código"
 * (instrução 3) e evita que uma correção de regra precise ser replicada em
 * vários lugares.
 *
 * <h2>Regra de "avanço de ciclo" (usada por MONTH e ANNUAL)</h2>
 * Dado um {@code baseDate} e um número de ciclos, soma-se {@code cycles}
 * meses (MONTH) ou {@code cycles * 12} meses (ANNUAL) à data base, mantendo o
 * MESMO dia do mês, sempre à meia-noite (00:00) no fuso de negócio. Quando o
 * dia não existe no mês de destino (ex: dia 30 em fevereiro), a regra do
 * enunciado manda pular para o dia 1º do mês SEGUINTE ao mês de destino (não
 * para o último dia do mês de destino) - ver exemplo no enunciado: compra em
 * 30/01, +1 mês, cai em 01/03 (não em 28/02).
 *
 * <h2>Regra de trial</h2>
 * Quando a compra é trial, a próxima cobrança é {@code transactionDate + trialDays}
 * (dias corridos), sempre jogada para a meia-noite do PRÓXIMO dia - ver
 * exemplo do enunciado: compra às 10:38 do dia 10/09 + 7 dias = 17/09 10:38,
 * jogado para a meia-noite do dia seguinte = 18/09 00:00.
 */
@Service
public class RecurrenceCalculatorService {

    /**
     * Avança {@code cycles} ciclos de {@code frequency} a partir de
     * {@code baseEpochMillis}, preservando o dia do mês (com estouro para o
     * 1º dia do mês seguinte quando o dia não existir no mês de destino),
     * sempre retornando a meia-noite (00:00) do dia resultante no fuso de
     * negócio.
     *
     * @param baseEpochMillis data base (normalmente {@code transactionDate}
     *                        ou uma {@code nextBillDate} já calculada)
     * @param cycles          número de ciclos a avançar (>= 1)
     * @param frequency       MONTH ou ANNUAL
     * @return o novo instante, em epoch millis, à meia-noite do dia resultante
     */
    public long advanceCycles(long baseEpochMillis, int cycles, RecurrenceFrequency frequency) {
        LocalDate baseDate = EpochDateUtil.toBusinessDateTime(baseEpochMillis).toLocalDate();
        int monthsToAdd = (frequency == RecurrenceFrequency.ANNUAL) ? cycles * 12 : cycles;

        int totalMonths = baseDate.getYear() * 12 + (baseDate.getMonthValue() - 1) + monthsToAdd;
        int targetYear = Math.floorDiv(totalMonths, 12);
        int targetMonth = Math.floorMod(totalMonths, 12) + 1;

        int day = baseDate.getDayOfMonth();
        YearMonth targetYearMonth = YearMonth.of(targetYear, targetMonth);

        LocalDate resultDate;
        if (day <= targetYearMonth.lengthOfMonth()) {
            resultDate = targetYearMonth.atDay(day);
        } else {
            // o dia não existe no mês de destino -> primeiro dia do mês SEGUINTE
            resultDate = targetYearMonth.plusMonths(1).atDay(1);
        }

        ZonedDateTime midnight = resultDate.atStartOfDay(EpochDateUtil.BUSINESS_ZONE);
        return EpochDateUtil.toEpochMillis(midnight);
    }

    /**
     * Calcula a data de próxima cobrança para uma compra em regime de TRIAL:
     * {@code transactionDate + trialDays} (dias corridos), sempre arredondado
     * para a meia-noite do dia SEGUINTE ao resultado dessa soma (ver exemplo
     * do enunciado, citado na documentação da classe).
     *
     * @param transactionEpochMillis data/hora da transação de origem
     * @param trialDays              número de dias de cortesia (>= 1)
     * @return o instante, em epoch millis, da próxima cobrança após o trial
     */
    public long calculateTrialEndDate(long transactionEpochMillis, int trialDays) {
        LocalDate dateAfterTrial = EpochDateUtil.toBusinessDateTime(transactionEpochMillis)
                .toLocalDate()
                .plusDays(trialDays);
        // "sempre jogaremos para o próximo dia à meia-noite": soma-se +1 dia à
        // data resultante e trunca-se a hora para 00:00.
        LocalDate nextMidnightDay = dateAfterTrial.plusDays(1);
        ZonedDateTime midnight = nextMidnightDay.atStartOfDay(EpochDateUtil.BUSINESS_ZONE);
        return EpochDateUtil.toEpochMillis(midnight);
    }
}
