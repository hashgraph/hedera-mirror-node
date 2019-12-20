#!/usr/bin/env bash
# Runs scalability tests for REST API. Does following in order:
# 1. Generates fake data using Faker
# 2. Loads generated fake data into PostgreSQL database

set -e

# Directory containing this bash file
BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

PG_HOST=127.0.0.1
PG_PORT=5432
PG_USERNAME=mirror_node
PG_PASSWORD=mirror_node_pass
PG_DBNAME=mirror_node
PG_API_USERNAME=mirror_api
PG_API_PASSWORD=mirror_api_pass
FAKER_JAR=${BASE_DIR}/../target/hedera-mirror-faker-*.jar
IMPORT_DIR=
TMP_DIR=$(mktemp -d)

function display_help() {
    echo "Usage: $0 [options...]"
    echo
    echo "    -h, --help                         Prints this help message"
    echo "    -d, --import-dir DIR               Directory containing pre-generated csv files which can be imported"
         "                                       directly. If specified, data generation step will be skipped."
    echo "    -pgh, --pg-host HOSTNAME           Postgres database server host (default: '${PG_HOST}')"
    echo "    -pgp, --pg-port PORT               Postgres database server port (default: '${PG_PORT}'"
    echo "    -pgu, --pg-username USERNAME       Postgres database user name (default: '${PG_USERNAME}')"
    echo "    -pgw, --pg-password PASSWORD       Postgres database user name (default: '${PG_PASSWORD}')"
    echo "    -pgd, --pg-dbname DBNAME           Postgres database name (default: '${PG_DBNAME}')"
    echo "    -j, --jar JAR                      JAR file to run Faker (default: '${FAKER_JAR}')"
    echo ""
}

# Parse command line arguments
while(( "$#" )); do
    case "$1" in
        -h | --help)
            display_help
            exit 0
            ;;
        -d | --import-dir)
            IMPORT_DIR=$2
            shift 2
            ;;
        -pgh | --pg-host)
            PG_HOST=$2
            shift 2
            ;;
        -pgp | --pg-port)
            PG_PORT=$2
            shift 2
            ;;
        -pgu | --pg-username)
            PG_USERNAME=$2
            shift 2
            ;;
        -pgw | --pg-password)
            PG_PASSWORD=$2
            shift 2
            ;;
        -pgd | --pg-dbname)
            PG_DBNAME=$2
            shift 2
            ;;
        --) # end argument parsing
            shift
            break
            ;;
        -*|--*=) # unsupported flags
            echo "Error: Unsupported flag $1" >&2
            exit 1
            ;;
        *)  # No more options
            break
            ;;
    esac
done

export PGPASSWORD=${PG_PASSWORD}

CSV_DIR=""
if [[ "${IMPORT_DIR}" == "" ]]; then
    # Run Faker to generate data
    echo "********************************************"
    echo "Generating fake data files in dir ${TMP_DIR}"
    echo "********************************************"
    CSV_DIR=${TMP_DIR}
    java -jar ${FAKER_JAR} \
        --hedera.mirror.db.host="${PG_HOST}" \
        --hedera.mirror.db.name="${PG_DBNAME}" \
        --hedera.mirror.db.username="${PG_USERNAME}" \
        --hedera.mirror.db.password="${PG_PASSWORD}" \
        --hedera.mirror.db.port="${PG_PORT}" \
        --hedera.mirror.db.apiUsername="${PG_API_USERNAME}" \
        --hedera.mirror.db.apiPassword="${PG_API_PASSWORD}" \
        --faker.output.postgres.csv.outputDir="${CSV_DIR}"
else
    echo "********************************"
    echo "Skipping generation of fake data"
    echo "********************************"
    CSV_DIR=${IMPORT_DIR}
fi
echo
echo

execute_sql_file() {
    psql -h ${PG_HOST} -U ${PG_USERNAME} -d ${PG_DBNAME} -f $1
}

echo "***********************************************"
echo "Loading helper functions into PostgreSQL Server"
echo "***********************************************"
execute_sql_file \
    "${BASE_DIR}/../../hedera-mirror-importer/src/main/resources/db/scripts/manage_constraints_and_indexes.sql"

# Load generate data into Postgres
echo "***********************************"
echo "Loading data into PostgreSQL Server"
echo "***********************************"
# psql variable substitution does not work for \copy command, hence the sed workaround.
sed "s#%%TMP_DIR%%#${CSV_DIR}#g" ${BASE_DIR}/load_data.sql > ${TMP_DIR}/load_data.sql
time execute_sql_file ${TMP_DIR}/load_data.sql
