\set db_name `postgres`
\set db_user `postgres`
\set api_user `api`
-- \set db_name `echo "$POSTGRES_DB"`
-- \set db_user `echo "$POSTGRES_USER"`
-- \set api_user `echo "$DB_USER"`

\c :db_name

\unset version
SELECT version = 1 AS version FROM t_version
\gset

\set ON_ERROR_STOP on

\if :version
	-- database is at version 1, perform the following updates
	\echo Creating sequence s_events_id_seq
	CREATE SEQUENCE s_events_id_seq;

	\echo Creating events table
	CREATE TABLE t_events
	(
			id                       BIGINT  NOT NULL DEFAULT nextval('s_events_id_seq')
	    ,consensus_order         BIGINT  NOT NULL
	    ,creator_node_id         BIGINT  NOT NULL
	    ,creator_seq             BIGINT  NOT NULL
	    ,other_node_id           BIGINT
	    ,other_seq               BIGINT
	    ,signature               BYTEA   NOT NULL
	    ,hash                    BYTEA   NOT NULL
	    ,self_parent_id          BIGINT
	    ,other_parent_id         BIGINT
	    ,self_parent_hash        BYTEA
	    ,other_parent_hash       BYTEA
	    ,self_parent_generation  BIGINT
	    ,other_parent_generation BIGINT
	    ,generation              BIGINT  NOT NULL
	    ,created_timestamp_ns    BIGINT  NOT NULL
	    ,consensus_timestamp_ns  BIGINT  NOT NULL
	    ,latency_ns              BIGINT  NOT NULL
	    ,txs_bytes_count         INTEGER NOT NULL
	    ,platform_tx_count       INTEGER NOT NULL
	    ,app_tx_count            INTEGER NOT NULL
	);

	\echo Creating constraints on t_events
	ALTER TABLE t_events ADD CONSTRAINT pk_events_id PRIMARY KEY (id);
	ALTER TABLE t_events ADD CONSTRAINT fk_events_self_parent_id FOREIGN KEY (self_parent_id) REFERENCES t_events (id);
	ALTER TABLE t_events ADD CONSTRAINT fk_events_other_parent_id FOREIGN KEY (other_parent_id) REFERENCES t_events (id);

	GRANT ALL ON t_events TO :db_user;
	GRANT ALL ON s_events_id_seq TO :db_user;
	GRANT SELECT ON t_events TO :api_user;

	UPDATE t_version set version = 2;
\endif
# end of version 1->2  upgrades
