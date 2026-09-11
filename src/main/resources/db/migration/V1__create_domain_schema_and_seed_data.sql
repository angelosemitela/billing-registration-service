-- =============================================================================
-- V1__create_domain_schema_and_seed_data.sql
--
-- Cria o banco de dados e todas as tabelas DE DOMÍNIO (listas de valores fixos
-- usadas para validação e/ou como "lookup" por outras tabelas), já populadas
-- com os dados iniciais pedidos no enunciado (instrução 6).
--
-- Tabelas de domínio (contêm a palavra "DOMAIN" no nome) são a ÚNICA exceção
-- à regra de geração de tabela de auditoria "_AU" (instrução 4 do enunciado):
-- elas não mudam por ação do usuário final, então não fazem sentido auditar.
--
-- Nota de nomenclatura: o enunciado original usa o prefixo "T_" para a
-- maioria das tabelas de domínio, mas usa "TB_DOMAIN_CURRENCY" (com "TB_")
-- para moedas. Padronizamos tudo com o prefixo "T_" por consistência -
-- ver detalhes em README/ANALISE.md, seção "Inconsistências encontradas".
-- =============================================================================

CREATE DATABASE IF NOT EXISTS billing_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE billing_db;

-- -----------------------------------------------------------------------------
-- T_DOMAIN_COUNTRY: países (ISO 3166-1 alpha-2), nome por extenso em PT-BR.
-- Usada para validar o campo "country" de documentos e endereços.
-- -----------------------------------------------------------------------------
CREATE TABLE T_DOMAIN_COUNTRY
(
    CODE    CHAR(2) PRIMARY KEY,
    COUNTRY VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO T_DOMAIN_COUNTRY (CODE, COUNTRY)
VALUES ('AF', 'Afeganistao'),
       ('ZA', 'Africa do Sul'),
       ('AL', 'Albania'),
       ('DE', 'Alemanha'),
       ('AD', 'Andorra'),
       ('AO', 'Angola'),
       ('AG', 'Antigua e Barbuda'),
       ('SA', 'Arabia Saudita'),
       ('DZ', 'Argelia'),
       ('AR', 'Argentina'),
       ('AM', 'Armenia'),
       ('AU', 'Australia'),
       ('AT', 'Austria'),
       ('AZ', 'Azerbaijao'),
       ('BS', 'Bahamas'),
       ('BD', 'Bangladesh'),
       ('BB', 'Barbados'),
       ('BH', 'Bahrein'),
       ('BE', 'Belgica'),
       ('BZ', 'Belize'),
       ('BJ', 'Benin'),
       ('BY', 'Bielorrussia'),
       ('BO', 'Bolivia'),
       ('BA', 'Bosnia e Herzegovina'),
       ('BW', 'Botsuana'),
       ('BR', 'Brasil'),
       ('BN', 'Brunei'),
       ('BG', 'Bulgaria'),
       ('BF', 'Burquina Faso'),
       ('BI', 'Burundi'),
       ('BT', 'Butao'),
       ('CV', 'Cabo Verde'),
       ('CM', 'Camaroes'),
       ('KH', 'Camboja'),
       ('CA', 'Canada'),
       ('QA', 'Catar'),
       ('KZ', 'Cazaquistao'),
       ('TD', 'Chade'),
       ('CL', 'Chile'),
       ('CN', 'China'),
       ('CY', 'Chipre'),
       ('CO', 'Colombia'),
       ('KM', 'Comores'),
       ('CG', 'Congo'),
       ('CD', 'Congo (Republica Democratica do)'),
       ('KP', 'Coreia do Norte'),
       ('KR', 'Coreia do Sul'),
       ('CI', 'Costa do Marfim'),
       ('CR', 'Costa Rica'),
       ('HR', 'Croacia'),
       ('CU', 'Cuba'),
       ('DK', 'Dinamarca'),
       ('DJ', 'Djibuti'),
       ('DM', 'Dominica'),
       ('EG', 'Egito'),
       ('SV', 'El Salvador'),
       ('AE', 'Emirados Arabes Unidos'),
       ('EC', 'Equador'),
       ('ER', 'Eritreia'),
       ('SK', 'Eslovaquia'),
       ('SI', 'Eslovenia'),
       ('ES', 'Espanha'),
       ('US', 'Estados Unidos'),
       ('EE', 'Estonia'),
       ('SZ', 'Essuatini'),
       ('ET', 'Etiopia'),
       ('FJ', 'Fiji'),
       ('PH', 'Filipinas'),
       ('FI', 'Finlandia'),
       ('FR', 'Franca'),
       ('GA', 'Gabao'),
       ('GM', 'Gambia'),
       ('GH', 'Gana'),
       ('GE', 'Georgia'),
       ('GD', 'Granada'),
       ('GR', 'Grecia'),
       ('GT', 'Guatemala'),
       ('GY', 'Guiana'),
       ('GN', 'Guine'),
       ('GW', 'Guine-Bissau'),
       ('GQ', 'Guine Equatorial'),
       ('HT', 'Haiti'),
       ('HN', 'Honduras'),
       ('HU', 'Hungria'),
       ('YE', 'Iemen'),
       ('IN', 'India'),
       ('ID', 'Indonesia'),
       ('IQ', 'Iraque'),
       ('IR', 'Ira'),
       ('IE', 'Irlanda'),
       ('IS', 'Islandia'),
       ('IL', 'Israel'),
       ('IT', 'Italia'),
       ('JM', 'Jamaica'),
       ('JP', 'Japao'),
       ('JO', 'Jordania'),
       ('KI', 'Kiribati'),
       ('KW', 'Kuwait'),
       ('LA', 'Laos'),
       ('LS', 'Lesoto'),
       ('LV', 'Letonia'),
       ('LB', 'Libano'),
       ('LR', 'Liberia'),
       ('LY', 'Libia'),
       ('LI', 'Liechtenstein'),
       ('LT', 'Lituania'),
       ('LU', 'Luxemburgo'),
       ('MK', 'Macedonia do Norte'),
       ('MG', 'Madagascar'),
       ('MY', 'Malasia'),
       ('MW', 'Malaui'),
       ('MV', 'Maldivas'),
       ('ML', 'Mali'),
       ('MT', 'Malta'),
       ('MA', 'Marrocos'),
       ('MH', 'Ilhas Marshall'),
       ('MU', 'Mauricio'),
       ('MR', 'Mauritania'),
       ('MX', 'Mexico'),
       ('MM', 'Mianmar'),
       ('FM', 'Micronesia'),
       ('MZ', 'Mocambique'),
       ('MD', 'Moldavia'),
       ('MC', 'Monaco'),
       ('MN', 'Mongolia'),
       ('ME', 'Montenegro'),
       ('NA', 'Namibia'),
       ('NR', 'Nauru'),
       ('NP', 'Nepal'),
       ('NI', 'Nicaragua'),
       ('NE', 'Niger'),
       ('NG', 'Nigeria'),
       ('NO', 'Noruega'),
       ('NZ', 'Nova Zelandia'),
       ('OM', 'Oma'),
       ('NL', 'Paises Baixos'),
       ('PW', 'Palau'),
       ('PA', 'Panama'),
       ('PG', 'Papua-Nova Guine'),
       ('PK', 'Paquistao'),
       ('PY', 'Paraguai'),
       ('PE', 'Peru'),
       ('PL', 'Polonia'),
       ('PT', 'Portugal'),
       ('KE', 'Quenia'),
       ('KG', 'Quirguistao'),
       ('GB', 'Reino Unido'),
       ('CF', 'Republica Centro-Africana'),
       ('CZ', 'Republica Tcheca'),
       ('DO', 'Republica Dominicana'),
       ('RW', 'Ruanda'),
       ('RO', 'Romenia'),
       ('RU', 'Russia'),
       ('SB', 'Ilhas Salomao'),
       ('WS', 'Samoa'),
       ('LC', 'Santa Lucia'),
       ('KN', 'Sao Cristovao e Nevis'),
       ('SM', 'San Marino'),
       ('ST', 'Sao Tome e Principe'),
       ('VC', 'Sao Vicente e Granadinas'),
       ('SN', 'Senegal'),
       ('SL', 'Serra Leoa'),
       ('RS', 'Servia'),
       ('SC', 'Seicheles'),
       ('SG', 'Singapura'),
       ('SY', 'Siria'),
       ('SO', 'Somalia'),
       ('LK', 'Sri Lanka'),
       ('SD', 'Sudao'),
       ('SS', 'Sudao do Sul'),
       ('SE', 'Suecia'),
       ('CH', 'Suica'),
       ('SR', 'Suriname'),
       ('TH', 'Tailandia'),
       ('TW', 'Taiwan'),
       ('TJ', 'Tajiquistao'),
       ('TZ', 'Tanzania'),
       ('TL', 'Timor-Leste'),
       ('TG', 'Togo'),
       ('TO', 'Tonga'),
       ('TT', 'Trinidad e Tobago'),
       ('TN', 'Tunisia'),
       ('TM', 'Turcomenistao'),
       ('TR', 'Turquia'),
       ('TV', 'Tuvalu'),
       ('UA', 'Ucrania'),
       ('UG', 'Uganda'),
       ('UY', 'Uruguai'),
       ('UZ', 'Uzbequistao'),
       ('VU', 'Vanuatu'),
       ('VA', 'Vaticano'),
       ('VE', 'Venezuela'),
       ('VN', 'Vietna'),
       ('ZM', 'Zambia'),
       ('ZW', 'Zimbabue');

-- -----------------------------------------------------------------------------
-- T_DOMAIN_CURRENCY: moedas (ISO 4217), nome por extenso em PT-BR. Usada para
-- validar o campo "currency" de produtos e faturas. Lista cobre as moedas
-- mais utilizadas em transações internacionais; pode ser expandida conforme
-- a necessidade (lista completa ISO-4217 tem >170 códigos).
-- -----------------------------------------------------------------------------
CREATE TABLE T_DOMAIN_CURRENCY
(
    CODE     CHAR(3) PRIMARY KEY,
    CURRENCY VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO T_DOMAIN_CURRENCY (CODE, CURRENCY)
VALUES ('BRL', 'Real Brasileiro'),
       ('USD', 'Dolar Americano'),
       ('EUR', 'Euro'),
       ('GBP', 'Libra Esterlina'),
       ('JPY', 'Iene Japones'),
       ('CNY', 'Yuan Chines'),
       ('ARS', 'Peso Argentino'),
       ('AUD', 'Dolar Australiano'),
       ('CAD', 'Dolar Canadense'),
       ('CHF', 'Franco Suico'),
       ('CLP', 'Peso Chileno'),
       ('COP', 'Peso Colombiano'),
       ('MXN', 'Peso Mexicano'),
       ('UYU', 'Peso Uruguaio'),
       ('PYG', 'Guarani Paraguaio'),
       ('BOB', 'Boliviano'),
       ('PEN', 'Sol Peruano'),
       ('VES', 'Bolivar Venezuelano'),
       ('INR', 'Rupia Indiana'),
       ('KRW', 'Won Sul-Coreano'),
       ('RUB', 'Rublo Russo'),
       ('ZAR', 'Rand Sul-Africano'),
       ('SEK', 'Coroa Sueca'),
       ('NOK', 'Coroa Norueguesa'),
       ('DKK', 'Coroa Dinamarquesa'),
       ('PLN', 'Zloti Polones'),
       ('CZK', 'Coroa Checa'),
       ('HUF', 'Florim Hungaro'),
       ('TRY', 'Lira Turca'),
       ('ILS', 'Novo Shekel Israelense'),
       ('AED', 'Dirham dos Emirados Arabes Unidos'),
       ('SAR', 'Rial Saudita'),
       ('SGD', 'Dolar de Singapura'),
       ('HKD', 'Dolar de Hong Kong'),
       ('NZD', 'Dolar Neozelandes'),
       ('THB', 'Baht Tailandes'),
       ('IDR', 'Rupia Indonesia'),
       ('MYR', 'Ringgit Malaio'),
       ('PHP', 'Peso Filipino'),
       ('VND', 'Dong Vietnamita'),
       ('EGP', 'Libra Egipcia'),
       ('NGN', 'Naira Nigeriana'),
       ('KES', 'Xelim Queniano'),
       ('PKR', 'Rupia Paquistanesa'),
       ('BDT', 'Taka de Bangladesh'),
       ('UAH', 'Hryvnia Ucraniana'),
       ('RON', 'Leu Romeno'),
       ('BGN', 'Lev Bulgaro'),
       ('ISK', 'Coroa Islandesa'),
       ('HRK', 'Kuna Croata'),
       ('GHS', 'Cedi Ganes');

-- -----------------------------------------------------------------------------
-- Tabelas de status (STATUS_ID/ID + STATUS_DESC), cada uma com os valores
-- exatos definidos no enunciado.
-- -----------------------------------------------------------------------------
CREATE TABLE T_DOMAIN_ACCOUNT_STATUS
(
    STATUS_ID   INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_ACCOUNT_STATUS (STATUS_ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Cancelado');

CREATE TABLE T_DOMAIN_ACCOUNT_DOCUMENT_STATUS
(
    STATUS_ID   INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_ACCOUNT_DOCUMENT_STATUS (STATUS_ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Cancelado');

CREATE TABLE T_DOMAIN_ACCOUNT_ADDRESS_STATUS
(
    STATUS_ID   INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_ACCOUNT_ADDRESS_STATUS (STATUS_ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Cancelado');

CREATE TABLE T_DOMAIN_ACCOUNT_PHONE_STATUS
(
    STATUS_ID   INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_ACCOUNT_PHONE_STATUS (STATUS_ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Cancelado');

CREATE TABLE T_DOMAIN_PRODUCT_STATUS
(
    STATUS_ID   INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_PRODUCT_STATUS (STATUS_ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Suspenso'),
       (3, 'Cancelado'),
       (4, 'Venda sem servico');

CREATE TABLE T_DOMAIN_DISCOUNT_STATUS
(
    STATUS_ID   INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_DISCOUNT_STATUS (STATUS_ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Expirado'),
       (3, 'Cancelado');

CREATE TABLE T_DOMAIN_PAYMENT_STATUS
(
    ID          INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_PAYMENT_STATUS (ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Expirado'),
       (3, 'Cancelado');

CREATE TABLE T_DOMAIN_PAYMENT_TOKEN_STATUS
(
    ID          INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_PAYMENT_TOKEN_STATUS (ID, STATUS_DESC)
VALUES (1, 'Ativo'),
       (2, 'Expirado'),
       (3, 'Cancelado');

CREATE TABLE T_DOMAIN_BILL_STATUS
(
    ID          INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_BILL_STATUS (ID, STATUS_DESC)
VALUES (1, 'Emitida'),
       (2, 'Enviada para cobranca'),
       (3, 'Nao Cobrada'),
       (4, 'Paga aguardando repasse'),
       (5, 'Em liquidacao de parcelas'),
       (6, 'Paga'),
       (7, 'Cancelada');

CREATE TABLE T_DOMAIN_BILL_TYPE
(
    ID          INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_BILL_TYPE (ID, STATUS_DESC)
VALUES (1, 'Compra'),
       (2, 'Recorrencia'),
       (3, 'Reativacao'),
       (4, 'Retentativa');

CREATE TABLE T_DOMAIN_BILL_INSTALLMENT_STATUS
(
    ID          INT PRIMARY KEY,
    STATUS_DESC VARCHAR(100) NOT NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
INSERT INTO T_DOMAIN_BILL_INSTALLMENT_STATUS (ID, STATUS_DESC)
VALUES (1, 'Paga aguardando repasse'),
       (2, 'Paga');
