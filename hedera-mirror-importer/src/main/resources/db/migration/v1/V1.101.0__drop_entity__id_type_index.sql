DROP INDEX IF EXISTS entity__id_type;

CREATE INDEX entity__type_id ON entities(type, id);