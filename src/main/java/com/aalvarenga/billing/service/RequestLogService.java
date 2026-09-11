package com.aalvarenga.billing.service;

import com.aalvarenga.billing.config.BillingProperties;
import com.aalvarenga.billing.entity.LogEntity;
import com.aalvarenga.billing.repository.LogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava, em {@code T_LOG}, TODA requisição recebida pelo serviço - tanto as
 * que terminam em sucesso quanto as que terminam em erro (instrução do
 * enunciado: "Registra as requisições do serviço em tabela").
 *
 * <p>Usamos {@code @Transactional(propagation = Propagation.REQUIRES_NEW)}
 * de propósito: o log precisa ser gravado MESMO QUE a transação principal de
 * persistência da compra tenha sido desfeita (rollback) por causa de um erro
 * de negócio. REQUIRES_NEW abre uma transação totalmente independente para
 * este INSERT, que faz commit sozinha, não importa o que aconteça com a
 * transação "de fora".
 */
@Service
@RequiredArgsConstructor
public class RequestLogService {

    private final LogRepository logRepository;
    private final BillingProperties billingProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String protocol, String result, String code, String reason, String inputJson, String outputJson) {
        int maxLength = billingProperties.log().maxPayloadLength();
        logRepository.save(LogEntity.builder()
                .protocol(protocol)
                .result(result)
                .code(code)
                // REASON também tem limite de tamanho na tabela (500) - trunca por segurança.
                .reason(truncate(reason, 500))
                .input(truncate(inputJson, maxLength))
                .output(truncate(outputJson, maxLength))
                .build());
    }

    /** Trunca ("cut") o conteúdo para caber na coluna do banco, conforme instrução explícita do enunciado. */
    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
