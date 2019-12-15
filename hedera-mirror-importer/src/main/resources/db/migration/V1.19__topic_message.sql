CREATE OR REPLACE FUNCTION drop_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    -- Drop order is roughly determined by foreign key constraints. If foreign key A.fk_id references B.id, then
    -- foreign key constraint on A.fk_id needs to be dropped before dropping any indexes/primary_key on B.id.
    PERFORM drop_t_cryptotransferlists_constraints_and_indexes();
    PERFORM drop_t_file_data_constraints_and_indexes();
    PERFORM drop_t_livehashes_constraints_and_indexes();
    PERFORM drop_t_contract_result_constraints_and_indexes();
    PERFORM drop_topic_message_constraints_and_indexes();
    PERFORM drop_t_transactions_constraints_and_indexes();
    PERFORM drop_t_entities_constraints_and_indexes();
    PERFORM drop_account_balances_constraints_and_indexes();
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    -- Creates should be in reverse order of drops above. If foreign key A.fk_id references B.id, then
    -- foreign key constraint on A.fk_id should be created after creating index/primary_key on B.id.
    PERFORM create_account_balances_constraints_and_indexes();
    PERFORM create_t_entities_constraints_and_indexes();
    PERFORM create_t_transactions_constraints_and_indexes();
    PERFORM create_topic_message_constraints_and_indexes();
    PERFORM create_t_contract_result_constraints_and_indexes();
    PERFORM create_t_livehashes_constraints_and_indexes();
    PERFORM create_t_file_data_constraints_and_indexes();
    PERFORM create_t_cryptotransferlists_constraints_and_indexes();
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION drop_topic_message_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE topic_message
        DROP CONSTRAINT topic_message_pkey;
    DROP INDEX topic_message__realm_num_timestamp;
    DROP TRIGGER topic_message_trigger ON topic_message; -- TODO : verify
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_topic_message_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE UNIQUE INDEX topic_message_pkey ON topic_message (consensus_timestamp);
    ALTER TABLE topic_message
        ADD PRIMARY KEY USING INDEX topic_message_pkey;
    CREATE INDEX topic_message__realm_num_timestamp ON topic_message (realm_num, topic_num, consensus_timestamp);
    CREATE TRIGGER topic_message_trigger
        AFTER INSERT ON topic_message FOR EACH ROW EXECUTE PROCEDURE topic_message_notifier('topic_message');
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup() RETURNS void AS
$$
DECLARE
BEGIN
    TRUNCATE TABLE account_balance_sets RESTART IDENTITY CASCADE;
    TRUNCATE TABLE account_balances RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_contract_result RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_cryptotransferlists RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_file_data RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_livehashes RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_record_files RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_entities RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_transactions RESTART IDENTITY CASCADE;
    TRUNCATE TABLE topic_message RESTART IDENTITY CASCADE;
    UPDATE t_application_status SET status_value = NULL;
END;
$$ LANGUAGE plpgsql;
