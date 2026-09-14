package com.aalvarenga.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
@SuperBuilder
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

    // @Lob sozinho não basta: sem "length", o Hibernate assume o padrão do
    // JPA (length = 255) para decidir QUAL variante de texto do MySQL
    // validar - e, para um CLOB com length <= 255, ele espera TINYTEXT (foi
    // exatamente o erro visto: "found [text], but expecting [tinytext]").
    // O "length" aqui não controla mais VARCHAR x TEXT (quem faz isso é o
    // @Lob) - controla apenas qual das quatro variantes de texto do MySQL
    // (TINYTEXT até 255, TEXT até 65.535, MEDIUMTEXT até 16.777.215,
    // LONGTEXT acima disso) o Hibernate espera encontrar. Como a migration
    // cria a coluna como TEXT (ver V2__create_application_schema.sql),
    // declaramos length = 16000 (> 255 e <= 65.535) para o Hibernate
    // validar contra TEXT, batendo com o banco real. O truncamento para
    // 16000 caracteres continua sendo feito em
    // com.aalvarenga.billing.service.RequestLogService, na camada Java -
    // este limite é regra de negócio, não uma restrição do banco; o
    // "length" na entidade só precisa cair na faixa certa de TEXT.
    @Lob
    @Column(name = "INPUT", nullable = false, length = 16000)
    private String input;

    @Lob
    @Column(name = "OUTPUT", nullable = false, length = 16000)
    private String output;
}
