-- HAPI changes the max symbol size to 100 characters, apply the same to db table
alter table if exists token alter column symbol TYPE character varying(100);
