\set db_name hederamirror
\set db_user hederamirror
\set db_password mysecretpassword

\unset version
SELECT version = 1 AS version FROM t_version
\gset

\set ON_ERROR_STOP on

\if :version
	-- database is at version 1, perform the following updates

	UPDATE t_version set version = 2;
\endif
# end of version 1->2  upgrades
