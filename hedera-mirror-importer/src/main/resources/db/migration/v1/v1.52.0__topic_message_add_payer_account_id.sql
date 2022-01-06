update topic_message tm
set payer_account_id = t.payer_account_id
from transaction t
where tm.consensus_timestamp = t.consensus_timestamp
and tm.payer_account_id is null;

-- alter table topic_message
-- alter column payer_account_id set not null;
