-- Define trigger function. Base64 encoding is required since JSON doesn't support binary
create or replace function topic_message_notifier()
    returns trigger
    language plpgsql
as
$$
declare
    topicmessage text := TG_ARGV[0];
begin
    perform (
        with payload(consensus_timestamp, message, realm_num, running_hash, running_hash_version, sequence_number, topic_num) as
                 (
                     select NEW.consensus_timestamp,
                            encode(NEW.message, 'base64'),
                            NEW.realm_num,
                            encode(NEW.running_hash, 'base64'),
                            NEW.running_hash_version,
                            NEW.sequence_number,
                            NEW.topic_num
                 )
        select pg_notify(topicmessage, row_to_json(payload)::text)
        from payload
    );
    return null;
end;
$$;

-- Setup trigger
create trigger topic_message_trigger
    after insert
    on topic_message
    for each row
execute procedure topic_message_notifier('topic_message');
commit;
