# billing-registration-service

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
  taxas, fórmula de `chargedValue`), com os repositórios simulados via Mockito.

Não incluímos testes de integração com banco real neste momento (ver
"Evoluções futuras" - Testcontainers) porque as triggers de auditoria são
SQL nativo do MySQL e não rodam em bancos em memória como H2.

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

### Próximo serviço natural a construir

Um *job* agendado (`@Scheduled` do Spring, ou um serviço separado disparado
por um *scheduler* externo como **Quartz** ou um *cron* de Kubernetes) que
busca produtos com `NEXT_BILL_DT` vencido, gera a próxima fatura de
recorrência (`BILL_TYPE = 2`) e recalcula `NEXT_BILL_DT`/`FUTURE_BILL_DT` -
reaproveitando diretamente `RecurrenceCalculatorService`, `BillingService` e
`InstallmentSplitService` já implementados aqui.
