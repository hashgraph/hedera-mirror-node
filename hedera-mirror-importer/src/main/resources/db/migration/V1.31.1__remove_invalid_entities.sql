-------------------
-- Fix entity type mismatches
-------------------

--- Delete entities above the max entity id of successful transactions stored
delete from t_entities where id > (select max(entity_id) from transaction where entity_id is not null and result = 22);

