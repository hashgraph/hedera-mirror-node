alter table contract add column decline_reward boolean not null default false;
alter table contract add column staked_account_id bigint null;
alter table contract add column staked_node_id bigint null;
alter table contract add column stake_period_start bigint null;

alter table contract_history add column decline_reward boolean not null default false;
alter table contract_history add column staked_account_id bigint null;
alter table contract_history add column staked_node_id bigint null;
alter table contract_history add column stake_period_start bigint null;

alter table entity add column decline_reward boolean not null default false;
alter table entity add column staked_account_id bigint null;
alter table entity add column staked_node_id bigint null;
alter table entity add column stake_period_start bigint null;

alter table entity_history add column decline_reward boolean not null default false;
alter table entity_history add column staked_account_id bigint null;
alter table entity_history add column staked_node_id bigint null;
alter table entity_history add column stake_period_start bigint null;
