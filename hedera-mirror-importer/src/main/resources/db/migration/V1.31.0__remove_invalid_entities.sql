-------------------
-- Fix entity type mismatches
-------------------

-- Backup t_entities prior to type mismatch fix
create table t_entities_archive as table t_entities;

--- Delete entities above the max entity id witnessed by transactions
delete from t_entities where id > (select max(entity_id) from transaction where entity_id is not null);

