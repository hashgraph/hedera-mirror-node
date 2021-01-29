-- Change the values below if you are not installing via Docker

\set db_name 'mirror_node'
\set db_create 'create database mirror_node'
\set db_user 'mirror_node'
\set db_password 'mirror_node_pass'
\set db_owner 'mirror_node'
\set grpc_user 'mirror_grpc'
\set grpc_password 'mirror_grpc_pass'
\set rosetta_user 'mirror_rosetta'
\set rosetta_password 'mirror_rosetta_pass'

create function init_create_user(user_name text, user_pass text) returns void as
$$
begin
    if not exists(select from pg_catalog.pg_roles where rolname = user_name) then
        execute format(
                'create user %I with
                    createrole
                    password %L'
            , user_name
            , user_pass
            );
        raise notice 'Created user "%" with create role', user_name;
    else
        raise notice 'User "%" already exists, not creating it', user_name;
    end if;
end
$$ language plpgsql;

create function init_user(user_name text, user_pass text) returns void as
$$
begin
    if not exists(select from pg_catalog.pg_roles where rolname = user_name) then
        execute format(
                'create user %I with
                    login
                    password %L'
            , user_name
            , user_pass
            );
        raise notice 'Created user "%"', user_name;
    else
        raise notice 'User "%" already exists, not creating it', user_name;
    end if;
end
$$ language plpgsql;

select :'db_create'
where not exists(select from pg_database where datname = :'db_name')
\gexec

select init_create_user(:'db_user', :'db_password');
select init_user(:'grpc_user', :'grpc_password');
select init_user(:'rosetta_user', :'rosetta_password');

grant connect on database :db_name to :grpc_user;

grant connect on database :db_name to :rosetta_user;

\c :db_name

alter default privileges in schema public grant select on tables to :grpc_user;

grant select on all tables in schema public to :grpc_user;

alter default privileges in schema public grant select on tables to :rosetta_user;

grant select on all tables in schema public to :rosetta_user;
