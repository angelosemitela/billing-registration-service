package com.aalvarenga.billing.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utilitário central para conversão entre "epoch millis" (formato em que TODAS
 * as datas são armazenadas no banco, conforme instrução 1 do enunciado) e os
 * tipos ricos de data/hora do {@code java.time} usados dentro da aplicação.
 *
 * <p>Centralizar essa conversão em uma única classe (em vez de espalhar
 * {@code Instant.ofEpochMilli(...)} pelo código) é o que garante
 * "reaproveitamento de código" e evita inconsistência de fuso horário: todo
 * cálculo de negócio (ex: próxima data de recorrência) usa o mesmo fuso de
 * referência, definido aqui uma única vez em {@link #BUSINESS_ZONE}.
 */
public final class EpochDateUtil {

    /**
     * Fuso horário de referência para todos os cálculos de negócio (próxima
     * recorrência, fim de vigência, fim de desconto, etc).
     *
     * <p>O enunciado exemplifica todas as regras de cálculo de data usando
     * "UTC-03:00", que corresponde ao horário de Brasília sem horário de
     * verão (não observado no Brasil desde 2019). Por isso usamos a zona
     * "America/Sao_Paulo" do IANA, que hoje é equivalente a UTC-03:00 fixo.
     */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private EpochDateUtil() {
        // classe utilitária: apenas métodos estáticos, não deve ser instanciada
    }

    /** Instante atual, em epoch millis (o mesmo formato usado nas colunas *_DT do banco). */
    public static long nowMillis() {
        return Instant.now().toEpochMilli();
    }

    /** Converte epoch millis para {@link ZonedDateTime} no fuso de negócio. */
    public static ZonedDateTime toBusinessDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(BUSINESS_ZONE);
    }

    /** Converte um {@link ZonedDateTime} de volta para epoch millis. */
    public static long toEpochMillis(ZonedDateTime dateTime) {
        return dateTime.toInstant().toEpochMilli();
    }
}
