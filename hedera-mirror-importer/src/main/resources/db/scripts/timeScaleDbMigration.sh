#!/bin/sh
set -e

# assumes 1. Valid populated current mirror node postgres db with appropriate user 2. New empty TimeScaleDb db host with appropriate user
echo "Migrating Mirror Node Data from Postgres($OLD_DB_HOST:$OLD_DB_PORT) to TimeScaleDb($NEW_DB_HOST:$NEW_DB_PORT)"

echo "1. Migrate schema to TimeScaleDb"
pg_dump -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER --schema-only -f old_mirror_node.bak mirror_node

echo "2. Restore table schemas to new host"
psql -d $NEW_DB_NAME <old_mirror_node.bak

# Optionally we could skip step 1 and 2 and just reate a whole new schema with a new init.sql -> timeScaleDBInit.sql
echo "3. Create new hyper tables"
declare -A tables
tables[account_balance]=consensus_timestamp
tables[account_balance_sets]=consensus_timestamp
tables[address_book]=start_consensus_timestamp
tables[address_book_entry]=id
tables[contract_result]=consensus_timestamp
tables[crypto_transfer]=consensus_timestamp
tables[file_data]=consensus_timestamp
tables[live_hash]=consensus_timestamp
tables[non_fee_transfer]=consensus_timestamp
tables[record_file]=consensus_start
tables[entities]=id
tables[topic_message]=consensus_timestamp
tables[transaction]=consensus_ns

for table in "${!tables[@]}"; do
    if [-n ${tables[$table]}]; then
        echo "Create hyper table for $table"
        psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "select create_hypertable('$table', '${tables[$table]}')"
    fi
done
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "select create_hypertable('account_balance', 'consensus_timestamp')"

echo "3. Copy tables into separate CSV's"
for table in "${!tables[@]}"; do
    echo "Create table $table to CSV"
    psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM $table) TO $table.csv DELIMITER ',' CSV"
done

#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM account_balance) TO account_balance.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM account_balance_sets) TO account_balance_sets.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM address_book) TO address_book.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM address_book_entry) TO address_book_entry.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM contract_result) TO contract_result.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM crypto_transfer) TO crypto_transfer.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM file_data) TO file_data.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM flyway_schema_history) TO flyway_schema_history.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM live_hash) TO live_hash.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM non_fee_transfer) TO non_fee_transfer.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM record_file) TO record_file.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM t_application_status) TO t_application_status.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM t_entities) TO entity.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM t_entity_types) TO entity_types.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM t_transaction_results) TO transaction_results.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM t_transaction_types) TO transaction_types.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM topic_message) TO topic_message.csv DELIMITER ',' CSV"
#psql -h $OLD_DB_HOST -d $OLD_DB_NAME -p $OLD_DB_PORT -U $OLD_DB_USER -c "\COPY (SELECT * FROM transaction) TO transaction.csv DELIMITER ',' CSV"

# Optionally use https://github.com/timescale/timescaledb-parallel-copy as it's mulithreaded
echo "4. Pg_restore to separate host"
for table in "${!tables[@]}"; do
    echo "Pg_restore table $table to separate host"
    psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY $table FROM $table.csv CSV"
done
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY account_balance FROM account_balance.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY account_balance_sets FROM account_balance_sets.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY address_book FROM address_book.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY address_book_entry FROM address_book_entry.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY contract_result FROM contract_result.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY crypto_transfer FROM crypto_transfer.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY file_data FROM file_data.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY flyway_schema_history FROM flyway_schema_history.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY live_hash FROM live_hash.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY non_fee_transfer FROM non_fee_transfer.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY record_file FROM record_file.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY t_application_status FROM t_application_status.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY entity FROM entity.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY entity_types FROM entity_types.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY transaction_results FROM transaction_results.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY transaction_types FROM transaction_types.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY topic_message FROM topic_message.csv CSV"
#psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "\COPY transaction FROM transaction.csv CSV"

# rename entities table
psql -h $NEW_DB_HOST -d $NEW_DB_NAME -p $NEW_DB_PORT -U $NEW_DB_USER -c "alter table t_entities rename to entity"

# leave index creation to migration 2.0
echo "5. Display migration time"
