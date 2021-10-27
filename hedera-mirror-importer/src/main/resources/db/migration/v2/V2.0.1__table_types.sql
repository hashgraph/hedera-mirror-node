-------------------
-- Create distributed tables for tables that can be distributed around an entity.
-- Colocate where possible with matching entity
-- Create reference tables for tables used only to describe with rare insert/updates
-------------------

-- reference tables
-- these rarely get updated, and will have a full copy located on each worker node
select create_reference_table('t_entity_types');
select create_reference_table('t_transaction_results');
select create_reference_table('t_transaction_types');
