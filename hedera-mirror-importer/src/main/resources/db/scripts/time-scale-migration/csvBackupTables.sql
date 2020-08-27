-------------------
-- Backup tables use efficient COPY process to CSV's
-------------------

\copy (select * from account_balance) to account_balance.csv delimiter ',' csv;

\copy (select * from account_balance_sets) to account_balance_sets.csv delimiter ',' csv;

\copy (select * from address_book) to address_book.csv delimiter ',' csv;

\copy (select * from address_book_entry) to address_book_entry.csv delimiter ',' csv;

\copy (select * from contract_result) to contract_result.csv delimiter ',' csv;

\copy (select * from crypto_transfer) to crypto_transfer.csv delimiter ',' csv;

\copy (select * from file_data) to file_data.csv delimiter ',' csv;

\copy (select * from flyway_schema_history) to flyway_schema_history.csv delimiter ',' csv;

\copy (select * from live_hash) to live_hash.csv delimiter ',' csv;

\copy (select * from non_fee_transfer) to non_fee_transfer.csv delimiter ',' csv;

\copy (select * from record_file) to record_file.csv delimiter ',' csv;

\copy (select * from t_application_status) to t_application_status.csv delimiter ',' csv;

\copy (select * from t_entities) to t_entities.csv delimiter ',' csv;

\copy (select * from t_entity_types) to t_entity_types.csv delimiter ',' csv;

\copy (select * from t_transaction_results) to t_transaction_results.csv delimiter ',' csv;

\copy (select * from t_transaction_types) to t_transaction_types.csv delimiter ',' csv;

\copy (select * from topic_message) to topic_message.csv delimiter ',' csv;

\copy (select * from transaction) to transaction.csv delimiter ',' csv;
