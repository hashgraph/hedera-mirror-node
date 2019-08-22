-- Change the values below if you are not installing via Docker (environment variable values come from .env file)

-- name of the database
\set db_name 'hedera_mirror_node__performance_test'
--username
\set db_user 'hedera_mirror_node__performance_test'
--user password
\set db_password 'hedera_mirror_node__performance_test'
--owner of the database (usually postgres)
\set db_owner 'postgres'
\set api_user 'hedera_mirror_node_api__performance_test'
--user password
\set api_password 'hedera_mirror_node_api__performance_test'

CREATE DATABASE :db_name
    WITH
    OWNER = :db_owner
    CONNECTION LIMIT = -1;

CREATE USER :db_user WITH
    LOGIN
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    CONNECTION LIMIT -1
    PASSWORD ':db_password';

CREATE USER :api_user WITH
    LOGIN
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    CONNECTION LIMIT -1
    PASSWORD ':api_password';

\c :db_name
