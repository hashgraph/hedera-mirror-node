-- Script to setup PostgresSQL initially when not using docker-compose. See docs/configuration.md
-- This script is to set up only the main user and db, which in case of docker are automatically set up via
-- POSTGRES_DB, POSTGRES_PASSWORD, and POSTGRES_USER environment configuration.

-- name of the database
\set db_name 'mirror_node'
--username
\set db_user 'mirror_node'
--user password
\set db_password 'mirror_node_pass'
\set db_owner 'mirror_node'

create user :db_user with login createrole password :'db_password';

create database :db_name with owner :db_owner;

\c :db_name
