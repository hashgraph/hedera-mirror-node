-------------------
-- Fill missing entity created_timestamp and modified_timestamp
-------------------

with entity_timestamp as (
    select entity_id, min(consensus_ns) created_timestamp, max(consensus_ns) modified_timestamp
    from transaction
    where result = 22
      and entity_id is not null
      and type in (8, 9, 11, 12, 15, 17, 18, 19, 22, 24, 25, 26, 29, 35, 36, 42)
    group by entity_id
)
update entity
set created_timestamp = entity_timestamp.created_timestamp,
    modified_timestamp = entity_timestamp.modified_timestamp
from entity_timestamp
where entity.id = entity_timestamp.entity_id;
