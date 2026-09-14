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
  -p 3306:3306 -d mysql:8.4
```

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

Ver também `database/README.md` para mais detalhes sobre por que os scripts
vivem ali (em vez de soltos em uma pasta separada).

### Convenções de modelagem (aplicadas em TODAS as tabelas)

- Datas: `BIGINT` (epoch em milissegundos) - conversão feita em `EpochDateUtil`.
- Booleanos: `CHAR(1)` (`"1"`/`"0"`) - conversão feita em `BooleanCharConverter` (JPA `AttributeConverter`).
- Toda tabela de aplicação (não-domínio) tem uma tabela `_AU` gêmea e duas triggers (`BEFORE UPDATE`, `BEFORE DELETE`) que fotografam o registro antes da alteração/exclusão.

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
   cliente (conta, produto, pagamento, fatura) é simplesmente o ID técnico
   (auto-increment) da respectiva tabela, convertido para `String`.
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
