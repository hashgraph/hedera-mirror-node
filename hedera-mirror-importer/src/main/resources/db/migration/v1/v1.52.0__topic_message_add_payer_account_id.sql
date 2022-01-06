-- Set topic_message.payer_account_id for non-chunked messages (chunked messages already handled by importer)

update topic_message tm
set payer_account_id = t.payer_account_id
from transaction t
where tm.consensus_timestamp = t.consensus_timestamp
and tm.payer_account_id is null;
