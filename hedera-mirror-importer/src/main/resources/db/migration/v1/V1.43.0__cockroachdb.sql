-------------------
-- Change the primary key of schedule from consensus_timestamp to schedule_id
-------------------

alter table if exists custom_fee
    add column if not exists id uuid default uuid_generate_v4() primary key;
