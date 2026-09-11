package com.aalvarenga.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Mapeia {@code T_LOG}: registra cada requisição recebida pelo serviço,
 * grava o JSON de entrada/saída e o resultado do processamento.
 *
 * <p>É consultada por {@code PROTOCOL} para a checagem de idempotência (ver
 * com.aalvarenga.billing.service.RequestLogService): um protocolo que já
 * teve um processamento de SUCESSO anteriormente é bloqueado; um protocolo
 * cujo único histórico é de ERRO pode ser reprocessado normalmente.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "T_LOG")
public class LogEntity extends BaseAuditableEntity {

    @Column(name = "PROTOCOL", nullable = false, length = 100)
    private String protocol;

    @Column(name = "RESULT", nullable = false, length = 20)
    private String result;

    @Column(name = "CODE", nullable = false, length = 10)
    private String code;

    @Column(name = "REASON", nullable = false, length = 500)
    private String reason;

    @Column(name = "INPUT", nullable = false, length = 16000)
    private String input;

    @Column(name = "OUTPUT", nullable = false, length = 16000)
    private String output;
}
