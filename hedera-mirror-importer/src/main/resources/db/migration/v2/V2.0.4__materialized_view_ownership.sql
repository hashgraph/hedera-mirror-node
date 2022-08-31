-- necessary to make changing materialized view owner work
grant create on schema ${db-schema} to readwrite;
grant readwrite to CURRENT_USER;

alter materialized view if exists entity_state_start owner to readwrite;

-- revert the permission / role changes
revoke readwrite from CURRENT_USER;
revoke create on schema ${db-schema} from readwrite;
