-------------------
-- alter tables by removing domains
-- update to custom schema
-------------------

\set newSchema mirrornode
-- Update schema from public to custom schema e.g mirrornode
alter table flyway_schema_history
    set schema :newSchema;

-- update sequence start values
select setval('address_book_entry_id_seq', (select max(id) from address_book_entry));
