-- Loads data from CSV files to PostgresSQL

\echo ------------------------------
\echo Truncating tables
\echo ------------------------------
truncate t_entities cascade;
truncate t_record_files cascade;
truncate t_transactions cascade;
truncate t_cryptotransferlists;
truncate t_file_data;
truncate account_balances;


\echo ------------------------------
\echo COPY data to t_record_files from %%TMP_DIR%%/t_record_files
\echo ------------------------------
--  No need to drop indexes and constraints for t_entities
\copy t_record_files FROM '%%TMP_DIR%%/t_record_files' WITH CSV HEADER;


\echo ------------------------------
\echo COPY data to t_entities from %%TMP_DIR%%/t_entities
\echo ------------------------------
--  No need to drop indexes and constraints for t_entities
\copy t_entities FROM '%%TMP_DIR%%/t_entities' WITH CSV HEADER;


\echo ------------------------------
\echo COPY data to t_transactions from %%TMP_DIR%%/t_transactions
\echo ------------------------------
\echo Dropping all constraints and indexes
-- Dropping primary key on consensus_ns will require dropping indexes in 3 other tables too. Leaving it as optimization
-- for later. TODO.
-- ALTER TABLE t_transactions DROP CONSTRAINT pk__t_transactions__consensus_ns;
ALTER TABLE t_transactions DROP CONSTRAINT fk_cud_entity_id;
ALTER TABLE t_transactions DROP CONSTRAINT fk_node_account_id;
ALTER TABLE t_transactions DROP CONSTRAINT fk_payer_account_id;
ALTER TABLE t_transactions DROP CONSTRAINT fk_rec_file_id;
DROP INDEX idx__t_transactions__transaction_id;
DROP INDEX idx_t_transactions_node_account;
DROP INDEX idx_t_transactions_payer_id;
DROP INDEX idx_t_transactions_rec_file;
\echo Copying data
\copy t_transactions FROM '%%TMP_DIR%%/t_transactions' WITH CSV HEADER;
\echo Recreate all constraints and indexes
CREATE INDEX idx__t_transactions__transaction_id ON t_transactions (valid_start_ns, fk_payer_acc_id);
CREATE INDEX idx_t_transactions_node_account ON t_transactions (fk_node_acc_id);
CREATE INDEX idx_t_transactions_payer_id ON t_transactions (fk_payer_acc_id);
CREATE INDEX idx_t_transactions_rec_file ON t_transactions (fk_rec_file_id);
ALTER TABLE t_transactions ADD CONSTRAINT fk_cud_entity_id FOREIGN KEY (fk_cud_entity_id) REFERENCES t_entities (id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_node_account_id FOREIGN KEY (fk_node_acc_id) REFERENCES t_entities (id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_payer_account_id FOREIGN KEY (fk_payer_acc_id) REFERENCES t_entities (id) ON UPDATE CASCADE ON DELETE CASCADE;
ALTER TABLE t_transactions ADD CONSTRAINT fk_rec_file_id FOREIGN KEY (fk_rec_file_id) REFERENCES t_record_files (id) ON UPDATE CASCADE ON DELETE CASCADE;
-- CREATE UNIQUE INDEX pk__t_transactions__consensus_ns ON t_transactions(consensus_ns);
-- ALTER TABLE t_transactions ADD PRIMARY KEY USING INDEX pk__t_transactions__consensus_ns;


\echo ------------------------------
\echo COPY data to t_cryptotransferlists from %%TMP_DIR%%/t_cryptotransferlists
\echo ------------------------------
\echo Dropping all constraints and indexes
ALTER TABLE t_cryptotransferlists DROP CONSTRAINT fk__t_transactions;
DROP INDEX idx__t_cryptotransferlists__ts_then_acct;
DROP INDEX idx__t_cryptotransferlists__consensus_and_realm_and_num;
DROP INDEX idx_t_cryptotransferlist_amount;
\echo Copying data
\copy t_cryptotransferlists FROM '%%TMP_DIR%%/t_cryptotransferlists' WITH CSV HEADER;
\echo Recreate all constraints and indexes
CREATE INDEX idx_t_cryptotransferlist_amount ON t_cryptotransferlists (amount);
CREATE INDEX idx__t_cryptotransferlists__consensus_and_realm_and_num ON t_cryptotransferlists (consensus_timestamp, realm_num, entity_num);
CREATE INDEX idx__t_cryptotransferlists__ts_then_acct ON t_cryptotransferlists (consensus_timestamp, realm_num, entity_num);
ALTER TABLE t_cryptotransferlists ADD CONSTRAINT fk__t_transactions FOREIGN KEY (consensus_timestamp) REFERENCES t_transactions (consensus_ns);


\echo ------------------------------
\echo COPY data to t_file_data from %%TMP_DIR%%/t_file_data
\echo ------------------------------
\echo Dropping all constraints and indexes
DROP INDEX idx__t_file_data__consensus;
\echo Copying data
\copy t_file_data FROM '%%TMP_DIR%%/t_file_data' WITH CSV HEADER;
\echo Recreate all constraints and indexes
CREATE INDEX idx__t_file_data__consensus ON t_file_data (consensus_timestamp DESC);


\echo ------------------------------
\echo COPY data to account_balances from %%TMP_DIR%%/account_balances
\echo ------------------------------
\echo Dropping all constraints and indexes
ALTER TABLE account_balances DROP CONSTRAINT pk__account_balances;
DROP INDEX idx__account_balances__account_then_timestamp;
\echo Copying data
\copy account_balances FROM '%%TMP_DIR%%/account_balances' WITH CSV HEADER;
\echo Recreate all constraints and indexes
CREATE UNIQUE INDEX pk__account_balances ON account_balances (consensus_timestamp, account_realm_num, account_num);
ALTER TABLE account_balances ADD PRIMARY KEY USING INDEX pk__account_balances;
CREATE INDEX idx__account_balances__account_then_timestamp ON account_balances (account_realm_num DESC, account_num DESC, consensus_timestamp DESC);


-- Dump some stats to the console
SELECT count(*) from t_entities;
SELECT count(*) from t_transactions;
SELECT count(*) from t_cryptotransferlists;
SELECT count(*) from t_file_data;
SELECT count(*) from account_balances;
SELECT count(*) from t_record_files;
