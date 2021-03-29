-------------------
-- Backup tables use efficient COPY process to CSV's
-------------------

\copy account_balance to account_balance.csv delimiter ',' csv;

\copy account_balance_file to account_balance_file.csv delimiter ',' csv;

\copy address_book to address_book.csv delimiter ',' csv;

\copy address_book_entry to address_book_entry.csv delimiter ',' csv;

\copy contract_result to contract_result.csv delimiter ',' csv;

\copy crypto_transfer to crypto_transfer.csv delimiter ',' csv;

\copy file_data to file_data.csv delimiter ',' csv;

\copy live_hash to live_hash.csv delimiter ',' csv;

\copy non_fee_transfer to non_fee_transfer.csv delimiter ',' csv;

\copy record_file to record_file.csv delimiter ',' csv;

\copy schedule to schedule.csv delimiter ',' csv;

\copy t_entities to t_entities.csv delimiter ',' csv;

\copy t_entity_types to t_entity_types.csv delimiter ',' csv;

\copy t_transaction_results to t_transaction_results.csv delimiter ',' csv;

\copy t_transaction_types to t_transaction_types.csv delimiter ',' csv;

\copy token to token.csv delimiter ',' csv;

\copy token_account to token_account.csv delimiter ',' csv;

\copy token_balance to token_balance.csv delimiter ',' csv;

\copy token_transfer to token_transfer.csv delimiter ',' csv;

\copy topic_message to topic_message.csv delimiter ',' csv;

\copy transaction to transaction.csv delimiter ',' csv;

\copy transaction_signature to transaction_signature.csv delimiter ',' csv;
