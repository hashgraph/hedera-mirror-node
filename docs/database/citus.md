# Citus Backup and Restore

This document describes the steps to back up and restore a citus cluster using

- PostgreSQL base backup
- PostgreSQL WAL archiving
- Citus UDF `citus_create_restore_point`
- PostgreSQL point-in-time recovery to a named restore point

Unless otherwise noted, the steps should run on all database nodes and the shell commands and the queries are all run
with `postgres` user. The common exception is any `zfs` command should run on the hosting node with `root` user.

## Prerequisites

- All database servers are configured with archiving on and a no-op archive command. It's required since
  changing archive mode requires server restart while changing archive command only requires configuration reload.
  ```
  archive_mode = on
  archive_command = '/bin/true'
  wait_for_archive = true
  ```

- ZFS as the storage layer. Specifically, ZFS snapshot is used for database base backup since it takes up nearly zero 
  space and can be created instantly due to ZFS's copy-on-write architecture. However, instructions for file level base
  backup is also provided.

## Assumptions

- ZFS file system `postgresql/db` is where PostgreSQL data resides
- `/opt/backup/base` is where a base backup is stored
- `/opt/backup/wal` is where WAL segments backup is stored

## Create a backup

A PostgreSQL database backup is composed of a base backup and the WAL segments to replay the changes from the 
checkpoint at the base backup up until the point in time the database should recover to. Since a citus cluster has
multiple database servers, the only way to recover all nodes to a consistent state is set the recovery target to a named
restore point created by `citus_create_restore_point`

1. Enable WAL archiving to copy WAL segments to `/opt/backup/wal`
   - set `archive_command = 'test ! -f /opt/backup/wal/%f && cp %p /opt/backup/wal/%f'` in postgresql.conf
   - `pg_ctl reload` to reload the configuration

2. Create a base backup.
   - Use ZFS snapshot
     1. Start the backup. Open a psql session to run all commands during the backup.

        | PostgreSQL Version | Command                                                             |
        |--------------------|---------------------------------------------------------------------|
        | <= 14              | `select pg_start_backup('2023-10-02-base', true, false);`           |
        | \>= 15             | `select pg_backup_start(label => '2023-10-02-base', fast => true);` |
        
        Note fast checkpoint is preferred since otherwise the command will wait till the next regular checkpoint
        completes. The downside of fast checkpoint is extra IO incurred by the checkpointing process.

     2. Create a ZFS snapshot using `zfs snapshot postgresql/db@2023-10-02-base`. It's recommended to use the same 
        mark for the backup label and zfs snapshot name.

     3. Stop the backup.

        | PostgreSQL Version | Command                                                   |
        |--------------------|-----------------------------------------------------------|
        | <= 14              | `select * from pg_stop_backup(false, true);`              |
        | \>= 15             | `select * from pg_backup_stop(wait_for_archive => true);` |

        The command will return one row with three values, e.g.,
        ```
            lsn     |                           labelfile                           | spcmapfile
        ------------+---------------------------------------------------------------+------------
         1/9DEBC00  | START WAL LOCATION: 1/90036F8 (file 000000010000000100000009)+|
                    | CHECKPOINT LOCATION: 1/9DEBB38                               +|
                    | BACKUP METHOD: streamed                                      +|
                    | BACKUP FROM: primary                                         +|
                    | START TIME: 2023-10-03 03:45:13 UTC                          +|
                    | LABEL: 2023-10-02-base                                       +|
                    | START TIMELINE: 1                                            +|
                    |                                                               |
        (1 row)
        ```
     4. Save the `labelfile` value from the output of the previous step to `/opt/backup/base/backup_label`.

   - Use `pg_basebackup`
     
     Example command, `pg_basebackup -c fast -Ft -l 2023-10-02-base -P -z -D /opt/backup/base`.

     The command will create three files, the only file needed to restore the database is `base.tar.gz`.
     ```
     -rw-------    1 postgres postgres    240308 Oct  2 17:29 backup_manifest
     -rw-------    1 postgres postgres 909736907 Oct  2 17:29 base.tar.gz
     -rw-------    1 postgres postgres     18290 Oct  2 17:29 pg_wal.tar.gz
     ```

3. Create a named restore point. Run the following command on the coordinator in the `mirror_node` database,

   `select citus_create_restore_point('2023-10-02-restore-point');`

4. Force a WAL segment switch using `select pg_switch_wal()` to ensure the WAL segments in the backup contain the
   named restore point.

5. Disable WAL archiving.
   - Set `archive_command = '/bin/true'` in postgresql.conf
   - `pg_ctl reload` to reload the configuration

## Restore from a backup

1. Make sure the database server is stopped.

2. Restore the base backup.
   - Use ZFS snapshot
     1. `zfs rollback postgresql/db@2023-10-02-base`. Note the command will fail if there are later snapshots. To keep
        the later snapshots, instead, use `zfs send postgresql/db@2023-10-02-base | zfs receive postgresql/restore`
        to restore the ZFS snapshot to a different file system
     2. Remove all existing WAL segments from the snapshot, `rm -fr postgresql/db/data/pg_wal/*`.
     3. Copy the `backup_label` to `postgresql/db/data/`

   - Use base backup created by `pg_basebackup`
     1. Remove existing data, e.g., `rm -fr /var/lib/postgresql/data/*`
     2. Restore the base backup, `tar czvf /opt/backup/base/base.tar.gz -C /var/lib/postgresql/data`

3. Configure recovery.
   - Set `archive_command = '/bin/true'`
   - Add or update the following in postgresql.conf
     ```
     recovery_target_name = '2023-10-02-restore-point'
     restore_command = 'cp /opt/backup/wal/%f %p'
     ```
   - Touch a signal file `recovery.signal` in PostgreSQL data directory

4. Make sure all files have correct owner and access rights, and fix it with `chown` and `chmod` if necessary.

5. Start the database server and monitor the log for recovery progress. The following log indicates a successful recovery
   to the set named restore point.
   ```
   2023-10-03 03:54:51.194 UTC [21] LOG:  database system was interrupted; last known up at 2023-10-03 03:45:12 UTC
   cp: can't stat '/export/wal/00000002.history': No such file or directory
   2023-10-03 03:54:51.224 UTC [21] LOG:  starting point-in-time recovery to "2023-10-02-restore-point"
   2023-10-03 03:54:51.371 UTC [21] LOG:  restored log file "000000010000000100000009" from archive
   2023-10-03 03:54:51.412 UTC [21] LOG:  redo starts at 1/90036F8
   2023-10-03 03:54:51.676 UTC [21] LOG:  restored log file "00000001000000010000000A" from archive
   ...
   2023-10-03 03:55:30.765 UTC [21] LOG:  restored log file "00000001000000010000006A" from archive
   2023-10-03 03:55:30.840 UTC [21] LOG:  recovery stopping at restore point "2023-10-02-restore-point", time 2023-10-03 03:46:47.674119+00
   2023-10-03 03:55:30.840 UTC [21] LOG:  pausing at the end of recovery
   2023-10-03 03:55:30.840 UTC [21] HINT:  Execute pg_wal_replay_resume() to promote.
   ```

6. When all nodes reach the set named restore point, validate the data consistency if needed.

7. End the recovery mode with `select pg_wal_replay_resume()`. The cluster should have been successfully restored to the
   set consistent restore point and begin to accept all types of transactions.