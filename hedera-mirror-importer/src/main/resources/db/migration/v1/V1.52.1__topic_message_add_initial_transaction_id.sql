-- Add a column for the protobuf initial transaction id
alter table topic_message
add column initial_transaction_id bytea null;
