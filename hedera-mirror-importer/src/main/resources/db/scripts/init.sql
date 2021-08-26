create user mirror_node with login password 'mirror_node_pass';
create database mirror_node;
alter database mirror_node owner to mirror_node;
create role readonly;
create role readwrite;
create user mirror_grpc with login password 'mirror_grpc_pass';
create user mirror_importer with login password 'mirror_importer_pass';
create user mirror_rest with login password 'mirror_rest_pass';
grant readwrite to mirror_importer;
grant readonly to mirror_grpc;
grant readonly to mirror_rest;
\c mirror_node
create schema if not exists mirrornode authorization mirror_node;
grant usage on schema mirrornode to public;
revoke create on schema mirrornode from public;
grant select on database mirror_node to readonly;
grant usage on schema mirrornode to readonly, readwrite;
grant select, insert, update on database mirror_node to readwrite;
