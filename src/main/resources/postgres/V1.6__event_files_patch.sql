GRANT ALL ON FUNCTION f_file_create TO :db_user;

CREATE SEQUENCE s_event_files_seq;

GRANT SELECT ON SEQUENCE s_event_files_seq TO :db_user;

CREATE TABLE t_event_files (
  id                   BIGINT PRIMARY KEY NOT NULL DEFAULT nextval('s_event_files_seq')
  ,name                VARCHAR(250) NOT NULL
	,load_start          BIGINT
	,load_end            BIGINT
	,file_hash           VARCHAR(96)
	,prev_hash           VARCHAR(96)
);

GRANT ALL ON TABLE t_event_files TO :db_user;

ALTER TABLE t_events ADD COLUMN fk_event_file_id BIGINT NOT NULL;

ALTER TABLE t_events ADD CONSTRAINT fk_event_file_id FOREIGN KEY (fk_event_file_id) REFERENCES t_event_files (id) ON DELETE CASCADE ON UPDATE CASCADE;

CREATE UNIQUE INDEX idx_t_event_files_unq ON t_event_files (name);

CREATE FUNCTION f_event_file_create(_file_name t_event_files.name%TYPE)
  RETURNS BIGINT AS
$$
DECLARE file_id BIGINT;
BEGIN
  SELECT id
  INTO file_id
  FROM t_event_files
  WHERE name = _file_name;

  IF NOT FOUND THEN
    INSERT INTO t_event_files(name, load_start)
    VALUES (_file_name, extract(epoch from now()))
    RETURNING id INTO file_id;
  ELSE
    file_id = 0;
  END IF;

  RETURN file_id;
END;
$$ LANGUAGE plpgsql
;

GRANT ALL ON FUNCTION f_event_file_create TO :db_user;

CREATE FUNCTION f_event_file_complete (
  _file_id t_event_files.id%TYPE
  ,_file_hash t_event_files.file_hash%TYPE
  ,_prev_hash t_event_files.prev_hash%TYPE
) RETURNS VOID AS $$
BEGIN
  UPDATE t_event_files
  SET load_end = extract(epoch from now())
    ,file_hash = _file_hash
    ,prev_hash = _prev_hash
  WHERE id = _file_id;
END
$$ LANGUAGE plpgsql
;

GRANT ALL ON FUNCTION f_event_file_complete TO :db_user;
