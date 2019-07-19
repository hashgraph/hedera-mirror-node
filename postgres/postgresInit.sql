\set db_name hederamirror
\set db_user hederamirror
\set db_password mysecretpassword

-- CREATE DATABASE :'db_name'
--     WITH
--     OWNER = postgres
--     TABLESPACE = pg_default
--     CONNECTION LIMIT = -1;
--
-- CREATE USER :db_user WITH
-- 	LOGIN
-- 	SUPERUSER
-- 	NOCREATEDB
-- 	NOCREATEROLE
-- 	NOINHERIT
-- 	NOREPLICATION
-- 	CONNECTION LIMIT -1
-- 	PASSWORD :'db_password';

\echo Creating sequences

CREATE SEQUENCE s_transaction_types_seq;
CREATE SEQUENCE s_event_files_seq;
CREATE SEQUENCE s_transactions_seq;
CREATE SEQUENCE s_entities_seq;

\echo Creating table t_transfer_types

CREATE TABLE t_transfer_types (
	id			  INTEGER PRIMARY KEY
	,amount       BIGINT NOT NULL
	,description  VARCHAR(10)
);

\echo Inserting into t_transfer_types

INSERT INTO t_transfer_types (id, amount, description) values (1, -1, 'OTHER');
INSERT INTO t_transfer_types (id, amount, description) values (2, 500000000, 'REWARD');
INSERT INTO t_transfer_types (id, amount, description) values (3, 4666667, 'DTS');
INSERT INTO t_transfer_types (id, amount, description) values (4, 100000, 'TXFEE');

\echo Creating table t_transaction_types

CREATE TABLE t_transfer_types (
	id			  INTEGER PRIMARY KEY
	,amount       BIGINT NOT NULL
	,description  VARCHAR(10)
);
CREATE TABLE t_transaction_types (
	id			  INTEGER PRIMARY KEY NOT NULL DEFAULT nextval('s_transaction_types_seq')
	,name  VARCHAR(20)
);

\echo Inserting into t_transaction_types

INSERT INTO t_transaction_types (name) values ('unknown');
INSERT INTO t_transaction_types (name) values ('contractcall');
INSERT INTO t_transaction_types (name) values ('contractcreate');
INSERT INTO t_transaction_types (name) values ('contractupdate');
INSERT INTO t_transaction_types (name) values ('contractdelete');
INSERT INTO t_transaction_types (name) values ('cryptotransfer');
INSERT INTO t_transaction_types (name) values ('cryptoaddclaim');
INSERT INTO t_transaction_types (name) values ('cryptocreate');
INSERT INTO t_transaction_types (name) values ('cryptodelete');
INSERT INTO t_transaction_types (name) values ('cryptodeleteclaim');
INSERT INTO t_transaction_types (name) values ('cryptoupdate');
INSERT INTO t_transaction_types (name) values ('fileappend');
INSERT INTO t_transaction_types (name) values ('filecreate');
INSERT INTO t_transaction_types (name) values ('filedelete');
INSERT INTO t_transaction_types (name) values ('fileupdate');
INSERT INTO t_transaction_types (name) values ('systemdelete');
INSERT INTO t_transaction_types (name) values ('systemundelete');

\echo Creating table t_event_files

CREATE TABLE t_event_files (
  id                   BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_event_files_seq')
  ,name                VARCHAR(250) NOT NULL
  ,last_load           TIMESTAMP NOT NULL
  ,status              VARCHAR(10)
);

\echo Creating table t_entities

CREATE TABLE t_entities (
  id                   BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_entities_seq')
  ,entity_num         BIGINT NOT NULL
  ,entity_realm       BIGINT NOT NULL DEFAULT 0
  ,entity_shard       BIGINT NOT NULL DEFAULT 0
);

\echo Creating table t_account_balances

CREATE TABLE t_account_balances (
  account_num          BIGINT NOT NULL
  ,account_realm       BIGINT NOT NULL
  ,account_shard       BIGINT NOT NULL
  ,balance             BIGINT NOT NULL
);

\echo Creating table t_account_balance_history

CREATE TABLE t_account_balance_history (
  snapshot_time        TIMESTAMP NULL
  ,account_num         BIGINT NOT NULL
  ,account_realm       BIGINT NOT NULL
  ,account_shard       BIGINT NOT NULL
  ,balance             BIGINT NOT NULL
);

\echo Creating table t_transactions

CREATE TABLE t_transactions (
  id                   BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_transactions_seq')
  ,node_account_id     BIGINT NOT NULL
  ,tx_time_sec         TIMESTAMP NULL
  ,tx_time_hour        TIMESTAMP NULL
  ,memo		           VARCHAR(100)
  ,seconds             BIGINT NOT NULL
  ,nanos               INT
  ,xfer_count          INT NOT NULL
  ,trans_type_id       INT
  ,processed 	       BOOLEAN DEFAULT false
  ,result              VARCHAR(45) DEFAULT 'UNKNOWN'
  ,consensus_seconds   BIGINT NULL
  ,consensus_nanos     BIGINT NULL
  ,trans_account_id    BIGINT NOT NULL
  ,transaction_fee     BIGINT
  ,initial_balance     BIGINT DEFAULT 0
  ,crud_entity_id      BIGINT
  ,consensus_time      TIMESTAMP NOT NULL
);

