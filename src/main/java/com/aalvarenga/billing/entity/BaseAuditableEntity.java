package com.aalvarenga.billing.entity;

import com.aalvarenga.billing.util.EpochDateUtil;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

/**
 * Superclasse comum a TODAS as entidades "de aplicação" (não-domínio).
 *
 * <p>Isto é "reaproveitamento de código" na prática: em vez de repetir os
 * campos ID/CREATED_DT/MODIFIED_DT e a lógica de preenchimento automático de
 * datas em 12 classes diferentes, centralizamos tudo aqui uma única vez.
 * {@code @MappedSuperclass} diz ao JPA/Hibernate para "copiar" estes campos
 * para a tabela de cada subclasse, em vez de criar uma tabela própria para
 * esta classe (diferente de uma herança normal de Java).
 *
 * <p>Os callbacks {@link PrePersist} e {@link PreUpdate} são "ganchos" do
 * ciclo de vida do JPA: o Hibernate os invoca automaticamente um instante
 * antes de gerar o INSERT/UPDATE no banco, garantindo que NINGUÉM esqueça de
 * preencher as datas de auditoria manualmente.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // delega a geração do valor ao AUTO_INCREMENT do MySQL
    @Column(name = "ID")
    private Long id;

    @Column(name = "CREATED_DT", nullable = false, updatable = false)
    private Long createdDt;

    @Column(name = "MODIFIED_DT", nullable = false)
    private Long modifiedDt;

    @PrePersist
    protected void onCreate() {
        long now = EpochDateUtil.nowMillis();
        this.createdDt = now;
        this.modifiedDt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.modifiedDt = EpochDateUtil.nowMillis();
    }
}
