-------------------
-- alter tables by removing domains
-- update to custom schema
-------------------

\set newSchema mirrornode
-- Update schema from public to custom schema e.g mirrornode
alter table flyway_schema_history
    set schema :newSchema;
