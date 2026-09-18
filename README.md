# billing-registration-service

[![CI](https://github.com/angelosemitela/billing-registration-service/actions/workflows/ci.yml/badge.svg)](https://github.com/angelosemitela/billing-registration-service/actions/workflows/ci.yml)

> O selo acima só passa a exibir o status real depois do primeiro `push`
> deste workflow para o GitHub (antes disso, o GitHub ainda não tem
> nenhuma execução para mostrar).

Serviço backend em **Java 25 + Spring Boot 4.1** que recebe, via REST, o
registro de uma compra/assinatura e grava conta, produtos, pagamentos e
faturas em um banco **MySQL**, aplicando um conjunto extenso de regras de
negócio (validação de campos, cálculo de datas de recorrência, rateio de
parcelas, etc).

Projeto de **estudo/portfólio** (não é software de produção) - por isso o
código é fortemente comentado, e este README documenta também as decisões de
design tomadas e as inconsistências encontradas na especificação original.

## Sumário

- [Como rodar o projeto](#como-rodar-o-projeto)
- [Arquitetura](#arquitetura)
- [Endpoint da API](#endpoint-da-api)
- [Consulta de dados (`POST /api/v1/purchases/query`)](#consulta-de-dados-post-apiv1purchasesquery)
- [Banco de dados](#banco-de-dados)
- [Inconsistências encontradas na especificação e decisões tomadas](#inconsistências-encontradas-na-especificação-e-decisões-tomadas)
- [Limitações conhecidas](#limitações-conhecidas)
- [Testes](#testes)
- [Evoluções futuras / outros frameworks para estudo](#evoluções-futuras--outros-frameworks-para-estudo)

## Como rodar o projeto

### Pré-requisitos

- JDK 25
- Maven 3.9+ (ou use o wrapper, se você adicionar um - `mvn -v` para conferir a versão instalada)
- MySQL 8+ rodando localmente (ou em container)

### Subindo um MySQL local rapidamente (Docker)

```bash
docker run --name billing-mysql -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=billing_db \
  -e MYSQL_USER=billing_app -e MYSQL_PASSWORD=billing_app \
  -p 3306:3306 -d mysql:8.4 \
  --log-bin-trust-function-creators=1
```

> A flag `--log-bin-trust-function-creators=1` é necessária porque a
> migration `V3__create_audit_infrastructure.sql` cria **triggers** de
> auditoria (`TRG_*_AU_UPD`/`TRG_*_AU_DEL`) - ver a seção
> ["Nota de operação: criação de triggers com binary log habilitado"](#nota-de-operação-criação-de-triggers-com-binary-log-habilitado)
> logo abaixo para o motivo completo. Se você já subiu o container SEM essa
> flag, não precisa recriá-lo: basta rodar
> `docker exec -it billing-mysql mysql -u root -proot -e "SET GLOBAL log_bin_trust_function_creators = 1;"`
> uma vez (efeito equivalente, só que não sobrevive a um `docker restart`).

> ⚠️ **Duas pegadinhas comuns ao digitar esse comando de memória**:
>
> 1. **Posição da flag `--log-bin-trust-function-creators=1`**: ela precisa
>    vir *depois* do nome da imagem (`mysql:8.4`), nunca antes. A sintaxe do
>    `docker run` é `docker run [OPÇÕES DO DOCKER] IMAGEM [ARGUMENTOS DO
>    PROCESSO DENTRO DO CONTAINER]` - tudo que vem depois da imagem é
>    repassado para o `mysqld`, não é uma opção do Docker. Colocá-la antes de
>    `mysql:8.4` gera o erro `unknown flag: --log-bin-trust-function-creators`.
> 2. **Recriar o container sem `MYSQL_USER`/`MYSQL_PASSWORD`**: essas duas
>    variáveis são o que faz a imagem oficial do MySQL criar
>    automaticamente o usuário `billing_app` (com `GRANT ALL` já aplicado
>    sobre `billing_db`) na primeira subida. Se você recriar o container
>    (`docker rm` + `docker run` de novo) omitindo essas duas variáveis, só
>    o usuário `root` existirá, e a aplicação falhará ao conectar (usuário
>    `billing_app` não existe). Se isso acontecer e você não quiser
>    recriar o container de novo, dá pra criar o usuário manualmente:
>    ```bash
>    docker exec -it billing-mysql mysql -u root -proot -e "CREATE USER 'billing_app'@'%' IDENTIFIED BY 'billing_app'; GRANT ALL PRIVILEGES ON billing_db.* TO 'billing_app'@'%'; FLUSH PRIVILEGES;"
>    ```

### Rodando a aplicação

```bash
mvn spring-boot:run
```

Na primeira subida, o **Flyway** cria automaticamente o banco, as tabelas, os
dados de domínio e as triggers de auditoria (ver seção
[Banco de dados](#banco-de-dados)) - não é necessário rodar nenhum script
manualmente.

Health check: `GET http://localhost:8080/actuator/health`

### Variáveis de ambiente (todas têm um valor padrão de desenvolvimento)

| Variável      | Padrão                                              |
|---------------|------------------------------------------------------|
| `SERVER_PORT` | `8080`                                                |
| `DB_URL`      | `jdbc:mysql://localhost:3306/billing_db?...`          |
| `DB_USERNAME` | `billing_app`                                         |
| `DB_PASSWORD` | `billing_app`                                         |

### Nota de operação: criação de triggers com binary log habilitado

Na primeira execução com um MySQL 8.4 "do zero" (imagem oficial do Docker),
a migration `V3__create_audit_infrastructure.sql` pode falhar com:

```
Error Code: 1419
Message: You do not have the SUPER privilege and binary logging is
enabled (you *might* want to use the less safe
log_bin_trust_function_creators variable)
```

**Causa raiz**: essa migration cria 22 *triggers* (`BEFORE UPDATE`/`BEFORE
DELETE`, uma dupla para cada tabela auditável) que gravam uma "fotografia"
do registro antes de ele ser alterado/excluído. O MySQL 8.4 já vem com
*binary logging* habilitado por padrão (usado para replicação e
recuperação point-in-time) - e, quando o binary log está ativo, criar
rotinas armazenadas (funções e, em algumas configurações/versões,
triggers) exige o privilégio `SUPER` **ou** a variável de sistema
`log_bin_trust_function_creators = 1`. O motivo é a segurança da
replicação: uma trigger "mal comportada" poderia gravar dados diferentes
em cada réplica se o log fosse baseado em instruções (`STATEMENT`) em vez
de linhas alteradas. O usuário da aplicação (`billing_app`) não tem (e não
deveria ter, por princípio de menor privilégio) o privilégio `SUPER` -
então o próprio Flyway, rodando com esse usuário, esbarra na restrição.

**Correção**: habilitamos `log_bin_trust_function_creators` no próprio
container MySQL - via flag `--log-bin-trust-function-creators=1` no
`docker run` (fica persistido enquanto o container existir) ou, para um
container já em execução, com
`SET GLOBAL log_bin_trust_function_creators = 1;` (efeito imediato, mas
some se o processo do MySQL for reiniciado). Isso não é uma mudança de
código do projeto - é puramente configuração do ambiente de banco local.

**Alternativas para estudo futuro** (mais robustas para um cenário real
com replicação, onde "confiar" na criação de rotinas é mais delicado):
usar `SQL SECURITY INVOKER` nas triggers (não resolve sozinho esse erro
específico, mas reduz o escopo de permissões com que a trigger roda);
desabilitar o binary log inteiramente para um ambiente 100% local sem
replicação (`--skip-log-bin`, mais simples ainda para este projeto, já que
não há réplicas); ou, em um MySQL gerenciado na nuvem (RDS, Cloud SQL
etc.), conceder ao usuário de deploy uma role equivalente a `SUPER`
apenas durante a execução das migrations (nunca em produção contínua).

## Arquitetura

```
com.aalvarenga.billing
├── controller/       -> camada web (REST), sem lógica de negócio
├── dto/
│   ├── request/      -> records que espelham o JSON de entrada
│   └── response/     -> records que espelham o JSON de saída
├── entity/           -> entidades JPA (mapeiam as tabelas do MySQL)
│   └── converter/    -> conversores JPA (ex: boolean <-> CHAR(1))
├── enums/            -> valores fixos (ENUMs) do contrato de entrada
├── exception/        -> BusinessException + tratamento centralizado de erros
├── repository/       -> repositórios Spring Data JPA
├── service/          -> TODA a lógica de negócio (validação, cálculo, persistência)
├── util/             -> utilitários reaproveitáveis (datas, dinheiro, rateio)
└── config/           -> configurações (ex: parâmetros de negócio via .yml)
```

### Fluxo de uma requisição

```
PurchaseController
      │
      ▼
PurchaseService (fachada)
      │
      ├──► PurchaseValidationService  (valida TODAS as regras de negócio;
      │                                 lança BusinessException se algo falhar)
      │
      ├──► PurchaseOrchestrationService (@Transactional - grava tudo ou nada)
      │        ├─ AccountService   (conta, documentos, endereços, telefones)
      │        ├─ PaymentService   (pagamentos e tokens)
      │        ├─ ProductService   (produtos, descontos, datas de recorrência)
      │        └─ BillingService   (faturas, taxas, parcelas)
      │
      └──► RequestLogService (grava T_LOG - SEMPRE, sucesso ou erro,
                               em uma transação própria e independente)
```

Essa separação (validar → persistir → responder → logar) foi escolhida de
propósito: cada camada tem uma única responsabilidade, o que facilita tanto
o entendimento do fluxo quanto a escrita de testes unitários isolados (ver
[Testes](#testes)).

### Por que não usar só Bean Validation (`@NotNull` etc.)?

A maioria das regras do enunciado é **condicional** ("obrigatório somente
se..."), **cruzada** (envolve 2+ campos) ou depende de **consulta ao banco**
(ex: "o país existe na tabela de domínio?"). Bean Validation resolve bem
casos simples e isolados, mas não expressa naturalmente esse tipo de regra
nem permite escolher, campo a campo, entre os códigos HTTP 400/404/409/412.
Por isso usamos Bean Validation apenas para a validação estrutural mínima
(campos realmente sempre obrigatórios) e concentramos todo o resto em
`PurchaseValidationService`, de forma explícita e testável.

## Endpoint da API

```
POST /api/v1/purchases
Content-Type: application/json
```

Corpo de entrada e saída seguem exatamente o contrato descrito no documento
original do projeto (`projeto.txt`), com os ajustes de nomenclatura
documentados na seção de inconsistências abaixo.

Além deste, existe um segundo endpoint de CONSULTA (leitura) do que já foi
persistido - ver seção "Consulta de dados" logo abaixo.

## Consulta de dados (`POST /api/v1/purchases/query`)

```
POST /api/v1/purchases/query
Content-Type: application/json
```

Endpoint de leitura, adicionado em 18/09/2026 a partir de um segundo
documento de especificação (`docs/consulta-dados.txt`), devolvendo conta,
produtos, pagamentos e faturas já persistidos - por `externalId` da conta
(devolve tudo) ou por `productId` (devolve só aquele produto e o que está
diretamente ligado a ele).

**Por que `POST` para uma leitura?** A spec HTTP desaconselha corpo em
`GET` (nem todo cliente/proxy repassa), e o filtro de entrada tem 6 campos
opcionais, incluindo booleans com valor padrão `true` - GET com query
params funcionaria, mas ficaria bem menos legível que JSON. É o mesmo
padrão usado por APIs de busca complexa no mercado (o endpoint `_search`
do Elasticsearch/OpenSearch também é `POST`, apesar de ser leitura).

**Decisões tomadas com o usuário** (pergunta de múltipla escolha, mesmo
padrão usado nas decisões originais do projeto):

1. **Desconto "vigente" na lista `products[].discount`**: o anexo original
   contradizia a si mesmo (uma regra dizia `endDt` MENOR que o sysdate,
   outra, para `nextBillValue`, dizia `endDt` MAIOR que uma data de
   referência). Decisão: "vigente" = `status = 1` E `endDt` no FUTURO em
   relação ao instante atual - combina com o sentido usual de "ativo" e
   com a regra de `nextBillValue`.
2. **`cardNumber` no retorno de pagamento**: o anexo não previa máscara
   para este campo (só para o documento). Decisão: mascarar, mas mantendo
   os 4 últimos dígitos visíveis (diferente do documento, que mostra só os
   2 últimos) - regra especial: se o valor tiver 4 posições ou menos, fica
   mascarado por inteiro. Ver `MaskingUtil`.
3. **Verbo/caminho do endpoint**: `POST /api/v1/purchases/query` (ver
   racional acima).
4. **Formato dos IDs dentro de `bill[]`**: `productId`/`billId` são
   formatados com prefixo (`PROD_x`/`BILL_x`), por consistência com o
   resto da API - o anexo original mostrava esses dois campos sem prefixo
   no exemplo, mas o próprio comentário do `billId` já dizia que deveria
   ser formatado assim (mais um exemplo desatualizado, como outros já
   documentados neste README).

**Outras decisões, tomadas sem bloquear no usuário** (por dedução direta a
partir do schema já existente ou por não haver ambiguidade real - ver doc
de decisões do projeto para o detalhamento completo de cada uma):

- **`splitValue` da fatura**: o anexo descreve a fórmula como
  `productValue / installments`, mas o próprio exemplo numérico do anexo só
  fecha usando `chargedValue / installments` (mais um copy-paste
  inconsistente) - implementado com `chargedValue`, por bater com o
  exemplo E fazer mais sentido de negócio ("quanto o cliente paga por
  parcela" depende do valor cobrado, não do preço bruto do produto antes de
  desconto/imposto).
- **Formato aceito para o `productId` de ENTRADA** (filtro da consulta):
  aceita tanto o formato já devolvido pela própria API (`"PROD_2"` - o
  mesmo valor que um consumidor já recebeu antes) quanto o ID técnico puro
  (`"2"`), por robustez.
- **Prioridade quando `externalId` E `productId` são informados juntos**: o
  anexo trata os dois campos como alternativas mutuamente exclusivas e
  nunca diz o que fazer se ambos vierem preenchidos. `productId` tem
  prioridade, por ser o identificador mais RESTRITIVO (escopo de um único
  produto) - `externalId` é o nível mais geral (toda a conta), então usá-lo
  devolveria mais dados do que o chamador provavelmente queria. **Ajustado
  em 18/09/2026**: a primeira versão fazia o oposto (`externalId`
  prioritário, por ser "o identificador de nível mais alto"), até o usuário
  observar em teste manual que o resultado mais geral não era o esperado
  quando os dois IDs eram enviados juntos.
- **Pagamento ao consultar por `productId`**: o schema atual não tem uma
  FK direta de pagamento para produto - o único vínculo é
  `T_PRODUCT.DEFAULT_PAYMENT_ID`. Por isso, "pagamento associado ao
  produto" é implementado como o pagamento padrão do produto (só ele, se
  existir e estiver ativo).
- **`GlobalExceptionHandler` para JSON malformado**: como este handler é
  GLOBAL (cobre os dois endpoints), ele agora decide, pela URI da
  requisição, se devolve o corpo de erro no formato de
  `PurchaseResponse` (compra) ou `QueryResponse` (consulta) - sem essa
  correção, um JSON malformado na consulta voltaria com os nomes de campo
  errados (`product`/`billing` em vez de `products`/`bill`, mais um
  `protocol` que não existe neste contrato).
- **Log de auditoria**: a consulta também grava em `T_LOG`, reaproveitando
  `RequestLogService` (mesma tabela/serviço da criação de compra) - como
  não existe um "protocol" de verdade neste endpoint, é sintetizada uma
  chave (`"QUERY externalId=... productId=..."`) só para fins de
  rastreio/auditoria.
- **Tabelas de domínio de status**: `T_DOMAIN_ACCOUNT_STATUS`/
  `_PRODUCT_STATUS`/`_DISCOUNT_STATUS`/`_PAYMENT_STATUS`/`_BILL_STATUS`/
  `_BILL_TYPE` já existiam desde a V1/V4 (usadas até aqui só para o
  `BACKEND_VALUE`, nunca mapeadas para entidade JPA porque nada ainda
  precisava LER esse valor de volta). A consulta foi a primeira feature a
  precisar disso - nenhuma migration nova foi necessária, só as 6 entidades/
  repositórios novos (`DomainAccountStatusEntity` etc.) e um
  `DomainStatusLookupService` que busca em LOTE (evita N+1 quando a
  resposta tem várias linhas).

**Não coberto por teste automatizado ainda** (pendência registrada no doc
de decisões do projeto): rodar de ponta a ponta contra um MySQL real -
assim como o resto do projeto, a suíte desta feature é 100% testes
unitários com Mockito (`PurchaseQueryValidationServiceTest`,
`PurchaseQueryServiceTest`, `MaskingUtilTest`), sem nenhum teste de
integração com banco.

## Banco de dados

Os scripts de criação de banco ficam versionados como *migrations* do
[Flyway](https://flywaydb.org/) em `src/main/resources/db/migration/`:

| Script | Conteúdo |
|---|---|
| `V1__create_domain_schema_and_seed_data.sql` | banco + tabelas de domínio (`T_DOMAIN_*`) já populadas |
| `V2__create_application_schema.sql` | tabelas de aplicação (`T_ACCOUNT`, `T_PRODUCT`, `T_BILL`, ...) |
| `V3__create_audit_infrastructure.sql` | tabelas `_AU` + triggers `BEFORE UPDATE`/`BEFORE DELETE` de auditoria |
| `V4__add_domain_status_backend_values.sql` | coluna `BACKEND_VALUE` nas tabelas `T_DOMAIN_*_STATUS`/`T_DOMAIN_BILL_TYPE` |
| `V5__add_product_account_id.sql` | `T_PRODUCT.ACCOUNT_ID` (FK para `T_ACCOUNT`) |
| `V6__add_bill_cycle_and_payment_dates.sql` | `T_BILL.CYCLE_START_DT`/`CYCLE_END_DT`/`DUE_DT`/`PAYMENT_DT` |
| `V7__add_bill_installment_payment_date.sql` | `T_BILL_INSTALLMENT.PAYMENT_DT` |
| `V8__add_account_email_fallback_and_payment_brand.sql` | `T_ACCOUNT.EMAIL`/`AUTHORIZED_FALLBACK_B`, `T_PAYMENT.BRAND` |
| `V9__add_product_disable_billing_and_cancellation.sql` | `T_PRODUCT.DISABLE_BILLING_B`/`CANCELLATION_REQ_DT`/`CANCELLATION_SCH_DT` |
| `V10__create_config_feature_toggle.sql` | tabela `T_CONFIG_FEATURE_TOGGLE` (+ `_AU`) - liga/desliga regras em runtime |

Ver também `database/README.md` para mais detalhes sobre por que os scripts
vivem ali (em vez de soltos em uma pasta separada).

### Nota de boas práticas: por que V4+ em vez de editar V1/V2/V3

Depois que uma migration já rodou num ambiente (o Flyway grava um checksum
dela em `flyway_schema_history`), ela vira **imutável**: editar o arquivo
retroativamente - mesmo que só para "arrumar" o `CREATE TABLE` original -
faz o Flyway recusar a próxima subida com `Migration checksum mismatch`,
porque o conteúdo do arquivo não bate mais com o que foi validado da
primeira vez. A regra vale para qualquer ferramenta de migration (Flyway,
Liquibase, Rails migrations, Django migrations...): toda mudança de schema
decidida DEPOIS que a migration anterior já foi aplicada em algum ambiente
vira uma **migration nova**, nunca uma edição da antiga - é assim que
`V4`, `V5`, `V6` e `V7` deste projeto nasceram, todas depois do primeiro
teste end-to-end bem-sucedido.

### Auditoria de tabelas

Toda tabela de **aplicação** (não-domínio - `T_ACCOUNT`, `T_PRODUCT`,
`T_BILL`, `T_BILL_INSTALLMENT` etc.) tem uma tabela gêmea de auditoria com
sufixo `_AU` (`T_ACCOUNT_AU`, `T_PRODUCT_AU`, ...), criada em
`V3__create_audit_infrastructure.sql`. Cada uma dessas tabelas `_AU`
replica **todas** as colunas da tabela original, mais duas colunas
próprias: `AUDIT_DT` (quando a auditoria foi gravada) e `COMMAND`
(`'UPDATE'` ou `'DELETE'`).

Duas triggers por tabela (`TRG_<tabela>_AU_UPD` e `TRG_<tabela>_AU_DEL`,
ambas `BEFORE UPDATE`/`BEFORE DELETE`) fazem essa gravação automaticamente:
antes de uma linha ser alterada ou excluída, o valor **anterior** (`OLD.*`)
é copiado para a tabela `_AU` correspondente - uma "fotografia" do estado
anterior, sem depender de nenhum código Java para isso.

**Duas armadilhas já encontradas neste projeto por causa desse padrão**
(ambas resolvidas, ver as seções de compatibilidade mais abaixo):
1. **Nada sincroniza `_AU` automaticamente com a tabela original.** Sempre
   que uma migration nova (`V5`, `V6`, `V7`...) adiciona/altera uma coluna
   na tabela de aplicação, a tabela `_AU` e as duas triggers precisam ser
   atualizadas manualmente na mesma migration - inclusive as triggers, que
   precisam ser recriadas do zero (`DROP TRIGGER` + `CREATE TRIGGER`) já
   que o MySQL não suporta `ALTER TRIGGER`.
2. **Todo ajuste de *tipo* de coluna também precisa ser replicado.** Foi
   assim que o mesmo bug de "Row size too large" apareceu duas vezes neste
   projeto: primeiro em `T_LOG` (`V2`), depois em `T_LOG_AU` (`V3`) - ver
   ["Nota de compatibilidade: limite de tamanho de linha do MySQL"](#nota-de-compatibilidade-limite-de-tamanho-de-linha-do-mysql-t_log-e-t_log_au).

### Convenções de modelagem (aplicadas em TODAS as tabelas)

- Datas: `BIGINT` (epoch em milissegundos) - conversão feita em `EpochDateUtil`.
- Booleanos: `CHAR(1)` (`"1"`/`"0"`) - conversão feita em `BooleanCharConverter` (JPA `AttributeConverter`).
- Toda tabela de aplicação (não-domínio) tem uma tabela `_AU` gêmea e duas triggers (`BEFORE UPDATE`, `BEFORE DELETE`) que fotografam o registro antes da alteração/exclusão - ver ["Auditoria de tabelas"](#auditoria-de-tabelas) acima.

## Inconsistências encontradas na especificação e decisões tomadas

Conforme pedido no enunciado ("se encontrar inconsistências... me procure
para tentar responder já com alguma proposta"), a lista abaixo documenta
TUDO que foi encontrado. As 4 primeiras foram levadas diretamente ao usuário
(via perguntas de múltipla escolha) antes da implementação; as demais foram
resolvidas com uma decisão registrada aqui, seguindo a mesma lógica ("boas
práticas" e a intenção mais provável do texto original).

### Decisões confirmadas com o usuário

1. **Idempotência do `protocol`**: um protocolo só é bloqueado (`409`) se já
   existir um log de **sucesso** anterior para ele; um protocolo cujo único
   histórico é de erro pode ser reprocessado normalmente.
2. **Trial + billing**: uma fatura (`billing`) PODE existir para um produto
   trial, desde que `chargedValue = 0`; caso contrário, erro `412`.
3. **Geração de IDs**: o enunciado cita uma "regra de formação do
   AssetNumber" que nunca é definida. Assumimos que todo ID retornado ao
   cliente (conta, produto, pagamento, fatura) é o ID técnico (auto-increment)
   da respectiva tabela, convertido para `String`. **Atualização**: por
   pedido do usuário, passou a levar um prefixo curto por tipo de entidade
   para ficar mais legível (`ACCT_1`, `PROD_2`, `PAY_3`, `BILL_4`) - ver
   `com.aalvarenga.billing.util.AssetIdFormatter`. O prefixo é só de
   apresentação: a chave técnica continua um `BIGINT` puro no banco (ver
   javadoc da classe para o porquê disso importar).
4. **Campo `payment` na resposta**: os métodos de pagamento são **sempre
   persistidos** no banco quando informados (necessários para cobranças
   futuras de recorrência), mas só aparecem no JSON de resposta quando há
   cobrança efetiva (`chargedValue` somado > 0) em alguma fatura.

### Demais inconsistências (decisão registrada, sem necessidade de bloquear o desenvolvimento)

- **Payload de exemplo não é um JSON válido**: faltam vírgulas, dois-pontos e
  chaves de fechamento (ex.: `"protocol" "WEB-12345"` sem `:`, `"value" 0.5`
  sem `:` na estrutura `tax`). Tratado como um exemplo ilustrativo comentado;
  o contrato real foi inferido a partir da intenção descrita nos comentários.
- **`T_PRODUCT.PRODUCT_ID`** é descrito como vindo de `product.id`, mas a
  entrada só tem `product.codeId`. Assumimos `PRODUCT_ID = codeId`.
  **Importante** (dúvida recorrente): `PRODUCT_ID` NÃO é uma cópia do `ID`
  técnico da tabela - é o identificador do produto no sistema EXTERNO que
  chamou a API (o `codeId` do payload de entrada, ex.: `"1"`, `"2"`), usado
  para ecoar de volta o campo `codeId` na resposta. Quem replica o `ID`
  técnico é a coluna `ASSET_ID` (ver decisão nº3 acima). Por guardar uma
  informação genuinamente diferente (a referência do chamador), mantivemos
  `PRODUCT_ID` na tabela em vez de removê-la.
- **`T_PRODUCT` sem vínculo com `T_ACCOUNT`**: até a `V5`, não havia nenhuma
  coluna ligando um produto à conta que o comprou - a associação só existia
  "de passagem", dentro da mesma requisição HTTP. Adicionamos `ACCOUNT_ID`
  (FK, `NOT NULL`), indispensável para consultas futuras do tipo "quais
  produtos pertencem à conta X" (e para o job de recorrência).
- **Nome de tabela inconsistente**: o enunciado usa `TB_DOMAIN_CURRENCY` (com
  `TB_`) enquanto todas as outras tabelas de domínio usam `T_`. Padronizado
  para `T_DOMAIN_CURRENCY`.
- **Campos de saída com grafia diferente da entrada**: a saída usa
  `"codeid"` (d minúsculo); padronizado para `codeId` (camelCase) nos DTOs
  de resposta.
- **`T_PAYMENT` sem vínculo com `T_ACCOUNT`**: o enunciado não define nenhuma
  coluna de ligação entre pagamento e conta. Adicionamos `ACCOUNT_ID` (FK),
  indispensável para localizar os meios de pagamento salvos de um assinante.
- **`T_PAYMENT_TOKEN` sem vínculo com `T_PAYMENT`**: mesma situação;
  adicionamos `PAYMENT_ID` (FK).
- **`T_BILL` sem vínculo específico com `T_PAYMENT`**: o enunciado só guarda
  `PAYMENT_METHOD` como texto solto. Adicionamos `PAYMENT_ID` (FK, opcional)
  para rastrear exatamente qual pagamento financiou aquela fatura.
- **`T_PAYMENT.DEFAULT_B`** está descrito, no enunciado, como vindo de
  `payment.isMultiple` (igual ao campo `MULTIPLE_B` logo acima) - claramente
  um erro de cópia. Mapeado corretamente a partir de `payment.isDefault`.
- **Regra de trial (`isTrial`) e token de expiração**: o enunciado reaproveita,
  para `payment.token.expirationDate`, o mesmo texto de validação usado em
  `transactionDate` ("se a data estiver no futuro, erro"), o que não faz
  sentido para uma data de EXPIRAÇÃO. Interpretado como erro de cópia; essa
  restrição não foi aplicada a `expirationDate`.
- **`account` como lista**: modelado no contrato como array, mas toda a
  regra de negócio (uma compra = um assinante) só faz sentido para
  exatamente 1 elemento. Validamos explicitamente que a lista tenha
  exatamente 1 item.
- **`discountCycles` para produtos `ONESHOT`**: o enunciado diz que o campo
  "só terá aplicação para type=RECURRENCE", mas o próprio payload de exemplo
  envia `discountCycles` em um produto `ONESHOT`. Interpretamos que a
  regra de "mínimo 1 ciclo" não se aplica a `ONESHOT`, mas o desconto ainda é
  registrado normalmente em `T_DISCOUNT` quando informado.
- **`recurrenceFrequency` ausente em produto `ONESHOT`**: usado para calcular
  `cycleEndDate` (que, por regra explícita, segue a mesma fórmula de
  `nextBillDate` mesmo para `ONESHOT`). Quando ausente, assumimos `MONTH`
  como frequência padrão apenas para esse cálculo.
- **Status inicial de `T_BILL` = 4 ("Paga aguardando repasse")**: parece
  contraintuitivo para uma fatura recém-criada (o esperado seria "1 -
  Emitida"), mas é o valor explicitamente pedido no enunciado - mantido como
  especificado.
- **Vínculo `billing.paymentMethod` ↔ `payment[].method`**: o enunciado não
  exige explicitamente que o método de uma fatura exista na lista de
  pagamentos informados, mas validamos essa consistência (erro `400` se não
  houver correspondência), por ser uma checagem de integridade mínima
  razoável.

### Evoluções pedidas após o primeiro teste end-to-end (14-15/09/2026)

Com o serviço já rodando de ponta a ponta, o usuário pediu um conjunto de
ajustes pensando em consultas futuras (um próximo `GET`) e no job de
recorrência. Todos entraram como migrations NOVAS (`V4` a `V7` - ver a nota
de boas práticas acima sobre por que não editamos `V1`/`V2`/`V3`):

- **`BACKEND_VALUE` nas tabelas `T_DOMAIN_*_STATUS`/`T_DOMAIN_BILL_TYPE`**
  (`V4`): até aqui, essas tabelas só tinham `STATUS_DESC` (descrição em
  português, para humanos). `BACKEND_VALUE` é um valor estável em inglês
  (`ACTIVE`, `CANCELLED`, `PAID`...), pensado para ser devolvido em uma
  futura API de consulta - várias linhas mapeiam para o mesmo
  `BACKEND_VALUE` de propósito (ex.: os status de fatura 4/5/6 - "paga
  aguardando repasse" / "em liquidação de parcelas" / "paga" - todos viram
  `PAID` para quem consome de fora, mesmo sendo 3 granularidades internas
  diferentes). **Suposição não confirmada**: acrescentamos
  `T_DOMAIN_ACCOUNT_DOCUMENT_STATUS` à lista (não estava no pedido original)
  por seguir exatamente o mesmo padrão `1=Ativo/2=Cancelado` das outras três
  tabelas de status de conta - avise se não for para incluir.
- **`T_PRODUCT.ACCOUNT_ID`** (`V5`): ver bullet dedicado logo acima, na
  lista de inconsistências.
- **`T_BILL.CYCLE_START_DT`/`CYCLE_END_DT`/`DUE_DT`/`PAYMENT_DT`** (`V6`):
  os dois primeiros replicam a mesma vigência já calculada para o produto
  correspondente (mesma regra de `T_PRODUCT`, só copiada no momento da
  criação da fatura); `DUE_DT` usa o mesmo valor de `TRANSACTION_DT` no
  fluxo de `/api/v1/purchases`; `PAYMENT_DT` fica `NULL` até que um
  processo futuro efetive o repasse (mudando `STATUS` para "6 - Paga").
- **`T_BILL_INSTALLMENT.PAYMENT_DT`** (`V7`): mesma lógica de
  `T_BILL.PAYMENT_DT`, só que por parcela.
- **Prefixo nos IDs de saída** (`ACCT_`/`PROD_`/`PAY_`/`BILL_`): ver decisão
  nº3 atualizada acima e `com.aalvarenga.billing.util.AssetIdFormatter`.

### Evoluções pedidas em 17/09/2026

Uma segunda rodada de ajustes, também via migrations novas (`V8` a `V10`
- mesma regra de nunca editar migration já aplicada):

- **Renomeação `transactionDate` → `transactionDt`** no payload raiz da
  requisição: só um ajuste de nomenclatura, para manter o padrão já usado em
  todos os outros campos de data do contrato (sufixo `Dt`/`DT`, nunca
  `Date`). Não gera coluna nova nem migration - é só `PurchaseRequest` e
  quem o consome (`PurchaseValidationService`, testes).
- **`account.email`** (`T_ACCOUNT.EMAIL`, `V8`): e-mail da conta, texto
  livre. **Sempre obrigatório**, em toda requisição - diferente de
  `name`/`externalId`, que só são exigidos quando a conta é nova (ver
  `PurchaseValidationService.validateAccount`).
- **`account.isAuthorizedFallback`** (`T_ACCOUNT.AUTHORIZED_FALLBACK_B`,
  `V8`): booleano indicando se o assinante autoriza cobrança em um método de
  pagamento alternativo quando o principal falha (ex.: sem sucesso no
  `CREDIT`, tenta automaticamente no `DEBIT`). Também sempre obrigatório.
  Por enquanto só é **persistido** - a lógica de retentativa em si (qual
  serviço decide "falhou, tenta o próximo") é um passo futuro, fora do
  escopo desta compra inicial.
- **`payment.brand`** (`T_PAYMENT.BRAND`, `V8`) + enum `CardBrand`
  (`VISA`/`MASTERCARD`/`AMEX`/`ELO`): obrigatório apenas quando
  `payment.method` é `CREDIT` ou `DEBIT` (`PaymentMethod.isCardBased()`).
  Um valor de enum inválido (ex.: `"brand": "DINERS"`) já é rejeitado
  automaticamente pelo Jackson, na desserialização - antes mesmo de o
  código de validação rodar - e cai no mesmo tratamento de "JSON malformado"
  (`GlobalExceptionHandler.handleMalformedJson`, HTTP 400) já usado pelos
  outros enums do contrato (`PaymentMethod`, `ProductType`,
  `RecurrenceFrequency`, `DocumentType`, `AddressType`). Por isso
  `PurchaseValidationService` só precisa checar ausência (`null`), não
  "valor inválido" - essa segunda checagem já é gratuita.
- **`T_PRODUCT.DISABLE_BILLING_B`** (`V9`): sempre `'1'` quando
  `product.type` é `"ONESHOT"`, sempre `'0'` nos demais casos. Regra
  derivada de outra coluna já existente na própria linha (`TYPE`) - por
  isso, diferente de `V8` (ver abaixo), esta coluna pôde nascer `NOT NULL`
  sem exigir reset do banco: a migration faz `ADD COLUMN NULL` → `UPDATE`
  com a regra `CASE WHEN TYPE = 'ONESHOT' THEN '1' ELSE '0' END` → `MODIFY
  NOT NULL` (mesmo padrão de 3 passos já usado em `V4` para
  `BACKEND_VALUE`).
- **Mecanismo de *feature toggle*** (tabela nova `T_CONFIG_FEATURE_TOGGLE` +
  `_AU`, `V10`): uma tabela de configuração que liga/desliga, em runtime
  (sem novo deploy), regras de negócio condicionais. Curiosidade de
  modelagem: apesar de ser uma tabela de "configuração", ela **é** auditada
  (tem `_AU` + triggers, como qualquer tabela de aplicação) - diferente das
  `T_DOMAIN_*`, que não são auditadas por não mudarem por ação de usuário
  final. A diferença é justamente que `STATUS_B` de uma regra **pode** ser
  alterado em produção (ex.: um administrador desligando a regra), então
  faz sentido rastrear quem mudou o quê. Lida através de
  `FeatureToggleService.isEnabled(String ruleName)`, com nomes de regra
  centralizados em `FeatureToggleRules` (mesmo padrão de constantes já usado
  em `DomainStatus`) - e com um comportamento *fail-safe*: se o nome da
  regra não existir na tabela, é tratado como **desligada** (`false`) e um
  aviso é logado, em vez de lançar exceção.
  - Regra semeada (nasce **ligada**, `STATUS_B = '1'`):
    `AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT` - "Agenda o cancelamento
    automático para assinantes que possuem serviço associado em compras One
    Shot".
- **`T_PRODUCT.CANCELLATION_REQ_DT`/`CANCELLATION_SCH_DT`** (`V9`, sempre
  `NULLABLE`): preenchidas por `ProductService.persistProducts` somente
  quando a regra `AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT` está **ligada** E
  o produto é `product.type = "ONESHOT"` com `product.isExpiriationService
  = true`. Nesse caso, `CANCELLATION_REQ_DT` recebe a data da própria
  requisição (`transactionDt`, em epoch ms) e `CANCELLATION_SCH_DT` recebe o
  `CYCLE_END_DT` já calculado pelo serviço de recorrência. Em qualquer outro
  caso (regra desligada, produto não é `ONESHOT`, ou não tem vigência),
  ambas as colunas ficam `NULL`.
- **`exemplo-requisicao.json`** foi atualizado com todos os campos acima
  (`transactionDt`, `account.email`, `account.isAuthorizedFallback`,
  `payment.brand` em cada um dos dois pagamentos de exemplo).

**Atenção - reset do banco local exigido para `V8`**: assim como em
`V5`/`V6` (colunas sem valor "correto" possível de adivinhar para uma linha
já existente), `T_ACCOUNT.EMAIL` e `T_ACCOUNT.AUTHORIZED_FALLBACK_B` nascem
`NOT NULL` sem um passo de *backfill* - a migration assume a tabela vazia.
Antes de rodar `mvn spring-boot:run` depois de baixar esta versão, resete o
banco de desenvolvimento:

```bash
docker exec -it billing-mysql mysql -u root -proot \
  -e "DROP DATABASE billing_db; CREATE DATABASE billing_db;"
```

`V9` e `V10` **não** exigem esse reset: `V9.DISABLE_BILLING_B` é
preenchida por *backfill* determinístico (a partir de `TYPE`, já existente
na mesma linha) antes de virar `NOT NULL`, e as demais colunas novas
(`CANCELLATION_REQ_DT`/`CANCELLATION_SCH_DT`, `T_PAYMENT.BRAND`) nascem
`NULLABLE`; `V10` só cria uma tabela nova.

### Nota de compatibilidade: Jackson 3 no Spring Boot 4.1

O Spring Boot 4.1 (Spring Framework 7) passou a usar o **Jackson 3** como
biblioteca JSON padrão em vez do Jackson 2 (pacotes `com.fasterxml.jackson.*`
viraram `tools.jackson.*`, e `ObjectMapper` foi substituído, na prática, por
`JsonMapper`, sua subclasse imutável e "pronta para JSON"). Isso quebra
qualquer código que ainda importe as classes antigas do Jackson 2
(`com.fasterxml.jackson.databind.ObjectMapper`,
`com.fasterxml.jackson.core.JsonProcessingException`) - foi exatamente o que
aconteceu neste projeto em `PurchaseService` e `GlobalExceptionHandler`, que
falhavam ao compilar com `package com.fasterxml.jackson.core does not exist`.

**Decisão tomada**: migrar os dois pontos de uso para a API nativa do
Jackson 3 (`tools.jackson.databind.json.JsonMapper` +
`tools.jackson.core.JacksonException`, esta última agora *unchecked*), em vez
de reativar o Jackson 2 via o módulo de compatibilidade
`spring-boot-jackson2`. Optamos pela migração porque:
1. O próprio módulo de compatibilidade é documentado pelo Spring como
   **depreciado** ("should not be relied upon in the longer term");
2. O uso de Jackson neste projeto é mínimo (só serialização de logs em
   `toJsonSafely`), então o custo da migração é baixíssimo;
3. Para um projeto de estudo, faz mais sentido aprender a API atual do que
   já nascer "usando algo deprecado por compatibilidade".

**Para quem quiser estudar o caminho alternativo** (útil em projetos legados
maiores, onde reescrever todo uso de Jackson de uma vez não é viável): basta
adicionar a dependência `org.springframework.boot:spring-boot-jackson2` ao
`pom.xml` (sem precisar declarar versão - gerenciada pelo BOM do Spring
Boot) e configurar `spring.http.converters.preferred-json-mapper=jackson2`
no `application.yml`, mantendo o código Java inalterado.

### Nota de compatibilidade: Lombok exige configuração explícita a partir do JDK 23

Ao corrigir o problema do Jackson acima, o Lombok (`@Builder`, `@Getter`/
`@Setter`, `@Slf4j`, `@RequiredArgsConstructor`) continuou **completamente
inoperante** em todo o projeto - toda chamada a `.builder()`, todo getter/
setter gerado e até o campo `log` do `@Slf4j` davam `cannot find symbol`, e
o Spring nem conseguia instanciar os `@Service`/`@RestController` porque o
construtor gerado por `@RequiredArgsConstructor` simplesmente não existia
("variable X not initialized in the default constructor").

**Causa raiz**: a partir do **JDK 23**, o `javac` deixou de escanear
automaticamente o classpath de compilação em busca de annotation
processors (medida de segurança contra jars maliciosos disfarçados de
processor - um processor mal-intencionado roda código arbitrário durante a
compilação). Isso quebra silenciosamente qualquer projeto Maven que
dependa da descoberta automática do Lombok, como este (JDK 25). Não é um
erro de configuração deste projeto especificamente - é uma mudança de
comportamento do próprio `javac`/Maven que afeta qualquer projeto Lombok +
JDK 23+ sem esse ajuste, e por isso vale sempre conferir a documentação
oficial (https://projectlombok.org/setup/maven) ao subir a versão do JDK.

**Correção aplicada**: registrar o Lombok explicitamente como annotation
processor no `maven-compiler-plugin`, em vez de depender da descoberta
automática:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```
Aproveitamos para também fixar `lombok.version=1.18.48` (mais recente que a
gerenciada pelo BOM do Spring Boot 4.1.1, que é `1.18.46`), já que o Lombok
teve diversos ajustes de compatibilidade com o JDK 25 ao longo de 2025/2026.

### Nota de compatibilidade: `@Builder` não inclui campos herdados (→ `@SuperBuilder`)

Ao rodar os testes (`mvn test`), surgiu mais um erro, desta vez sem relação
com JDK/versão nenhuma - um limite conhecido do próprio Lombok: `cannot find
symbol: method id(long) location: class AccountEntity.AccountEntityBuilder`.

**Causa**: todas as entidades deste projeto (`AccountEntity`,
`ProductEntity`, etc.) estendem `BaseAuditableEntity` (que centraliza os
campos `id`/`createdDt`/`modifiedDt` - ver seção "Convenções de modelagem").
O Lombok "comum" (`@Builder`) só inclui, no builder gerado, os campos
declarados na PRÓPRIA classe anotada - campos herdados de uma superclasse
ficam de fora do builder. Isso nunca deu erro na aplicação em si porque o
código de produção nunca precisa definir o `id` manualmente (ele é sempre
gerado pelo `AUTO_INCREMENT` do MySQL), mas um teste unitário que monta um
`AccountEntity` "fake" (simulando um registro já existente) precisava disso.

**Correção**: trocamos `@Builder` por **`@SuperBuilder`** em
`BaseAuditableEntity` e em todas as 11 entidades que a estendem (mesmo nome
de método gerado - `builder()` -, então nenhum código de produção precisou
mudar). `@SuperBuilder` encadeia os builders de toda a hierarquia, mas por
isso precisa estar presente em TODAS as classes da cadeia (nunca misturado
com `@Builder` simples numa mesma hierarquia) - ver comentário completo em
`BaseAuditableEntity.java`.

### Nota de compatibilidade: auto-configuração modular no Spring Boot 4 (Flyway)

No primeiro teste real do serviço (com MySQL de verdade rodando via Docker),
a aplicação subia, conectava no banco normalmente (log do HikariCP OK), mas
o Hibernate falhava logo em seguida com `Schema validation: missing table
[t_account]` - como se as tabelas do Flyway nunca tivessem sido criadas.
Confirmamos direto no MySQL (`SHOW TABLES;` retornando vazio) que era
exatamente isso: **o Flyway nunca chegou a rodar**, e o mais intrigante é
que não aparecia NENHUMA linha de log do Flyway no console - nem sucesso,
nem erro. Silêncio total.

**Causa raiz**: até o Spring Boot 3, bastava colocar `flyway-core` no
classpath que a auto-configuração do Flyway era ativada automaticamente. A
partir do **Spring Boot 4**, o framework passou a ser distribuído em módulos
menores e mais especializados - a auto-configuração de cada integração
(Flyway, Liquibase, e como já vimos antes, o próprio Hibernate/JPA -
repare no pacote `org.springframework.boot.hibernate.autoconfigure` nos
stack traces) foi separada em artefatos próprios. Sem o *starter* correto,
a auto-configuração correspondente simplesmente **não ativa - sem erro, sem
aviso, sem log nenhum**. É um comportamento silencioso por design (não é um
bug), mas pega muita gente desprevenida numa migração.

**Correção**: trocamos a dependência solta `org.flywaydb:flyway-core` por
`org.springframework.boot:spring-boot-starter-flyway` no `pom.xml` (mantendo
o `flyway-mysql`, que continua sendo o módulo específico de suporte ao
dialeto MySQL). Nenhuma mudança de código ou de configuração (`application.yml`)
foi necessária - só a troca da dependência.

**Lição para o estudo**: sempre que uma integração do Spring Boot "some" sem
motivo aparente após atualizar para uma major version, vale checar a lista
oficial de *starters* modulares do Boot 4
(https://docs.spring.io/spring-boot/appendix/auto-configuration-classes/index.html)
antes de assumir que é um bug de configuração no seu próprio projeto.

### Nota de compatibilidade: nomes de tabela em maiúsculas x `SpringPhysicalNamingStrategy`

Depois de corrigir o problema acima (Flyway passou a criar as tabelas
normalmente), a aplicação voltou a falhar com `Schema validation: missing
table [t_account]` - só que agora a tabela existia de verdade no banco
(`SHOW TABLES;` mostrava `T_ACCOUNT`). O detalhe é a **caixa** do nome: o
Hibernate procurava `t_account` (minúsculo).

**Causa raiz**: por padrão, o Spring Boot troca a estratégia "pura" do
Hibernate pela sua própria (`SpringPhysicalNamingStrategy`), que converte
todo nome de tabela/coluna para snake_case minúsculo - **mesmo** quando já
existe um nome explícito em `@Table(name = "T_ACCOUNT")`. Isso nunca deu
problema em bancos case-*insensitive* (Windows, ou MySQL configurado com
`lower_case_table_names=1`), mas a imagem oficial `mysql:8.4` roda em Linux,
onde nomes de tabela são case-*sensitive* por padrão - então `t_account` e
`T_ACCOUNT` passam a ser considerados nomes diferentes.

**Correção**: em `application.yml`, trocamos a estratégia de nomenclatura
física para a padrão do Hibernate "puro":

```yaml
spring:
  jpa:
    hibernate:
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

Essa estratégia respeita exatamente o nome informado na anotação, sem
reformatar capitalização - a escolha certa sempre que o projeto já define
nomes de tabela/coluna explicitamente em maiúsculas, como fizemos em 100%
das entidades deste projeto.

**Lição para o estudo**: `@Table(name = "...")` e `@Column(name = "...")`
não são a "palavra final" sobre o nome físico no Hibernate - por padrão,
ainda passam por uma estratégia de nomenclatura que pode reescrevê-los. Vale
sempre conferir qual `PhysicalNamingStrategy` está ativa quando o nome
esperado não bate com o nome real da tabela/coluna no banco.

### Nota de compatibilidade: limite de tamanho de linha do MySQL (`T_LOG` e `T_LOG_AU`)

Com o Flyway já criando as tabelas corretamente, a migration
`V2__create_application_schema.sql` passou a falhar logo na primeira
tabela (`T_LOG`) com o erro do MySQL:

```
Error Code: 1118. Row size too large. The maximum row size for the used
table type, not counting BLOBs, is 65535. This includes storage overhead,
check the manual. You have to change some columns to TEXT or BLOBs.
```

**Causa raiz**: as colunas `INPUT` e `OUTPUT` guardam o JSON de
entrada/saída da requisição e foram originalmente declaradas como
`VARCHAR(16000)` (o limite de 16000 caracteres vem da regra de negócio de
truncamento do enunciado - ver `billing.log.max-payload-length`). O
problema é que o MySQL/InnoDB limita o tamanho **total** de uma linha,
somando todas as colunas "de tamanho fixo na página" (tudo exceto
`TEXT`/`BLOB`), a **65.535 bytes** - e, como a tabela usa `utf8mb4` (até 4
bytes por caractere, para suportar acentuação e emojis), só a coluna
`INPUT` já reservava até `16000 × 4 = 64.000` bytes; somando `OUTPUT` (mais
64.000 bytes) e as demais colunas, a linha estourava o limite com folga.

**Correção**: seguimos a própria recomendação da mensagem de erro do
MySQL e trocamos `INPUT`/`OUTPUT` de `VARCHAR(16000)` para `TEXT` na
migration. Colunas `TEXT` são armazenadas fora da página principal da
linha (só um ponteiro fica "na linha"), então não contam para o limite de
65.535 bytes - e o tipo `TEXT` comporta até 65.535 *bytes*, o que cobre
com folga os 16000 *caracteres* (até 64.000 bytes em `utf8mb4`) que a
aplicação efetivamente grava. No lado Java (`LogEntity`), acrescentamos
`@Lob` nessas duas colunas, mantendo `length = 16000` (ver "pegadinha
extra" logo abaixo - por que o `length` continuou necessário mesmo com
`@Lob`). O truncamento em si continua 100% no código Java
(`RequestLogService.truncate`), sem nenhuma relação com o tipo da coluna -
o banco só precisa ser capaz de *armazenar* até 16000 caracteres, e quem
decide truncar antes disso é regra de negócio, não limitação de schema.

**Lição para o estudo**: em bancos relacionais tradicionais, colunas de
texto "grandes" (JSON, descrições longas, corpos de mensagem) quase sempre
devem ser `TEXT`/`CLOB` em vez de `VARCHAR` com um número grande - além do
limite de linha do MySQL/InnoDB, `VARCHAR` grande também desperdiça espaço
fixo na página mesmo quando o conteúdo real é pequeno. Em bancos como o
PostgreSQL, essa distinção importa menos (o `TOAST` do Postgres já lida
com valores grandes automaticamente, então `VARCHAR` sem limite e `TEXT`
são praticamente equivalentes) - mais um motivo, entre vários já citados
neste README, para nunca supor que um comportamento é idêntico entre
bancos diferentes.

**Pegadinha extra**: a tabela de auditoria `T_LOG_AU` (criada em
`V3__create_audit_infrastructure.sql`, ver seção
["Auditoria de tabelas"](#auditoria-de-tabelas) mais abaixo) replica a
estrutura de `T_LOG` **inteira**, `INPUT`/`OUTPUT` incluídos - ou seja, tem
exatamente o mesmo problema, só que descoberto uma migration depois (V2
passou a rodar certinho; foi a V3 que voltou a estourar o limite de linha,
agora em `T_LOG_AU`). A correção foi idêntica: `VARCHAR(16000)` → `TEXT`.
Vale como lembrete de que, ao duplicar a estrutura de uma tabela para fins
de auditoria/histórico, qualquer ajuste de tipo feito na tabela original
precisa ser replicado manualmente na tabela `_AU` - não há nenhum mecanismo
automático de sincronização entre as duas neste projeto.

### Nota de compatibilidade: `@Lob` sozinho não é suficiente - o `length` decide TINYTEXT x TEXT x MEDIUMTEXT x LONGTEXT

Depois que a migration passou a criar `T_LOG.INPUT`/`OUTPUT` como `TEXT`
(nota acima), a aplicação subiu até a validação de schema do Hibernate e
falhou com:

```
Schema validation: wrong column type encountered in column [INPUT] in
table [T_LOG]; found [text (Types#LONGVARCHAR)], but expecting [tinytext
(Types#CLOB)]
```

**Causa raiz**: na primeira tentativa de corrigir a entidade, trocamos
`@Column(length = 16000)` por `@Lob` **sem** manter o `length` - o
raciocínio (errado) era que `length` só fazia sentido para `VARCHAR`. Só
que o MySQL não tem um único tipo "CLOB": tem quatro variantes de texto
(`TINYTEXT` até 255 bytes, `TEXT` até 65.535, `MEDIUMTEXT` até 16.777.215,
`LONGTEXT` acima disso), e é o `length` declarado no `@Column` que diz ao
Hibernate/dialeto MySQL **qual delas** validar - não mais "VARCHAR ou
TEXT" (isso quem decide é o `@Lob`), e sim "qual tamanho de TEXT". Sem
`length` explícito, vale o padrão do JPA (`length = 255`), que cai
exatamente na faixa do `TINYTEXT` - daí o Hibernate esperar `tinytext` e
encontrar `text` (criado pela migration) no banco real.

**Correção**: mantivemos `@Lob` (necessário para o tipo virar `TEXT`/CLOB
em vez de `VARCHAR`) **e** `length = 16000` (necessário para cair na faixa
do `TEXT`, e não do `TINYTEXT`) juntos em `INPUT`/`OUTPUT`:

```java
@Lob
@Column(name = "INPUT", nullable = false, length = 16000)
private String input;
```

**Lição para o estudo**: `@Lob` e `length` no JPA/Hibernate resolvem
problemas DIFERENTES e às vezes precisam ser usados **juntos**: `@Lob`
decide a família de tipo (texto grande vs. `VARCHAR` comum); `length`
decide o tamanho dentro dessa família (relevante em bancos, como o MySQL,
que têm múltiplas variantes de tipo texto grande com limites diferentes).
Remover `length` ao adicionar `@Lob` é um erro comum e intuitivo (parece
"redundante"), mas no MySQL faz o Hibernate assumir o menor tipo possível
(`TINYTEXT`, 255 bytes) por padrão.

## Limitações conhecidas

- **Log de erros de JSON malformado**: quando o corpo da requisição não é um
  JSON válido (ou contém um valor de enum inexistente), o Jackson falha
  ANTES de conseguirmos montar o objeto de entrada - nesse caso específico
  não é possível recuperar o `protocol` para gravar em `T_LOG` (a coluna é
  `NOT NULL`). A resposta HTTP 400 ainda é devolvida normalmente.
- Lista de moedas (`T_DOMAIN_CURRENCY`) cobre as ~50 moedas mais usadas em
  transações internacionais, não o ISO-4217 completo (~170 códigos) - fácil
  de estender inserindo novas linhas na migration.
- Não há autenticação/autorização no endpoint (fora do escopo do enunciado).
- Este serviço só implementa o processamento da **compra inicial**; o
  processamento de cobranças recorrentes futuras (usando `NEXT_BILL_DT`/
  `FUTURE_BILL_DT`) é o próximo passo natural do produto (ver seção
  "Evoluções futuras").
- `account.isAuthorizedFallback` é apenas **persistido** nesta versão; a
  lógica que de fato tentaria um método de pagamento alternativo quando o
  principal falha ainda não existe (não há, hoje, um fluxo de "falha de
  cobrança" no serviço) - fica registrada como um próximo passo natural,
  junto do processamento de recorrência.
- O agendamento de cancelamento (`CANCELLATION_REQ_DT`/`CANCELLATION_SCH_DT`)
  também só **grava a data planejada**; o job que efetivamente cancelaria o
  produto na data agendada é outro passo futuro (ver "Próximo serviço
  natural a construir").

## Testes

```bash
mvn test
```

Testes de unidade "puros" (sem subir Spring context nem banco de dados),
focados nos pontos de maior risco/complexidade:

- `RecurrenceCalculatorServiceTest` - reproduz os 3 exemplos numéricos
  literais do enunciado (recorrência mensal, anual e estouro de dia no mês).
- `InstallmentSplitServiceTest` - rateio de centavos entre parcelas.
- `CardExpirationUtilTest` - validação de `MM/YY` (mês inválido, cartão expirado).
- `PurchaseValidationServiceTest` - regras de cálculo mais críticas (soma de
  taxas, fórmula de `chargedValue`), mais os campos obrigatórios acrescentados
  em 17/09/2026 (`account.email`, `account.isAuthorizedFallback`,
  `payment.brand`), com os repositórios simulados via Mockito.
- `ProductServiceTest` (17/09/2026) - a regra mais nova e mais arriscada desta
  evolução: `DISABLE_BILLING_B` (sempre derivada de `type`) e o par
  `CANCELLATION_REQ_DT`/`CANCELLATION_SCH_DT`, condicionado ao *feature
  toggle* `AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT` - cobre as 4 combinações
  relevantes (ONESHOT+vigência com o toggle ligado/desligado, ONESHOT sem
  vigência, e RECURRENCE).
- `FeatureToggleServiceTest` (17/09/2026) - os 3 caminhos de
  `FeatureToggleService.isEnabled`: regra ligada, regra desligada, e regra
  **inexistente** (garante o comportamento *fail-safe* - nunca lança exceção
  por um nome de regra digitado errado).
- `AssetIdFormatterTest` (17/09/2026) - utilitário puro (sem dependências),
  cobre os 4 prefixos (`ACCT_`/`PROD_`/`BILL_`/`PAY_`), as duas sobrecargas
  de `payment` (`Long`/`String`) e o retorno `null` quando o ID técnico é
  `null`.
- `RequestLogServiceTest` (17/09/2026) - a regra de truncamento antes de
  gravar em `T_LOG` (`REASON` em 500 caracteres, `INPUT`/`OUTPUT` no limite
  configurável de `billing.log.max-payload-length`) e o tratamento de valor
  `null` (vira string vazia, nunca `null`, já que as colunas são `NOT NULL`).
- `GlobalExceptionHandlerTest` (17/09/2026) - os dois `@ExceptionHandler`
  (JSON malformado e falha de Bean Validation), incluindo a busca da causa
  raiz mais profunda da exceção, o fallback de mensagem padrão, a decisão de
  logar (ou não) em `T_LOG` dependendo de conseguir recuperar o `protocol`, e
  o *fallback* de serialização quando o `JsonMapper` falha.
- `BooleanCharConverterTest` (17/09/2026) - o `AttributeConverter` usado em
  toda coluna `*_B` do projeto: as duas direções da conversão
  (`Boolean`↔`CHAR(1)`), `null` em ambas as direções, e o comportamento
  "fail-safe" implícito de tratar qualquer valor de coluna diferente de
  `"1"` como `false`.

Não incluímos testes de integração com banco real neste momento (ver
"Evoluções futuras" - Testcontainers) porque as triggers de auditoria são
SQL nativo do MySQL e não rodam em bancos em memória como H2.

### Cobertura de código (JaCoCo)

`mvn test` agora também gera um relatório de cobertura via
[JaCoCo](https://www.jacoco.org/jacoco/) (plugin `jacoco-maven-plugin`,
adicionado em 17/09/2026): abra `target/site/jacoco/index.html` no navegador
depois de rodar os testes para ver o percentual real, por classe e por
linha/branch. Não existe um número de cobertura "oficial" documentado aqui
de propósito - ele muda a cada `mvn test` e este README não é regenerado a
cada rodada; o relatório do JaCoCo é a fonte da verdade.

> **Nota sobre a versão do plugin**: usamos `jacoco-maven-plugin` **0.8.15**
> (não a 0.8.12, versão inicialmente adicionada). O JaCoCo lê o bytecode
> compilado usando sua própria cópia da biblioteca ASM, que precisa
> reconhecer o "major version" do class file de cada JDK (Java 25 = 69). A
> 0.8.12 é anterior ao lançamento do Java 25 e falha com `Unsupported class
> file major version 69` no goal `report` (os testes chegam a passar
> normalmente - só a geração do relatório de cobertura quebra o build). O
> suporte oficial a Java 25 chegou na 0.8.14; a 0.8.15 é a versão estável
> mais recente no momento e já suporta oficialmente até o Java 26. Lição
> para o portfólio: ao fixar a versão de um plugin de build tooling (não só
> de dependências de runtime), vale checar se ele já suporta a versão do
> JDK do projeto - ferramentas de bytecode como JaCoCo, ASM, ByteBuddy
> (usado pelo Mockito) e afins costumam ficar defasadas em relação a
> lançamentos recentes do Java.

**Primeira medição real (17/09/2026)**, feita a partir do `mvn test` local do
autor (JDK 25/Windows), agregando as 47 classes do projeto: **≈41% de
instructions / ≈39% de linhas / ≈38% de branches**. Ver a análise completa
(por que ficou nesse patamar, e quais classes puxam a média para baixo) na
documentação de decisões e arquitetura do projeto.

#### Meta de cobertura: por que não "80% amanhã"

Uma dúvida comum em quem está começando com cobertura de código: **qual é o
número "certo"**? Não existe um padrão universal obrigatório, mas alguns
pontos de referência amplamente citados na indústria:

- O [Google Testing Blog](https://testing.googleblog.com/2020/08/code-coverage-best-practices.html)
  (post oficial "Code Coverage Best Practices", 2020) propõe faixas
  informais: **abaixo de 60% = não aceitável, 60% = aceitável, 75% =
  louvável ("commendable"), 90% = exemplar**. Não é uma régua rígida - é uma
  referência de "onde mais ou menos estar".
- O **SonarQube/SonarCloud** (ferramenta de análise estática muito usada em
  pipelines de CI/CD - ver tabela de frameworks abaixo) tem, por padrão, um
  "Quality Gate" que **exige 80% de cobertura, mas só no código NOVO ou
  ALTERADO** de um pull request - não no projeto inteiro. Essa é
  provavelmente a origem do número "80%" que mais aparece quando se procura
  por "boa prática de cobertura".
- A tendência moderna (Codecov, SonarQube, e a maioria das ferramentas de CI)
  é medir **"diff coverage"/"patch coverage"** (cobertura só do que mudou em
  cada PR) em vez de perseguir um número fixo sobre o código legado inteiro:
  é mais sustentável exigir "todo código NOVO vem com teste" do que tentar
  cobrir retroativamente um projeto inteiro de uma vez.

**Para este projeto**, isso se traduz em duas decisões concretas, já
aplicadas no `pom.xml`:

1. **Exclusões da métrica**: a classe de bootstrap (`BillingApplication`),
   os DTOs de request/response (`records` sem lógica condicional própria) e
   a classe de configuração (`BillingProperties`, populada pelo Spring) só
   seriam exercitados de verdade por um teste de *integração* (subindo
   contexto Spring) - não faz sentido penalizar a métrica de cobertura de
   *unidade* por eles. Excluí-los deixa o percentual mais fiel ao código que
   realmente tem regra de negócio para testar. Isso NÃO muda o resultado dos
   testes - só o que entra na conta do relatório/gate.
2. **Gate incremental** (goal `check` do JaCoCo, também na fase `test`):
   começa em **35%** de linhas (levemente abaixo do que já temos hoje, só
   para o gate existir sem travar o build imediatamente) e a ideia é ir
   subindo esse piso aos poucos (ex.: 35% → 50% → 65% → 80%) conforme novos
   lotes de teste entram - em vez de definir 80% de uma vez e travar todo
   `mvn test` até lá. Essa é a mesma lógica do Quality Gate do SonarQube:
   um número que sobe com o tempo, não uma meta que trava tudo hoje.

**O que TEM teste hoje**: as regras de cálculo mais arriscadas
(`RecurrenceCalculatorService`, `InstallmentSplitService`,
`CardExpirationUtil`), a validação de negócio mais crítica
(`PurchaseValidationService` - parcialmente, focada nas regras condicionais
mais fáceis de errar), as duas regras mais novas de `ProductService`
(`DISABLE_BILLING_B`/cancelamento agendado) + `FeatureToggleService`, e (a
partir desta rodada) `AssetIdFormatter`, `RequestLogService` e
`GlobalExceptionHandler` - escolhidos por serem baratos de testar (poucas
dependências, lógica simples) e ainda assim relevantes (formatação de ID
exposta na API, truncamento antes de gravar em banco, tradução de erro
HTTP).

**Atualização (mesma sessão, após confirmação real do usuário)**: com a
1ª leva de testes acima, a cobertura subiu de ~41%/39% (instructions/lines)
para **~48%/46%** — confirmado via `mvn test` real (45 testes, todos
passando, `jacoco:check` aprovado). Ver a tabela completa e a análise por
classe na documentação de decisões e arquitetura do projeto.

**O que ainda NÃO tem teste dedicado** (candidatos naturais para uma próxima
rodada, agora em ordem de prioridade CONFIRMADA pelos números reais do
JaCoCo, não só por intuição): `AccountService`/`PaymentService`/
`BillingService`/`PurchaseOrchestrationService` (as quatro em 0% de
cobertura hoje - camada de orquestração/persistência, o maior bloco
restante; pelo forte acoplamento a repositórios e transação, provavelmente
vale mais a pena testar com Testcontainers do que mockando tudo); o
restante de `PurchaseValidationService` (validação de documento/
endereço/telefone, regras de desconto, validação de token, validação de
billing cruzada com pagamento - hoje em ~55%); e os utilitários `MoneyUtil`
(~59%) e `PurchaseLookupUtils` (~68%, faltam ramos de erro). Nenhum desses
ficou sem teste por serem menos importantes - é só o corte que coube em
cada rodada; o JaCoCo aponta exatamente as linhas descobertas se quiser
fechar essa lacuna aos poucos.

> **Nota sobre enums e o JaCoCo**: `CardBrand`/`DocumentType`/`AddressType`
> aparecem em 0% mesmo sendo enums simples, sem lógica própria. Isso não é
> uma lacuna de teste real - é uma particularidade conhecida do JaCoCo: ele
> instrumenta os métodos sintéticos `values()`/`valueOf()` que o compilador
> gera automaticamente para TODO enum Java, e sem um teste que chame esses
> métodos especificamente, eles aparecem como "não cobertos". Os enums
> `PaymentMethod`/`ProductType`/`RecurrenceFrequency`/`ResultStatus` já
> aparecem cobertos só porque são construídos indiretamente pelos testes de
> service existentes - não porque alguém testou `values()` de propósito.

### Integração contínua (CI) com GitHub Actions

Além de rodar localmente, os testes (e o gate de cobertura do JaCoCo) rodam
automaticamente no GitHub a cada `push`/pull request para `main`/`master`,
via um workflow em `.github/workflows/ci.yml`.

**Por que faz sentido rodar os testes em pipeline, e não só localmente:**
- Garante que ninguém suba (ou faça merge de) código que quebra um teste ou
  derruba a cobertura abaixo do mínimo combinado - o build falha
  publicamente no GitHub, e não só "na máquina de quem esqueceu de rodar
  `mvn test` antes do commit".
- Roda o mesmo comando, no mesmo ambiente, toda vez - elimina o clássico
  "na minha máquina funciona" (versão de JDK diferente, dependência
  desatualizada no `.m2` local, etc.).
- Num PR, o resultado aparece direto na tela de revisão, antes do merge -
  o ponto mais barato para descobrir um problema.
- É pré-requisito para travar a branch `main` (Settings → Branches →
  Branch protection rules → "Require status checks to pass before
  merging"), impedindo push direto ou merge de PR com o pipeline
  vermelho - configuração feita na interface do GitHub, não neste
  repositório.

**O que o workflow faz:** configura o JDK 25 (Temurin), restaura o cache do
`~/.m2` entre execuções, e roda `mvn verify` (até 18/09/2026 era só
`mvn test` - ver "Qualidade de código automatizada" logo abaixo para o
motivo de ter avançado até `verify`). O relatório HTML/CSV do JaCoCo (e o
XML do SpotBugs) são publicados como artefato do run, então dá para abrir
a cobertura linha a linha - ou ver exatamente qual achado do SpotBugs
disparou - direto pela aba "Actions" do GitHub, sem rodar nada localmente.

### Qualidade de código automatizada (Checkstyle + SpotBugs)

Até aqui, os warnings de qualidade de código (import não usado, bloco sem
chaves, parâmetro sempre com o mesmo valor etc.) só existiam dentro do
IntelliJ, na máquina de quem estava codando - nada no pipeline impedia um
PR com warning de ser mesclado (foi exatamente por isso que duas "levas"
de warnings precisaram ser limpas manualmente ao longo desta sessão, ver
doc de decisões do projeto). Dois plugins novos no `pom.xml` transformam
isso em um GATE automático:

- **[Checkstyle](https://checkstyle.org/)** - análise estática do
  CÓDIGO-FONTE (não precisa compilar nada). Roda na fase `validate` (a
  primeira do Maven), então um problema aqui é descoberto antes até de
  compilar. Ruleset customizado em `checkstyle.xml`, na raiz do projeto -
  **deliberadamente enxuto**: em vez de importar um ruleset "de prateleira"
  (`sun_checks.xml`/`google_checks.xml`, com 100+ regras, muitas de
  formatação rígida como limite de 80/100 colunas ou Javadoc obrigatório em
  todo método público), listamos só checks de alto valor/baixo ruído -
  bugs estruturais reais (import não usado, `equals`/`hashCode`
  desalinhados, bloco sem chaves), não estilo. Ver os comentários dentro de
  `checkstyle.xml` para o racional de cada regra escolhida.
- **[SpotBugs](https://spotbugs.github.io/)** (sucessor do antigo
  FindBugs) - análise estática do BYTECODE compilado, por isso só roda na
  fase `verify` (depois de `compile`/`test`/`package`). Detecta uma
  categoria diferente de problema: padrões de bug REAIS (possível NPE,
  comparação de objetos com `==`, recurso não fechado), não estilo.
  Configurado com `threshold=High` (só os achados de maior confiança) em
  vez do padrão `Medium` - evita travar o pipeline com uma enxurrada de
  achados de baixa prioridade (vários deles falsos positivos comuns em
  código com Lombok/JPA/records, como `EI_EXPOSE_REP`) logo no primeiro
  dia da ferramenta no projeto.

**Por que os limiares começam "frouxos" em vez de já usar o padrão
rígido de cada ferramenta**: mesma filosofia de *ratchet* já usada no piso
de cobertura do JaCoCo (ver seção "Testes" acima, e o comentário no
`pom.xml`) - introduzir uma ferramenta de análise estática num projeto que
já existe com o limiar mais permissivo primeiro, medir o resultado real, e
ir apertando aos poucos, é mais sustentável do que importar a configuração
mais rígida do mercado de cara e travar todo o pipeline até o projeto
inteiro se adequar.

> **Nota de transparência**: como o ambiente onde esta funcionalidade foi
> implementada não tem acesso ao Maven Central (só o usuário consegue
> rodar `mvn verify` de verdade, na própria máquina), o ruleset do
> Checkstyle foi calibrado por inspeção manual do código-fonte (nenhuma
> linha hoje ultrapassa 167 caracteres, por exemplo, daí o limite de 180) -
> mas nem Checkstyle nem SpotBugs puderam ser executados de fato antes de
> entregar. É esperado (e normal, ao introduzir análise estática pela
> primeira vez num projeto) que a primeira rodada real aponte 1-2 ajustes
> pontuais - mesmo padrão de colaboração já usado neste projeto para toda
> mudança de ferramenta de build (JaCoCo, warnings do IntelliJ etc.): você
> roda localmente, compartilha o log se algo pintar, e ajustamos juntos.

**Por que não precisa subir um MySQL no pipeline:** toda a suíte atual é de
testes unitários com Mockito (repositórios e dependências mockados, sem
`@SpringBootTest`/`@DataJpaTest`) - nenhum teste hoje toca um banco de
verdade, então não há necessidade de um serviço de banco no job de CI. Isso
muda no dia em que a camada de orquestração/persistência (`AccountService`
etc., ver seção de cobertura acima) ganhar testes de integração com
Testcontainers: nesse ponto, o job de CI passaria a precisar do Docker (que
já vem pré-instalado nos runners `ubuntu-latest` do GitHub, então a
mudança seria só na suíte de testes, não no workflow).

**Possibilidades para estudos futuros:**

| Aspecto | Usado neste projeto | Alternativas para estudar |
|---|---|---|
| Motor de CI/CD | GitHub Actions | GitLab CI/CD, Jenkins, CircleCI, Azure Pipelines, Bitbucket Pipelines |
| Build reprodutível | `mvn` da imagem do runner | Maven Wrapper (`mvnw`/`mvnw.cmd`) versionado no repo, fixando a versão exata do Maven para CI e para qualquer dev que clone o projeto |
| Visualização de cobertura | Artefato de CI (HTML/CSV do JaCoCo) | Codecov/Coveralls (upload automático do relatório, badge de % no README, comentário automático no PR com o diff de cobertura) |
| Gate de qualidade estática | Checkstyle + SpotBugs (ver seção acima) | **SonarQube/SonarCloud** - mais completo que os dois juntos (duplicação de código, gate de "cobertura só no código NOVO/alterado de um PR" em vez do projeto inteiro, detecção de vulnerabilidades, dashboard visual) e gratuito para repositórios públicos, mas exige criar conta em sonarcloud.io e configurar um token como secret do repositório - próximo passo natural depois de validar Checkstyle/SpotBugs |
| Segurança da pipeline | `permissions` padrão (herdadas), actions fixadas por major version | `permissions: contents: read` explícito no workflow (princípio do menor privilégio), CodeQL (SAST nativo do GitHub, grátis p/ repositório público), Dependabot (atualiza dependências Maven E as próprias GitHub Actions, com alerta de CVE) - tema em alta desde os ataques de supply-chain via actions comprometidas em 2025 |
| Feedback no PR | Log bruto do `mvn verify` | Anotar falhas de teste direto no diff do PR (ex: `dorny/test-reporter`), resumo de cobertura/qualidade direto na tela do Actions (`$GITHUB_STEP_SUMMARY`), `concurrency` (cancela automaticamente um run anterior quando chega um push novo no mesmo PR, economizando minutos de CI) |
| Testes de integração em CI | Nenhum ainda (só unitários) | Testcontainers + serviço de MySQL real dentro do próprio job, ou `services:` do GitHub Actions apontando pra uma imagem `mysql` |
| Empacotamento/deploy | `package` gera o JAR, mas não publica em lugar nenhum | Publicação da imagem Docker (`docker build`/`docker push`) em um registry, como próximo passo rumo a deploy contínuo (CD) |

## Evoluções futuras / outros frameworks para estudo

Conforme pedido no enunciado ("colocar sempre as possibilidades de utilização
de outros frameworks para estudos futuros"):

| Área | Usado neste projeto | Alternativas para estudar |
|---|---|---|
| Framework web | Spring Boot (Spring MVC, bloqueante) | **Spring WebFlux** (reativo/não-bloqueante); **Quarkus** ou **Micronaut** (startup mais rápido, menor consumo de memória, ótimos para serverless/containers) |
| Persistência | Spring Data JPA (Hibernate) | **jOOQ** (SQL type-safe, sem "mágica" de ORM); **MyBatis** (mapeamento manual, mais controle); **Spring Data JDBC** (mais simples que JPA, sem cache de 1º nível) |
| Migração de banco | Flyway | **Liquibase** (changelogs em XML/YAML/JSON, rollback mais flexível) |
| Validação | Bean Validation + serviço próprio | **Vavr** ou um padrão *Railway-Oriented Programming* para acumular múltiplos erros de validação em vez de parar no primeiro |
| Mensageria/eventos | Síncrono (REST) | Publicar um evento (ex: `PurchaseRegisteredEvent`) em **Kafka** ou **RabbitMQ** ao final da compra, permitindo que outros serviços (ex: um futuro serviço de cobrança recorrente) reajam de forma assíncrona e desacoplada |
| Testes de integração | Só testes unitários | **Testcontainers** (sobe um MySQL real em Docker durante os testes, incluindo as triggers) + **Spring Boot Test** (`@SpringBootTest`) |
| Observabilidade | Actuator básico | **Micrometer + Prometheus + Grafana** para métricas; **OpenTelemetry** para tracing distribuído |
| Documentação da API | Nenhuma ainda | **springdoc-openapi** para gerar Swagger UI automaticamente a partir dos DTOs/controllers |
| Idempotência/cache | Consulta direta ao `T_LOG` | **Redis** como cache de idempotência (mais rápido que consultar o banco relacional a cada requisição) |
| Feature flags | Tabela própria (`T_CONFIG_FEATURE_TOGGLE`) + `FeatureToggleService` | **Togglz**, **FF4J**, **Unleash**, **LaunchDarkly** ou **Split** (soluções dedicadas, com painel de administração, *targeting* por usuário/percentual, e SDKs prontos); cache da leitura com Spring `@Cacheable` + **Caffeine** (evita ir ao banco em toda requisição); ou centralizar a configuração em **Spring Cloud Config**/**Consul**, com atualização em runtime via *refresh* |

### Próximo serviço natural a construir

Um *job* agendado (`@Scheduled` do Spring, ou um serviço separado disparado
por um *scheduler* externo como **Quartz** ou um *cron* de Kubernetes) que
busca produtos com `NEXT_BILL_DT` vencido, gera a próxima fatura de
recorrência (`BILL_TYPE = 2`) e recalcula `NEXT_BILL_DT`/`FUTURE_BILL_DT` -
reaproveitando diretamente `RecurrenceCalculatorService`, `BillingService` e
`InstallmentSplitService` já implementados aqui.
