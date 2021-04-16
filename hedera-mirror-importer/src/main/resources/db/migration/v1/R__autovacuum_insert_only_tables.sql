-------------------
-- autovacuum insert-only tables more frequently to ensure most pages are visible for index-only scans
-- prior to postgresql 13, this can be achieved by configuring a more aggressive anti-wraparound autovacuum
-------------------

alter table if exists crypto_transfer set (
    autovacuum_freeze_max_age = ${autovacuumFreezeMaxAgeInsertOnly},
    autovacuum_freeze_table_age = ${autovacuumFreezeMaxAgeInsertOnly},
    autovacuum_freeze_min_age = 0,
    log_autovacuum_min_duration = 0
    );

alter table if exists token_transfer set (
    autovacuum_freeze_max_age = ${autovacuumFreezeMaxAgeInsertOnly},
    autovacuum_freeze_table_age = ${autovacuumFreezeMaxAgeInsertOnly},
    autovacuum_freeze_min_age = 0,
    log_autovacuum_min_duration = 0
    );

alter table if exists transaction set (
    autovacuum_freeze_max_age = ${autovacuumFreezeMaxAgeInsertOnly},
    autovacuum_freeze_table_age = ${autovacuumFreezeMaxAgeInsertOnly},
    autovacuum_freeze_min_age = 0,
    log_autovacuum_min_duration = 0
    );
