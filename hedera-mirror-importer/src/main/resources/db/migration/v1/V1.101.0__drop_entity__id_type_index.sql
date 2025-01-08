DROP INDEX IF EXISTS entity__id_type;

CREATE INDEX entity_type_id ON entities(type, id);