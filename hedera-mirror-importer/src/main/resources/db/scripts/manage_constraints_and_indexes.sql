-- Functions to drop/recreate costly constraints and indexes. Useful when bulk loading data.

CREATE OR REPLACE FUNCTION drop_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    -- Drop order is roughly determined by foreign key constraints. If foreign key A.fk_id references B.id, then
    -- foreign key constraint on A.fk_id needs to be dropped before dropping any indexes/primary_key on B.id.
    PERFORM drop_crypto_transfer_constraints_and_indexes();
    PERFORM drop_file_data_constraints_and_indexes();
    PERFORM drop_live_hash_constraints_and_indexes();
    PERFORM drop_contract_result_constraints_and_indexes();
    PERFORM drop_topic_message_constraints_and_indexes();
    PERFORM drop_transaction_constraints_and_indexes();
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
    PERFORM create_transaction_constraints_and_indexes();
    PERFORM create_topic_message_constraints_and_indexes();
    PERFORM create_contract_result_constraints_and_indexes();
    PERFORM create_live_hash_constraints_and_indexes();
    PERFORM create_file_data_constraints_and_indexes();
    PERFORM create_crypto_transfer_constraints_and_indexes();
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_topic_message_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE topic_message
        DROP CONSTRAINT IF EXISTS topic_message_pkey;
    DROP INDEX IF EXISTS topic_message__realm_num_timestamp;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_topic_message_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE UNIQUE INDEX IF NOT EXISTS topic_message_pkey ON topic_message (consensus_timestamp);
    ALTER TABLE topic_message
        ADD PRIMARY KEY USING INDEX topic_message_pkey;
    CREATE INDEX IF NOT EXISTS topic_message__realm_num_timestamp ON topic_message (realm_num, topic_num, consensus_timestamp);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_crypto_transfer_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    DROP INDEX IF EXISTS crypto_transfer__consensus_timestamp;
    DROP INDEX IF EXISTS crypto_transfer__entity_id_consensus_timestamp;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_crypto_transfer_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE INDEX IF NOT EXISTS crypto_transfer__consensus_timestamp ON crypto_transfer (consensus_timestamp);
    CREATE INDEX IF NOT EXISTS crypto_transfer__entity_id_consensus_timestamp
        ON crypto_transfer (entity_id, consensus_timestamp) WHERE entity_id != 98;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_file_data_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    DROP INDEX IF EXISTS idx__file_data__consensus;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_file_data_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE INDEX IF NOT EXISTS idx__file_data__consensus ON file_data (consensus_timestamp DESC);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION drop_live_hash_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    DROP INDEX IF EXISTS idx__live_hash__consensus;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_live_hash_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE INDEX IF NOT EXISTS idx__live_hash__consensus ON live_hash (consensus_timestamp DESC);
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION drop_contract_result_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    DROP INDEX IF EXISTS idx__contract_result__consensus;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_contract_result_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE INDEX IF NOT EXISTS idx__contract_result__consensus ON contract_result (consensus_timestamp DESC);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_transaction_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE transaction
        DROP CONSTRAINT IF EXISTS transaction_pkey;
    DROP INDEX IF EXISTS transaction__payer_account_id;
    DROP INDEX IF EXISTS transaction__transaction_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_transaction_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE transaction ADD PRIMARY KEY (consensus_ns);
    CREATE INDEX transaction__transaction_id ON transaction (valid_start_ns, payer_account_id);
    CREATE INDEX transaction__payer_account_id ON transaction (payer_account_id);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_t_entities_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE t_entities
        DROP CONSTRAINT IF EXISTS t_entities_pkey;
    ALTER TABLE t_entities
        DROP CONSTRAINT IF EXISTS c__t_entities__lower_ed25519;
    DROP INDEX IF EXISTS idx_t_entities_unq;
    DROP INDEX IF EXISTS idx__t_entities__ed25519_public_key_hex_natural_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_t_entities_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE t_entities ADD PRIMARY KEY (id);
    ALTER TABLE t_entities ADD CONSTRAINT c__t_entities__lower_ed25519
        CHECK (ed25519_public_key_hex::text = lower(ed25519_public_key_hex::text));
    CREATE UNIQUE INDEX IF NOT EXISTS idx_t_entities_unq ON t_entities (entity_shard, entity_realm, entity_num);
    CREATE INDEX IF NOT EXISTS idx__t_entities__ed25519_public_key_hex_natural_id
        ON t_entities (ed25519_public_key_hex, fk_entity_type_id, entity_shard, entity_realm, entity_num);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION drop_account_balances_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    ALTER TABLE account_balances
        DROP CONSTRAINT IF EXISTS pk__account_balances;
    DROP INDEX IF EXISTS idx__account_balances__account_then_timestamp;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION create_account_balances_constraints_and_indexes() RETURNS void AS
$$
DECLARE
BEGIN
    CREATE UNIQUE INDEX IF NOT EXISTS pk__account_balances
        ON account_balances (consensus_timestamp, account_realm_num, account_num);
    ALTER TABLE account_balances ADD PRIMARY KEY USING INDEX pk__account_balances;
    CREATE INDEX IF NOT EXISTS idx__account_balances__account_then_timestamp
        ON account_balances (account_realm_num DESC, account_num DESC, consensus_timestamp DESC);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup() RETURNS void AS
$$
DECLARE
BEGIN
    TRUNCATE TABLE account_balance_sets RESTART IDENTITY CASCADE;
    TRUNCATE TABLE account_balances RESTART IDENTITY CASCADE;
    TRUNCATE TABLE contract_result RESTART IDENTITY CASCADE;
    TRUNCATE TABLE crypto_transfer RESTART IDENTITY CASCADE;
    TRUNCATE TABLE file_data RESTART IDENTITY CASCADE;
    TRUNCATE TABLE live_hash RESTART IDENTITY CASCADE;
    TRUNCATE TABLE topic_message RESTART IDENTITY CASCADE;
    TRUNCATE TABLE record_file RESTART IDENTITY CASCADE;
    TRUNCATE TABLE t_entities RESTART IDENTITY CASCADE;
    TRUNCATE TABLE transaction RESTART IDENTITY CASCADE;
    UPDATE t_application_status SET status_value = NULL;
END;
$$ LANGUAGE plpgsql;
