
CREATE TABLE t_application_status (
  status_name VARCHAR(40)
  ,status_code VARCHAR(6)
  ,status_value VARCHAR(100)
);

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded record file name', 'LVDRF');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded record file hash', 'LVDRFH');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded balance file name', 'LVDBF');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded event file name', 'LVDEF');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Last valid downloaded event file hash', 'LVDEFH');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Event hash mismatch bypass until after', 'EHMBUA');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Record hash mismatch bypass until after', 'RHMBUA');

INSERT INTO t_application_status (status_name, status_code) VALUES ('Last processed record hash', 'LPRH');
INSERT INTO t_application_status (status_name, status_code) VALUES ('Last processed event hash', 'LPEH');
