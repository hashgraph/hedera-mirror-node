-------------------
-- Set topic_message payer_account_id where null, set payer_account_id to not null, and add a new column for
-- initial_transaction_id to hold the TransactionID protobuf bytes
-------------------

-- Set topic_message.payer_account_id for non-chunked messages (chunked messages already handled by importer)
update topic_message tm
set payer_account_id = t.payer_account_id
from transaction t
where tm.consensus_timestamp = t.consensus_timestamp
and tm.payer_account_id is null;

alter table topic_message
alter column payer_account_id set not null;

-- Add a column for the protobuf initial transaction id
alter table topic_message
add column initial_transaction_id bytea null;
