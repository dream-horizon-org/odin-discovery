--liquibase formatted sql

--changeset odin:20231006150657_create_tables
CREATE TABLE IF NOT EXISTS record (
  id                BIGINT                                                          NOT NULL AUTO_INCREMENT,
  name              VARCHAR(100)                                                    NOT NULL,
  type              VARCHAR(100),
  provider_id       BIGINT                                                          NOT NULL,
  ttl_in_seconds               INT,
  weight            INT,
  identifier        VARCHAR(200),
  client_type       VARCHAR(100)                                                    NOT NULL,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (name, provider_id,identifier),
  INDEX (name, provider_id)
);


CREATE TABLE IF NOT EXISTS record_destination (
  id                bigint                                                          NOT NULL AUTO_INCREMENT,
  record_id         bigint                                                          NOT NULL,
  destination       varchar(100)                                                    NOT NULL,
  created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (record_id, destination),
  INDEX (record_id)
);

CREATE TABLE IF NOT EXISTS provider
(
  id              BIGINT                                                          NOT NULL AUTO_INCREMENT,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP                             NOT NULL,
  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
  org_id          BIGINT                                                          NOT NULL,
  account_name    VARCHAR(20)                                                     NOT NULL,
  name            VARCHAR(30)                                                     NOT NULL,
  config          TEXT                                                            NOT NULL,
  config_hash  VARCHAR(255)                                                    NOT NULL,
  is_active       BOOLEAN DEFAULT TRUE                                            NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (org_id, account_name, name,config_hash),
  INDEX (org_id, account_name, name)
);
