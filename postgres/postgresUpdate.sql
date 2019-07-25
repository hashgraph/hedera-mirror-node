\set db_name hederamirror
\set db_user hederamirror
\set db_password mysecretpassword

\echo Creating table t_version
CREATE TABLE IF NOT EXISTS t_version (
	version       INTEGER
);

\echo Inserting into t_version

\unset rcount
SELECT count(*) = 0 AS rcount FROM t_version
\gset

\if :rcount
  INSERT INTO t_version VALUES (1);
\endif

\unset version
SELECT version <= 1 AS version
\gset

\set ON_ERROR_STOP on

\if :version
  -- July 20th 2019 - ADDITION OF "SECONDS" column to t_account_balance_history
  \echo Adding seconds column to t_account_balance_history
  ALTER TABLE t_account_balance_history ADD COLUMN seconds BIGINT;

  \echo Populating t_account_balance_history.seconds
  UPDATE t_account_balance_history
  SET seconds = EXTRACT(EPOCH FROM snapshot_time)
  WHERE seconds IS NULL;

  \echo Setting t_account_balance_history.seconds to NOT NULL
  ALTER TABLE t_account_balance_history ALTER COLUMN seconds SET NOT NULL;

  -- July 21 2019 - New index on t_cryptotransfers
  \echo Creating index idx_cryptotransfers_id_from_to_account
  CREATE UNIQUE INDEX idx_cryptotransfers_id_from_to_account ON t_cryptotransfers (tx_id, from_account_id,to_account_id);

  -- July 22 2019
  \echo Dropping unnecessary indices
  DROP INDEX idx_t_transactions_trans_account;
  DROP INDEX idx_t_transactions_cons_seconds;
  DROP INDEX idx_t_transactions_cons_nanos;
  DROP INDEX idx_t_transactions_nanos;
  DROP INDEX idx_t_transactions_tx_time_hour;
  DROP INDEX idx_t_cryptotransferlist_tx_id_amount;
  DROP INDEX idx_t_cryptotransfers_amount;
  DROP INDEX idx_t_entities_num;
  DROP INDEX idx_t_entities_realm;
  DROP INDEX idx_t_entities_shard;
  DROP INDEX idx_t_entities_id_num;
  DROP INDEX idx_cryptotransferslist_tx_id;
  DROP INDEX idx_cryptotransfers_id_from_to_account;
  DROP INDEX idx_t_account_bal_hist_unq;
  DROP INDEX idx_t_transfer_types_unq;
  DROP INDEX idx_t_transaction_types_unq;

  \echo Creating new indices
  CREATE UNIQUE INDEX idx_t_entities_id_num_unq ON t_entities (id, entity_num);
  CREATE UNIQUE INDEX idx_t_transactions_transaction_id_unq ON t_transactions (seconds, nanos, trans_account_id);
  CREATE INDEX idx_cryptotransferslist_tx_id ON t_cryptotransferlists (tx_id);
  CREATE UNIQUE INDEX idx_t_account_bal_acc_num_unq ON t_account_balances (account_num);

  CREATE UNIQUE INDEX idx_t_account_bal_hist_unq ON t_account_balance_history (snapshot_time, seconds, account_num, account_realm, account_shard);
  CREATE UNIQUE INDEX idx_t_account_bal_sec_accnum_unq ON t_account_balance_history (seconds, account_num);

  \echo Adding transaction_id column to t_transactions
  ALTER TABLE t_transactions ADD COLUMN transaction_id VARCHAR(60);

  \echo migrating data to t_transactions.transaction_id
  UPDATE t_transactions t
  set transaction_id = (
      SELECT '0.0.' || e.entity_num || '-' || t2.seconds || '-' || t2.nanos
      FROM t_transactions t2
      , t_entities e
      WHERE e.id = t2.trans_account_id
      AND   t.id = t2.id
    )
  WHERE t.transaction_id IS NULL;

  \echo Setting t_transactions.transaction_id to NOT NULL
  ALTER TABLE t_transactions ALTER COLUMN transaction_id SET NOT NULL;

  \echo Adding index idx_t_transactions_transaction_id to t_transactions
  CREATE UNIQUE INDEX idx_t_transactions_transaction_id ON t_transactions (transaction_id);

  -- July 24 2019

  \echo Creating table t_file_data

  CREATE TABLE t_file_data (
  	tx_id                BIGINT NOT NULL
  	,file_data           BYTEA
  );

  \echo Creating constraints on t_file_data
  ALTER TABLE t_file_data ADD CONSTRAINT fk_fd_tx_id FOREIGN KEY (tx_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;

  \echo Creating indices on t_file_data
  CREATE INDEX idx_file_data_tx_id ON t_file_data (tx_id);

  \echo Granting access to t_file_data
  GRANT ALL ON t_file_data TO :db_user;

  \echo Creating table t_contract_result

  CREATE TABLE t_contract_result (
  	tx_id                BIGINT NOT NULL
    ,function_params     BYTEA
  	,gas_supplied				 BIGINT
  	,call_result         BYTEA
  	,gas_used            BIGINT
  );

  \echo Creating constraints on t_contract_result
  ALTER TABLE t_contract_result ADD CONSTRAINT fk_cr_tx_id FOREIGN KEY (tx_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;

  \echo Creating indices on t_contract_result
  CREATE INDEX idx_contract_result_tx_id ON t_contract_result (tx_id);

  \echo Granting access to t_contract_result
  GRANT ALL ON t_contract_result TO :db_user;

  \echo Creating table t_claim_data

  CREATE TABLE t_claim_data (
  	tx_id                BIGINT NOT NULL
  	,claim_data           BYTEA
  );

  \echo Creating constraints on t_claim_data
  ALTER TABLE t_claim_data ADD CONSTRAINT fk_cd_tx_id FOREIGN KEY (tx_id) REFERENCES t_transactions (id) ON DELETE CASCADE ON UPDATE CASCADE;

  \echo Creating indices on t_claim_data
  CREATE INDEX idx_claim_data_tx_id ON t_claim_data (tx_id);

  \echo Granting access to t_claim_data
  GRANT ALL ON t_claim_data TO :db_user;

  -- July 25 2019

  ALTER TABLE t_entities ADD COLUMN exp_time_seconds BIGINT;
  ALTER TABLE t_entities ADD COLUMN exp_time_nanos BIGINT;
  ALTER TABLE t_entities ADD COLUMN auto_renew_period BIGINT;
  ALTER TABLE t_entities ADD COLUMN admin_key BYTEA;
  ALTER TABLE t_entities ADD COLUMN key BYTEA;
  ALTER TABLE t_entities ADD COLUMN proxy_account_id BIGINT;

  UPDATE t_version set version = 2;
\endif
# end of version 1->2  upgrades
