-- Loads data from CSV files to PostgresSQL

SELECT cleanup();
SELECT drop_constraints_and_indexes();

\echo ------------------------------
\echo COPY data to t_record_files from %%TMP_DIR%%/t_record_files
\echo ------------------------------
\copy t_record_files FROM '%%TMP_DIR%%/t_record_files' WITH CSV HEADER;


\echo ------------------------------
\echo COPY data to t_entities from %%TMP_DIR%%/t_entities
\echo ------------------------------
\copy t_entities FROM '%%TMP_DIR%%/t_entities' WITH CSV HEADER;


\echo ------------------------------
\echo COPY data to t_transactions from %%TMP_DIR%%/t_transactions
\echo ------------------------------
\copy t_transactions FROM '%%TMP_DIR%%/t_transactions' WITH CSV HEADER;


\echo ------------------------------
\echo COPY data to t_cryptotransferlists from %%TMP_DIR%%/t_cryptotransferlists
\echo ------------------------------
\copy t_cryptotransferlists FROM '%%TMP_DIR%%/t_cryptotransferlists' WITH CSV HEADER;


\echo ------------------------------
\echo COPY data to t_file_data from %%TMP_DIR%%/t_file_data
\echo ------------------------------
\copy t_file_data FROM '%%TMP_DIR%%/t_file_data' WITH CSV HEADER;

\echo ------------------------------
\echo COPY data to account_balances from %%TMP_DIR%%/account_balances
\echo ------------------------------
\copy account_balances FROM '%%TMP_DIR%%/account_balances' WITH CSV HEADER;

SELECT create_constraints_and_indexes();
