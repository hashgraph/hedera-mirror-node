-- Remove Events
drop table if exists t_events;
drop sequence if exists s_events_id_seq;

-- Remove unused components
drop table if exists entity_types;
drop view if exists v_entities;
drop sequence if exists s_account_balances_seq;
