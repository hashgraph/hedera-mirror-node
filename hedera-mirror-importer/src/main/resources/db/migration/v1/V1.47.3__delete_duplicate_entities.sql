-- Remove duplicate entities from entity and contract tables.

delete
from entity
where id in (select id from contract);
