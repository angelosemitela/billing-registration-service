-- =============================================================================
-- V2__create_application_schema.sql
--
-- Cria todas as tabelas "de aplicação" (não-domínio) do projeto de registro
-- de compras/faturamento. Executa DEPOIS de V1 (tabelas de domínio) porque
-- várias colunas aqui têm chave estrangeira para tabelas de domínio
-- (T_DOMAIN_*), que precisam existir primeiro.
--
-- Convenções adotadas em todo o script (documentadas também no README):
--   * Todas as datas são armazenadas como BIGINT (epoch em milissegundos),
--     conforme instrução 1 do enunciado. A conversão para/de java.time é
--     feita na camada Java (ver com.aalvarenga.billing.util.EpochDateUtil).
--   * Todo booleano é armazenado como CHAR(1) com '1'=true e '0'=false,
--     conforme instrução 2 do enunciado.
--   * Todas as tabelas usam InnoDB (necessário para chaves estrangeiras e
--     transações) e charset utf8mb4 (suporta acentuação e emojis).
--   * Toda tabela "elegível" (não-domínio) tem ID numérico autoincremento
--     como chave primária técnica - o valor desse ID é o que devolvemos
--     como "id" nos JSONs de saída (ver decisão registrada no README/ANALISE
--     sobre a regra de geração de identificadores).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- T_LOG: registra cada requisição recebida pelo serviço (entrada, saída e
-- resultado do processamento). É a base para consulta de idempotência via
-- PROTOCOL (ver com.aalvarenga.billing.service.RequestLogService).
-- -----------------------------------------------------------------------------
CREATE TABLE T_LOG
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT  BIGINT       NOT NULL,
    MODIFIED_DT BIGINT       NOT NULL,
    PROTOCOL    VARCHAR(100) NOT NULL,
    RESULT      VARCHAR(20)  NOT NULL,
    CODE        VARCHAR(10)  NOT NULL,
    REASON      VARCHAR(500) NOT NULL,
    -- INPUT/OUTPUT guardam o JSON textual de entrada/saída. VARCHAR (e não
    -- TEXT/LONGTEXT) foi usado deliberadamente, pois o enunciado pede para
    -- truncar ("cut") o conteúdo quando ultrapassar o tamanho da coluna -
    -- esse limite (16000) precisa ficar sincronizado com
    -- billing.log.max-payload-length em application.yml.
    INPUT       VARCHAR(16000) NOT NULL,
    OUTPUT      VARCHAR(16000) NOT NULL,
    INDEX IDX_LOG_PROTOCOL (PROTOCOL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_ACCOUNT: dados cadastrais do assinante/cliente
-- -----------------------------------------------------------------------------
CREATE TABLE T_ACCOUNT
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT  BIGINT       NOT NULL,
    MODIFIED_DT BIGINT       NOT NULL,
    NAME        VARCHAR(200) NOT NULL,
    EXTERNAL_ID VARCHAR(100) NOT NULL,
    STATUS      INT          NOT NULL,
    CONSTRAINT UQ_ACCOUNT_EXTERNAL_ID UNIQUE (EXTERNAL_ID),
    CONSTRAINT FK_ACCOUNT_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_ACCOUNT_STATUS (STATUS_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_ACCOUNT_DOCUMENT: documentos do assinante (CPF, passaporte, etc).
-- UNIQUE(ACCOUNT_ID, TYPE) suporta a regra de "upsert por tipo de documento".
-- -----------------------------------------------------------------------------
CREATE TABLE T_ACCOUNT_DOCUMENT
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT  BIGINT      NOT NULL,
    MODIFIED_DT BIGINT      NOT NULL,
    ACCOUNT_ID  BIGINT      NOT NULL,
    TYPE        VARCHAR(20) NOT NULL,
    DESCRIPTION VARCHAR(200) NULL,
    VALUE       VARCHAR(100) NOT NULL,
    COUNTRY     CHAR(2)     NOT NULL,
    STATUS      INT         NOT NULL,
    CONSTRAINT UQ_ACC_DOCUMENT_TYPE UNIQUE (ACCOUNT_ID, TYPE),
    CONSTRAINT FK_ACC_DOCUMENT_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES T_ACCOUNT (ID),
    CONSTRAINT FK_ACC_DOCUMENT_COUNTRY FOREIGN KEY (COUNTRY) REFERENCES T_DOMAIN_COUNTRY (CODE),
    CONSTRAINT FK_ACC_DOCUMENT_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_ACCOUNT_DOCUMENT_STATUS (STATUS_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_ACCOUNT_ADDRESS: endereços do assinante (sempre inserido, nunca atualizado
-- - histórico completo de endereços informados)
-- -----------------------------------------------------------------------------
CREATE TABLE T_ACCOUNT_ADDRESS
(
    ID           BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT   BIGINT      NOT NULL,
    MODIFIED_DT  BIGINT      NOT NULL,
    ACCOUNT_ID   BIGINT      NOT NULL,
    TYPE         VARCHAR(20) NOT NULL,
    DESCRIPTION  VARCHAR(200) NOT NULL,
    ADDRESS_NAME VARCHAR(200) NOT NULL,
    NUMBER       VARCHAR(20) NOT NULL,
    COMPLEMENT   VARCHAR(200) NULL,
    ZIP_CODE     VARCHAR(20) NOT NULL,
    COUNTRY      CHAR(2)     NOT NULL,
    STATUS       INT         NOT NULL,
    CONSTRAINT FK_ACC_ADDRESS_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES T_ACCOUNT (ID),
    CONSTRAINT FK_ACC_ADDRESS_COUNTRY FOREIGN KEY (COUNTRY) REFERENCES T_DOMAIN_COUNTRY (CODE),
    CONSTRAINT FK_ACC_ADDRESS_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_ACCOUNT_ADDRESS_STATUS (STATUS_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_ACCOUNT_PHONE: telefones do assinante (sempre inserido, nunca atualizado)
-- -----------------------------------------------------------------------------
CREATE TABLE T_ACCOUNT_PHONE
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT  BIGINT      NOT NULL,
    MODIFIED_DT BIGINT      NOT NULL,
    ACCOUNT_ID  BIGINT      NOT NULL,
    NUMBER      VARCHAR(20) NOT NULL,
    STATUS      INT         NOT NULL,
    CONSTRAINT FK_ACC_PHONE_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES T_ACCOUNT (ID),
    CONSTRAINT FK_ACC_PHONE_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_ACCOUNT_PHONE_STATUS (STATUS_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_PRODUCT: cada produto vendido dentro de uma requisição vira 1 registro
-- aqui (é a "assinatura"/venda em si, controlando ciclo de vigência e
-- próxima recorrência).
-- -----------------------------------------------------------------------------
CREATE TABLE T_PRODUCT
(
    ID                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT            BIGINT       NOT NULL,
    MODIFIED_DT           BIGINT       NOT NULL,
    -- ATENÇÃO (ver README "Inconsistências"): o enunciado descreve esta coluna
    -- como vinda de "product"-"id" da entrada, mas o payload de exemplo só
    -- possui "product"-"codeId". Assumimos PRODUCT_ID = codeId informado.
    PRODUCT_ID            VARCHAR(100) NOT NULL,
    NAME                  VARCHAR(200) NOT NULL,
    TYPE                  VARCHAR(20)  NOT NULL,
    EXP_SERV_B            CHAR(1)      NOT NULL,
    RECURRENCE_FREQUENCY  VARCHAR(20) NULL,
    VALUE                 DECIMAL(14, 2) NOT NULL,
    CURRENCY              CHAR(3)      NOT NULL,
    TRIAL_B               CHAR(1)      NOT NULL,
    TRIAL_DAYS            INT NULL,
    -- ASSET_ID replica o ID técnico (ver decisão sobre geração de IDs no
    -- README). Mantido como coluna própria (String) para permitir, no
    -- futuro, uma regra de formação diferente sem alterar a PK da tabela.
    ASSET_ID              VARCHAR(100) NULL,
    NEXT_BILL_DT          BIGINT NULL,
    FUTURE_BILL_DT        BIGINT NULL,
    CYCLE_START_DT        BIGINT       NOT NULL,
    CYCLE_END_DT          BIGINT NULL,
    DEFAULT_PAYMENT_ID    VARCHAR(100) NULL,
    CHANNEL               VARCHAR(50)  NOT NULL,
    TRANSACTION_DT        BIGINT       NOT NULL,
    STATUS                INT          NOT NULL,
    CONSTRAINT FK_PRODUCT_CURRENCY FOREIGN KEY (CURRENCY) REFERENCES T_DOMAIN_CURRENCY (CODE),
    CONSTRAINT FK_PRODUCT_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_PRODUCT_STATUS (STATUS_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_DISCOUNT: descontos aplicados a um produto vendido (só é populada quando
-- discountValue > 0)
-- -----------------------------------------------------------------------------
CREATE TABLE T_DISCOUNT
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT  BIGINT NOT NULL,
    MODIFIED_DT BIGINT NOT NULL,
    PRODUCT_ID  BIGINT NOT NULL,
    START_DT    BIGINT NOT NULL,
    END_DT      BIGINT NOT NULL,
    VALUE       DECIMAL(14, 2) NOT NULL,
    STATUS      INT    NOT NULL,
    CONSTRAINT FK_DISCOUNT_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES T_PRODUCT (ID),
    CONSTRAINT FK_DISCOUNT_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_DISCOUNT_STATUS (STATUS_ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_PAYMENT: métodos de pagamento informados na compra.
-- ATENÇÃO (ver README "Inconsistências"): o enunciado original NÃO define
-- uma coluna de vínculo com T_ACCOUNT. Adicionamos ACCOUNT_ID (NOT NULL) por
-- ser indispensável para localizar os meios de pagamento de um assinante em
-- cobranças futuras de recorrência.
-- -----------------------------------------------------------------------------
CREATE TABLE T_PAYMENT
(
    ID           BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT   BIGINT      NOT NULL,
    MODIFIED_DT  BIGINT      NOT NULL,
    ACCOUNT_ID   BIGINT      NOT NULL,
    METHOD       VARCHAR(20) NOT NULL,
    ISSUER       VARCHAR(100) NULL,
    CARD_NUMBER  VARCHAR(50) NULL,
    EXPIRATION   VARCHAR(5) NULL,
    MULTIPLE_B   CHAR(1) NULL,
    DEFAULT_B    CHAR(1)     NOT NULL,
    INSTALLMENTS VARCHAR(5)  NOT NULL,
    STATUS       INT         NOT NULL,
    CONSTRAINT FK_PAYMENT_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES T_ACCOUNT (ID),
    CONSTRAINT FK_PAYMENT_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_PAYMENT_STATUS (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_PAYMENT_TOKEN: tokens de tokenização associados a um método de pagamento.
-- ATENÇÃO (ver README "Inconsistências"): adicionamos PAYMENT_ID (FK), pois
-- sem ela seria impossível saber a qual T_PAYMENT o token pertence.
-- -----------------------------------------------------------------------------
CREATE TABLE T_PAYMENT_TOKEN
(
    ID             BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT     BIGINT       NOT NULL,
    MODIFIED_DT    BIGINT       NOT NULL,
    PAYMENT_ID     BIGINT       NOT NULL,
    NAME           VARCHAR(100) NOT NULL,
    TOKEN          VARCHAR(200) NOT NULL,
    GATEWAY        VARCHAR(100) NOT NULL,
    EXPIRATION_DT  BIGINT NULL,
    STATUS         INT          NOT NULL,
    CONSTRAINT UQ_PAYMENT_TOKEN UNIQUE (TOKEN),
    CONSTRAINT FK_PAYMENT_TOKEN_PAYMENT FOREIGN KEY (PAYMENT_ID) REFERENCES T_PAYMENT (ID),
    CONSTRAINT FK_PAYMENT_TOKEN_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_PAYMENT_TOKEN_STATUS (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_BILL: cada fatura gerada para um produto vendido (só é criada quando
-- chargedValue > 0, conforme regra de negócio do enunciado).
-- ATENÇÃO (ver README "Inconsistências"): adicionamos PAYMENT_ID (FK,
-- nullable), permitindo rastrear qual T_PAYMENT (com cartão/token
-- específico) financiou esta fatura - o enunciado só guardava o método como
-- string solta (PAYMENT_METHOD), o que é insuficiente para auditoria.
-- -----------------------------------------------------------------------------
CREATE TABLE T_BILL
(
    ID              BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT      BIGINT       NOT NULL,
    MODIFIED_DT     BIGINT       NOT NULL,
    CODE_ID         VARCHAR(100) NOT NULL,
    PRODUCT_ID      BIGINT       NOT NULL,
    PAYMENT_ID      BIGINT NULL,
    PRODUCT_VALUE   DECIMAL(14, 2) NOT NULL,
    DISCOUNT_VALUE  DECIMAL(14, 2) NOT NULL,
    TAX_VALUE       DECIMAL(14, 2) NOT NULL,
    CHARGED_VALUE   DECIMAL(14, 2) NOT NULL,
    CURRENCY        CHAR(3)      NOT NULL,
    TRANSACTION_ID  VARCHAR(100) NOT NULL,
    INSTALLMENTS    VARCHAR(5)   NOT NULL,
    PROVIDER        VARCHAR(100) NOT NULL,
    PAYMENT_METHOD  VARCHAR(20)  NOT NULL,
    STATUS          INT          NOT NULL,
    BILL_TYPE       INT          NOT NULL,
    CONSTRAINT UQ_BILL_TRANSACTION_ID UNIQUE (TRANSACTION_ID),
    CONSTRAINT FK_BILL_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES T_PRODUCT (ID),
    CONSTRAINT FK_BILL_PAYMENT FOREIGN KEY (PAYMENT_ID) REFERENCES T_PAYMENT (ID),
    CONSTRAINT FK_BILL_CURRENCY FOREIGN KEY (CURRENCY) REFERENCES T_DOMAIN_CURRENCY (CODE),
    CONSTRAINT FK_BILL_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_BILL_STATUS (ID),
    CONSTRAINT FK_BILL_TYPE FOREIGN KEY (BILL_TYPE) REFERENCES T_DOMAIN_BILL_TYPE (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_BILL_TAX: detalhamento das taxas (impostos) que compõem uma fatura
-- -----------------------------------------------------------------------------
CREATE TABLE T_BILL_TAX
(
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT  BIGINT      NOT NULL,
    MODIFIED_DT BIGINT      NOT NULL,
    BILL_ID     BIGINT      NOT NULL,
    NAME        VARCHAR(50) NOT NULL,
    VALUE       DECIMAL(14, 2) NOT NULL,
    CONSTRAINT FK_BILL_TAX_BILL FOREIGN KEY (BILL_ID) REFERENCES T_BILL (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- -----------------------------------------------------------------------------
-- T_BILL_INSTALLMENT: detalhamento do parcelamento de uma fatura (só populada
-- quando installments > 1)
-- -----------------------------------------------------------------------------
CREATE TABLE T_BILL_INSTALLMENT
(
    ID                BIGINT AUTO_INCREMENT PRIMARY KEY,
    CREATED_DT        BIGINT NOT NULL,
    MODIFIED_DT       BIGINT NOT NULL,
    BILL_ID           BIGINT NOT NULL,
    INSTALLMENT       INT    NOT NULL,
    TOTAL_INSTALLMENT INT    NOT NULL,
    PRODUCT_VALUE     DECIMAL(14, 2) NOT NULL,
    DISCOUNT_VALUE    DECIMAL(14, 2) NOT NULL,
    TAX_VALUE         DECIMAL(14, 2) NOT NULL,
    STATUS            INT    NOT NULL,
    CONSTRAINT FK_BILL_INSTALLMENT_BILL FOREIGN KEY (BILL_ID) REFERENCES T_BILL (ID),
    CONSTRAINT FK_BILL_INSTALLMENT_STATUS FOREIGN KEY (STATUS) REFERENCES T_DOMAIN_BILL_INSTALLMENT_STATUS (ID)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
