-- Change the values below if you are not installing via Docker (environment variable values come from .env file)

-- name of the database
\set db_name 'mirror_node'
--username
\set db_user 'mirror_node'
--user password
\set db_password 'mirror_node_pass'
\set db_owner 'mirror_node'

\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'

create user :db_user with login createrole password :'db_password';

create database :db_name with owner :db_owner;

create user :grpc_user with login createrole password :'grpc_password';

grant connect on database :db_name to :grpc_user;

\c :db_name

alter default privileges in schema public
    grant select on tables to :grpc_user;
