# Scripts de banco de dados

Os scripts de criação de banco/tabelas, carga inicial das tabelas de domínio e
triggers de auditoria **não ficam soltos nesta pasta** — eles vivem como
migrations do [Flyway](https://flywaydb.org/) em:

```
src/main/resources/db/migration/
├── V1__create_domain_schema_and_seed_data.sql   # banco + tabelas de domínio + dados iniciais
├── V2__create_application_schema.sql            # tabelas de aplicação (T_ACCOUNT, T_PRODUCT, T_BILL, ...)
└── V3__create_audit_infrastructure.sql          # tabelas _AU + triggers de auditoria
```

Essa é a forma recomendada de entregar scripts de banco em um projeto Spring
Boot: eles ficam **versionados junto do código**, são aplicados
**automaticamente** (e na ordem certa) quando a aplicação sobe, e o Flyway
garante que cada script rode **exatamente uma vez** por banco, mesmo que a
aplicação seja reiniciada várias vezes.

Se você quiser rodar os scripts manualmente (fora da aplicação Spring), basta
executá-los na ordem V1 → V2 → V3 usando o client de sua preferência (MySQL
Workbench, DBeaver, `mysql` CLI, etc.).
