#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "mirror_node" --dbname "mirror_node" <<-EOSQL

    create user mirror_grpc with password 'mirror_grpc_pass';
    grant connect on database mirror_node to mirror_grpc;
    alter default privileges in schema public grant select on tables to mirror_grpc;
    grant select on all tables in schema public to mirror_grpc;

EOSQL
