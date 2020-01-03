alter table t_entities drop column topic_valid_start_time;
alter table t_entities add column auto_renew_account_id bigint null;
alter table t_entities add constraint autorenew_account foreign key (auto_renew_account_id) references t_entities(id);
