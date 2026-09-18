# Scripts de banco de dados

Os scripts de criação de banco/tabelas, carga inicial das tabelas de domínio,
triggers de auditoria e toda evolução posterior de schema **não ficam soltos
nesta pasta** — eles vivem como migrations do [Flyway](https://flywaydb.org/)
em:

```
src/main/resources/db/migration/
├── V1__create_domain_schema_and_seed_data.sql              # banco + tabelas de domínio + dados iniciais
├── V2__create_application_schema.sql                       # tabelas de aplicação (T_ACCOUNT, T_PRODUCT, T_BILL, ...)
├── V3__create_audit_infrastructure.sql                     # tabelas _AU + triggers de auditoria
├── V4__add_domain_status_backend_values.sql                # coluna BACKEND_VALUE nas tabelas T_DOMAIN_*_STATUS/T_DOMAIN_BILL_TYPE
├── V5__add_product_account_id.sql                          # T_PRODUCT.ACCOUNT_ID (FK para T_ACCOUNT)
├── V6__add_bill_cycle_and_payment_dates.sql                # T_BILL.CYCLE_START_DT/CYCLE_END_DT/DUE_DT/PAYMENT_DT
├── V7__add_bill_installment_payment_date.sql               # T_BILL_INSTALLMENT.PAYMENT_DT
├── V8__add_account_email_fallback_and_payment_brand.sql    # T_ACCOUNT.EMAIL/AUTHORIZED_FALLBACK_B, T_PAYMENT.BRAND
├── V9__add_product_disable_billing_and_cancellation.sql    # T_PRODUCT.DISABLE_BILLING_B/CANCELLATION_REQ_DT/CANCELLATION_SCH_DT
└── V10__create_config_feature_toggle.sql                   # tabela T_CONFIG_FEATURE_TOGGLE (+ _AU) - liga/desliga regras em runtime
```

Essa é a forma recomendada de entregar scripts de banco em um projeto Spring
Boot: eles ficam **versionados junto do código**, são aplicados
**automaticamente** (e na ordem certa) quando a aplicação sobe, e o Flyway
garante que cada script rode **exatamente uma vez** por banco, mesmo que a
aplicação seja reiniciada várias vezes.

Se você quiser rodar os scripts manualmente (fora da aplicação Spring), basta
executá-los na ordem V1 → V2 → ... → V10 usando o client de sua preferência
(MySQL Workbench, DBeaver, `mysql` CLI, etc.) — sempre na ordem numérica, já
que scripts posteriores costumam alterar tabelas criadas pelos anteriores
(ex: `V5` adiciona uma coluna em `T_PRODUCT`, que só existe porque `V2` já
rodou antes).

## Por que a lista cresceu com V4-V10 em vez de só V1-V3

As primeiras 3 migrations cobriram o desenho inicial do banco. Todo o
restante (`V4` a `V10`) nasceu de evoluções pedidas DEPOIS que o projeto já
tinha rodado ponta a ponta pelo menos uma vez - e, uma vez que uma migration
já rodou em algum ambiente, o Flyway grava um checksum dela e a trata como
**imutável**: editar o arquivo original (mesmo só para "arrumar" o `CREATE
TABLE`) faz a próxima subida falhar com `Migration checksum mismatch`. Por
isso, toda mudança de schema decidida depois do primeiro `V1`/`V2`/`V3`
sempre virou uma migration NOVA (`V4`, `V5`, ...), nunca uma edição das
antigas - a mesma regra vale para qualquer ferramenta de migration (Flyway,
Liquibase, Rails migrations, Django migrations...). Ver README principal,
seção "Banco de dados" → "Nota de boas práticas: por que V4+ em vez de
editar V1/V2/V3", para o detalhamento completo dessa decisão.

Um resumo do que cada uma trouxe, em ordem cronológica de motivação:

- **V4-V7**: ajustes de schema encontrados durante o primeiro teste
  end-to-end real do fluxo de criação de compra (`POST /api/v1/purchases`) -
  colunas que faltavam para o fluxo funcionar de ponta a ponta contra um
  MySQL de verdade (`BACKEND_VALUE` de domínio, FK de produto→conta, datas
  de ciclo/pagamento de fatura e parcela).
- **V8-V9**: evolução do payload de entrada/saída da criação de compra
  (e-mail e fallback de autorização da conta, marca do cartão, desativação
  de cobrança futura e datas de cancelamento do produto).
- **V10**: mecanismo de *feature toggle* (liga/desliga regras de negócio em
  runtime, sem precisar de deploy) - tabela nova, sem alterar nenhuma
  tabela existente.

Nenhuma migration foi necessária para a segunda feature do projeto (consulta
de dados, `POST /api/v1/purchases/query`, sessão de 18/09/2026): toda a
informação que ela devolve já tinha coluna/tabela criada por alguma das
migrations acima, incluindo as 6 tabelas de domínio de status
(`T_DOMAIN_ACCOUNT_STATUS` etc., com `BACKEND_VALUE` desde a `V4`) - só
nunca tinham sido mapeadas para entidade JPA porque, até aquele ponto, nada
precisava LER esse valor de volta (só gravar o ID numérico).