\echo Creating table t_cryptotransfers

CREATE TABLE t_cryptotransfers (
  tx_id                BIGINT NOT NULL
  ,from_account_id     BIGINT NOT NULL
  ,to_account_id       BIGINT NOT NULL
  ,amount              BIGINT NOT NULL
  ,payment_type_id     INT NOT NULL
);

\echo Creating table t_cryptotransferlists

CREATE TABLE t_cryptotransferlists (
  tx_id                BIGINT NOT NULL
  ,account_id          BIGINT NOT NULL
  ,amount              BIGINT NOT NULL
  ,payment_type_id     INT NOT NULL
);

-- CONSTRAINTS
-- t_transactions
\echo Creating constraints on t_transactions

ALTER TABLE t_transactions ADD CONSTRAINT fk_trans_type_id FOREIGN KEY (trans_type_id) REFERENCES t_transaction_types (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_node_account_id FOREIGN KEY (node_account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_trans_account_id FOREIGN KEY (trans_account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_crud_entity_id FOREIGN KEY (crud_entity_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_cryptotransfers
\echo Creating constraints on t_cryptotransfers

ALTER TABLE t_cryptotransfers ADD CONSTRAINT fk_ct_tx_id FOREIGN KEY (tx_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_cryptotransfers ADD CONSTRAINT fk_ct_from_account_id FOREIGN KEY (from_account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_cryptotransfers ADD CONSTRAINT fk_ct_to_account_id FOREIGN KEY (to_account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_cryptotransferlists
\echo Creating constraints on t_cryptotransferlists

ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_ctl_tx_id FOREIGN KEY (tx_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_ctl_account_id FOREIGN KEY (account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- INDICES
-- t_transactions
\echo Creating indices on t_transactions

CREATE INDEX idx_t_transactions_id ON t_transactions (id);
CREATE INDEX idx_t_transactions_seconds ON t_transactions (seconds);
CREATE INDEX idx_t_transactions_nanos ON t_transactions (nanos);
CREATE INDEX idx_t_transactions_processed ON t_transactions (processed);
CREATE INDEX idx_t_transactions_tx_time_hour ON t_transactions (tx_time_hour);
CREATE INDEX idx_t_transactions_acc_id ON t_transactions (trans_account_id);
CREATE INDEX idx_t_transactions_cons_seconds ON t_transactions (consensus_seconds);
CREATE INDEX idx_t_transactions_cons_nanos ON t_transactions (consensus_nanos);
CREATE UNIQUE INDEX idx_t_transactions_transaction_id_unq ON t_transactions (seconds, nanos, trans_account_id);
CREATE INDEX idx_t_transactions_trans_account ON t_transactions (trans_account_id);
CREATE INDEX idx_t_transactions_node_account ON t_transactions (node_account_id);
CREATE INDEX idx_t_transactions_crud_entity ON t_transactions (crud_entity_id);

-- t_cryptotransfers
\echo Creating indices on t_cryptotransfers

CREATE INDEX idx_cryptotransfers_tx_id ON t_cryptotransfers (tx_id);
CREATE INDEX idx_cryptotransfers_from_account ON t_cryptotransfers (from_account_id);
CREATE INDEX idx_cryptotransfers_to_account ON t_cryptotransfers (to_account_id);
CREATE INDEX idx_t_cryptotransfers_amount ON t_cryptotransfers (amount);
CREATE INDEX idx_t_cryptotransfers_payment_type_id ON t_cryptotransfers (payment_type_id);
ALTER TABLE t_cryptotransfers ADD CONSTRAINT fk_ct_from_acc_id FOREIGN KEY (from_account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE t_cryptotransfers ADD CONSTRAINT fk_ct_to_acc_id FOREIGN KEY (to_account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_cryptotransferlists
\echo Creating indices on t_cryptotransferlists

CREATE INDEX idx_cryptotransferslist_tx_id ON t_cryptotransfers (tx_id);
CREATE INDEX idx_cryptotransferlist_account ON t_cryptotransferlists (account_id);
CREATE INDEX idx_t_cryptotransferlist_amount ON t_cryptotransferlists (amount);
CREATE INDEX idx_t_cryptotransferlist_tx_id_amount ON t_cryptotransferlists (tx_id, amount);
CREATE INDEX idx_t_cryptotransferlist_tx_id_account ON t_cryptotransferlists (tx_id, account_id);
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_ctl_acc_id FOREIGN KEY (account_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

-- t_event_files
\echo Creating indices on t_event_files

CREATE UNIQUE INDEX idx_t_event_files_name ON t_event_files (name);

--t_account_balances
\echo Creating indices on t_account_balances

CREATE UNIQUE INDEX idx_t_account_bal_unq ON t_account_balances (account_num, account_realm, account_shard);

--t_account_balance_history
\echo Creating indices on t_account_balance_history

CREATE UNIQUE INDEX idx_t_account_bal_hist_unq ON t_account_balance_history (snapshot_time, account_num, account_realm, account_shard);

--t_entities
\echo Creating indices on t_entities

CREATE UNIQUE INDEX idx_t_entities_unq ON t_entities (entity_num, entity_realm, entity_shard);

-- VIEWS
\echo Creating view v_event_files

CREATE VIEW v_event_files AS
SELECT id, name, last_load, status
FROM t_event_files;

\echo Creating view v_transactions

CREATE OR REPLACE VIEW v_transactions AS
SELECT t.id,0 as file_id,e.entity_num as node_account,t.tx_time_sec,t.memo,t.seconds,t.nanos,t.xfer_count,0 AS oos
FROM t_transactions t
    ,t_entities e
WHERE t.node_account_id = e.id;

\echo Creating view v_cryptotransferlist

CREATE OR REPLACE VIEW v_cryptotransferlist AS
SELECT 0 AS file_id, e.entity_num as node_account, e1.entity_num as account, tf.amount, t.tx_time_sec, t.seconds, t.nanos, t.xfer_count as txCount, 0 AS oos, tt.description as tx_type
FROM t_transactions t
    ,t_cryptotransferlists tf
    ,t_transfer_types tt
    ,t_entities e
    ,t_entities e1
WHERE t.id = tf.tx_id
AND tf.payment_type_id = tt.id
AND e.id = t.node_account_id
AND e1.id = tf.account_id;

\echo Creating view v_cryptotransfers

CREATE OR REPLACE VIEW v_cryptotransfers AS
SELECT 0 AS file_id, e.entity_num as node_account, e1.entity_num as from_account, e2.entity_num as to_account, tf.amount, t.tx_time_sec, t.seconds, t.nanos, t.xfer_count as txCount, 0 AS oos, tt.description as tx_type
FROM t_transactions t
    ,t_cryptotransfers tf
    ,t_transfer_types tt
    ,t_entities e
    ,t_entities e1
    ,t_entities e2
WHERE t.id = tf.tx_id
AND tf.payment_type_id = tt.id
AND e.id = t.node_account_id
AND e1.id = tf.from_account_id
AND e2.id = tf.to_account_id;

\echo Creating view v_reward_transactions

CREATE OR REPLACE VIEW v_reward_transactions AS
SELECT t.seconds, t.nanos, e.entity_num account_id, tt.name transaction_type, t.processed
FROM t_transactions t
	,t_transaction_types tt
  ,t_entities e
WHERE t.trans_type_id = tt.id
AND e.id = t.trans_account_id;

-- TRIGGERS
\echo Creating trigger t_transactions_bi

DROP TRIGGER IF EXISTS t_transactions_bi on t_transactions;

CREATE OR REPLACE FUNCTION transactions_bi() RETURNS trigger AS $t_transactions_bi$
	DECLARE
      milliseconds BIGINT;
    BEGIN
    	NEW.tx_time_sec := to_timestamp(NEW.seconds);
	    NEW.tx_time_hour := to_timestamp(NEW.seconds - (NEW.seconds % 3600));

        milliseconds := (NEW.consensus_seconds * 1000) + NEW.consensus_nanos / 1000000;

        NEW.consensus_time := to_timestamp(milliseconds::double precision / 1000);

        RETURN NEW;
    END;
$t_transactions_bi$ LANGUAGE plpgsql;

CREATE TRIGGER t_transactions_bi BEFORE INSERT ON t_transactions
	FOR EACH ROW EXECUTE PROCEDURE transactions_bi();

--
\echo Issuing grants
GRANT USAGE ON SCHEMA public TO :db_user;
GRANT CONNECT ON DATABASE :db_name TO :db_user;
GRANT ALL PRIVILEGES ON DATABASE :db_name TO :db_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO :db_user;
GRANT SELECT ON t_event_files TO :db_user;
GRANT ALL ON t_event_files TO :db_user;
GRANT ALL ON t_transactions TO :db_user;
GRANT ALL ON t_cryptotransfers TO :db_user;
GRANT ALL ON t_cryptotransferlists TO :db_user;
GRANT ALL ON t_transfer_types TO :db_user;
GRANT ALL ON t_transaction_types TO :db_user;
GRANT ALL ON t_entities TO :db_user;
GRANT ALL ON t_account_balances TO :db_user;
GRANT ALL ON t_account_balance_history TO :db_user;

GRANT SELECT ON v_cryptotransfers to :db_user;
GRANT SELECT ON v_event_files to :db_user;
GRANT SELECT ON v_transactions to :db_user;
GRANT SELECT ON v_cryptotransferlist to :db_user;
GRANT ALL ON s_transaction_types_seq TO :db_user;
GRANT ALL ON s_event_files_seq TO :db_user;
GRANT ALL ON s_transactions_seq TO :db_user;
GRANT ALL ON s_entities_seq TO :db_user;
