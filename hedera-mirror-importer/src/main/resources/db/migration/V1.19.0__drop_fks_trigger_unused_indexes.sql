-- Changes to optimize write performance
-- Drop foreign keys in t_transactions
-- Drop foreign keys referencing t_transactions(consensus_timestamp) in t_cryptotransferlists, t_file_data,
--   t_livehashes and t_contract_result.
-- Drop trigger in topic_message
-- Drop unused indexes: idx_t_transactions_rec_file, idx__t_cryptotransferlist_amount

alter table t_transactions
    drop constraint fk_cud_entity_id;
alter table t_transactions
    drop constraint fk_node_account_id;
alter table t_transactions
    drop constraint fk_payer_account_id;
alter table t_transactions
    drop constraint fk_rec_file_id;

alter table t_cryptotransferlists
    drop constraint if exists fk__t_transactions;
alter table t_file_data
    drop constraint if exists fk__t_transactions;
alter table t_livehashes
    drop constraint if exists fk__t_transactions;
alter table t_contract_result
    drop constraint if exists fk__t_transactions;

drop trigger topic_message_trigger on topic_message;

drop index idx_t_transactions_rec_file;
drop index idx__t_cryptotransferlist_amount;
