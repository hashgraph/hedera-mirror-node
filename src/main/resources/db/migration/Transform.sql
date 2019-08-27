ALTER TABLE t_entities RENAME COLUMN fk_entity_type_id TO entity_type_id;
ALTER TABLE t_entities RENAME COLUMN fk_prox_acc_id TO proxy_account_entity_id;
ALTER TABLE t_entities RENAME COLUMN exp_time_ns TO expiry_time_ns;
ALTER TABLE t_entities DROP COLUMN exp_time_seconds;
ALTER TABLE t_entities DROP COLUMN exp_time_nanos;

ALTER TABLE t_account_balance_history DROP COLUMN seconds;
ALTER TABLE t_account_balance_history DROP COLUMN nanos;
ALTER TABLE t_account_balance_history DROP COLUMN snapshot_time;
ALTER TABLE t_account_balance_history RENAME COLUMN fk_balance_id TO balance_id;

ALTER TABLE t_transactions DROP COLUMN vs_seconds;
ALTER TABLE t_transactions DROP COLUMN vs_nanos;
ALTER TABLE t_transactions DROP COLUMN consensus_seconds;
ALTER TABLE t_transactions DROP COLUMN consensus_nanos;

ALTER TABLE t_transactions RENAME COLUMN fk_node_acc_id TO node_entity_id;
ALTER TABLE t_transactions RENAME COLUMN fk_trans_type_id TO transaction_type_id;
ALTER TABLE t_transactions RENAME COLUMN fk_result_id TO transaction_result_id;
ALTER TABLE t_transactions RENAME COLUMN fk_payer_acc_id TO payer_entity_id;
ALTER TABLE t_transactions RENAME COLUMN fk_cud_entity_id TO cud_entity_id;
ALTER TABLE t_transactions RENAME COLUMN fk_rec_file_id TO record_file_id;

ALTER TABLE t_cryptotransferlists ADD COLUMN transactions_consensus_ns BIGINT;

UPDATE t_cryptotransferlists ctl1
SET transactions_consensus_ns =
  (SELECT t.consensus_ns
  FROM t_transactions t
  WHERE ctl.fk_trans_id = t.id);

ALTER TABLE t_cryptotransferlists DROP COLUMN fk_trans_id;
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk_crypto_trans_list_consensus_ns FOREIGN KEY (transactions_consensus_ns) REFERENCES t_transactions (consensus_ns) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE t_file_data ADD COLUMN transactions_consensus_ns BIGINT;

UPDATE t_file_data tfd
SET transactions_consensus_ns =
  (SELECT t.consensus_ns
  FROM t_transactions t
  WHERE tfd.fk_trans_id = t.id);

ALTER TABLE t_file_data DROP COLUMN fk_trans_id;
ALTER TABLE t_file_data ADD CONSTRAINT fk_file_data_consensus_ns FOREIGN KEY (transactions_consensus_ns) REFERENCES t_transactions (consensus_ns) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE t_contract_result ADD COLUMN transactions_consensus_ns BIGINT;

UPDATE t_contract_result tcr
SET transactions_consensus_ns =
  (SELECT t.consensus_ns
  FROM t_transactions t
  WHERE tcr.fk_trans_id = t.id);

ALTER TABLE t_contract_result DROP COLUMN fk_trans_id;
ALTER TABLE t_contract_result ADD CONSTRAINT fk_cont_result_consensus_ns FOREIGN KEY (transactions_consensus_ns) REFERENCES t_transactions (consensus_ns) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE t_livehashes ADD COLUMN transactions_consensus_ns BIGINT;

UPDATE t_livehashes tlh
SET transactions_consensus_ns =
  (SELECT t.consensus_ns
  FROM t_transactions t
  WHERE tlh.fk_trans_id = t.id);

ALTER TABLE t_livehashes DROP COLUMN fk_trans_id;
ALTER TABLE t_livehashes ADD CONSTRAINT fk_livehashes_consensus_ns FOREIGN KEY (transactions_consensus_ns) REFERENCES t_transactions (consensus_ns) ON DELETE CASCADE ON UPDATE CASCADE;

DROP INDEX idx_t_account_balance_hist_snap_ns;
DROP INDEX idx_t_account_bal_unq;
DROP INDEX idx_t_account_bal_id_num;

ALTER TABLE t_cryptotransferlists DROP CONSTRAINT fk_ctl_acc_id;
DROP INDEX idx_t_cryptotransferlist_amount;

DROP INDEX idx_t_entities_id;
DROP INDEX idx_t_entities_exp_t_ns;

ALTER TABLE t_transactions DROP CONSTRAINT t_transactions_pkey;
DROP INDEX idx_t_transactions_id;

ALTER TABLE t_transactions ADD CONSTRAINT pk_consensus_ns PRIMARY KEY USING INDEX idx_t_transactions_cs_ns;

DROP INDEX idx_t_transactions_vs_ns;

ALTER TABLE t_transactions DROP COLUMN id;

DROP SEQUENCE s_transactions_seq;

ALTER TABLE t_contract_result RENAME TO t_contract_results;

ALTER TABLE t_account_balances ADD COLUMN entities_id BIGINT;

UPDATE t_account_balances
SET entities_id =
  (SELECT e.id
  FROM t_entities e, t_entity_types et
  WHERE e.entity_num = num
  AND et.id = e.entity_type_id
  AND et.name = 'account'
);

ALTER TABLE t_account_balance_history ADD COLUMN entities_id BIGINT;

UPDATE t_account_balance_history
SET entities_id =
  (SELECT b.entities_id
  FROM t_account_balances b
  WHERE b.id = balance_id);

ALTER TABLE t_account_balance_history DROP COLUMN balance_id;

ALTER TABLE t_account_balance_history ADD CONSTRAINT fk_acc_bal_hist_entities_id FOREIGN KEY (entities_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

CREATE UNIQUE INDEX idx_t_account_bal_hist_unq ON t_account_balance_history (snapshot_time_ns, entities_id);

ALTER TABLE t_account_balances DROP COLUMN shard;
ALTER TABLE t_account_balances DROP COLUMN realm;
ALTER TABLE t_account_balances DROP COLUMN num;
ALTER TABLE t_account_balances DROP COLUMN id;

ALTER TABLE t_account_balances ADD CONSTRAINT fk_acc_bal_entities_id FOREIGN KEY (entities_id) REFERENCES t_entities (id) ON DELETE CASCADE ON UPDATE CASCADE;

CREATE UNIQUE INDEX idx_t_account_bal_unq ON t_account_balances (entities_id);
CREATE INDEX idx_t_account_bal_ent_bal ON t_account_balances (entities_id, balance);

ALTER TABLE t_entities rename column entity_type_id to entity_types_id;

ALTER TABLE t_transactions RENAME COLUMN node_entity_id TO node_entities_id;
ALTER TABLE t_transactions RENAME COLUMN transaction_type_id TO transaction_types_id;
ALTER TABLE t_transactions RENAME COLUMN transaction_result_id TO transaction_results_id;
ALTER TABLE t_transactions RENAME COLUMN payer_entity_id TO payer_entities_id;
ALTER TABLE t_transactions RENAME COLUMN cud_entity_id TO cud_entities_id;
