-------------------
-- Restore Backup tables use efficient COPY process to CSV's
-------------------

\copy account_balance from account_balance.csv csv;

\copy account_balance_file from account_balance_file.csv csv;

\copy address_book from address_book.csv csv;

\copy address_book_entry from address_book_entry.csv csv;

\copy contract_result from contract_result.csv csv;

\copy crypto_transfer from crypto_transfer.csv csv;

\copy file_data from file_data.csv csv;

\copy live_hash from live_hash.csv csv;

\copy non_fee_transfer from non_fee_transfer.csv csv;

\copy record_file from record_file.csv csv;

\copy schedule from schedule.csv csv;

\copy t_entities from t_entities.csv csv;

\copy t_entity_types from t_entity_types.csv csv;

\copy t_transaction_results from t_transaction_results.csv csv;

\copy t_transaction_types from t_transaction_types.csv csv;

\copy token from token.csv csv;

\copy token_account from token_account.csv csv;

\copy token_balance from token_balance.csv csv;

\copy token_transfer from token_transfer.csv csv;

\copy topic_message from topic_message.csv csv;

\copy transaction from transaction.csv csv;

\copy transaction_signature from transaction_signature.csv csv;
