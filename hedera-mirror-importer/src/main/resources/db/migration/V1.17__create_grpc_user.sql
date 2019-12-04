--
-- Creates user for grpc server
--

CREATE USER ${grpc-user} WITH
    LOGIN
    NOCREATEDB
    NOCREATEROLE
    NOINHERIT
    NOREPLICATION
    PASSWORD '${grpc-password}';


GRANT CONNECT ON DATABASE ${db-name} to ${grpc-user};

GRANT SELECT ON ALL TABLES IN SCHEMA public to ${grpc-user}

