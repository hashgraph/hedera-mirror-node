/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {execSync} from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';

import config from '../config';
import {getDatabaseName} from './globalSetup';
import {getModuleDirname, isV2Schema} from './testutils';
import {getPoolClass} from '../utils';

const {db: defaultDbConfig} = config;
const Pool = await getPoolClass();

const cleanupSql = {
  v1: fs.readFileSync(
    path.join(
      getModuleDirname(import.meta),
      '..',
      '..',
      'hedera-mirror-importer',
      'src',
      'test',
      'resources',
      'db',
      'scripts',
      'cleanup.sql'
    ),
    'utf8'
  ),
  v2: null,
};

const v1SchemaConfigs = {
  baselineVersion: '0',
  locations: '../hedera-mirror-importer/src/main/resources/db/migration/v1',
};
const v2SchemaConfigs = {
  baselineVersion: '1.999.999',
  locations: '../hedera-mirror-importer/src/main/resources/db/migration/v2',
};

const schemaConfigs = isV2Schema() ? v2SchemaConfigs : v1SchemaConfigs;

const dbUrlRegex = /^postgresql:\/\/(.*):(.*)@(.*):(\d+)/;

const extractDbConnectionParams = (url) => {
  const found = url.match(dbUrlRegex);
  return {
    user: found[1],
    password: found[2],
    host: found[3],
    port: found[4],
  };
};

const cleanUp = async () => {
  const cleanupSql = await getCleanupSql();
  await pool.query(cleanupSql);
};

const createPool = () => {
  const database = getDatabaseName();
  const dbConnectionParams = extractDbConnectionParams(process.env.INTEGRATION_DATABASE_URL);
  global.pool = new Pool({
    ...dbConnectionParams,
    database,
    sslmode: 'DISABLE',
  });
};

/**
 * Run the SQL (non-java) based migrations stored in the Importer project against the target database.
 */
const flywayMigrate = async () => {
  if (isDbMigrated()) {
    logger.info('Skipping flyway migration since db is already migrated');
    return;
  }

  const workerId = process.env.JEST_WORKER_ID;
  logger.info(`Using flyway CLI to construct schema for jest worker ${workerId}`);
  const dbConnectionParams = extractDbConnectionParams(process.env.INTEGRATION_DATABASE_URL);
  const apiUsername = `${defaultDbConfig.username}_${workerId}`;
  const dbName = getDatabaseName();
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = path.join('.', 'build', 'flyway');
  const flywayConfigPath = path.join(os.tmpdir(), `config_worker_${workerId}.json`); // store configs in temp dir
  const locations = getMigrationScriptLocation(schemaConfigs.locations);

  const flywayConfig = `{
    "flywayArgs": {
      "baselineVersion": "${schemaConfigs.baselineVersion}",
      "locations": "filesystem:${locations}",
      "password": "${dbConnectionParams.password}",
      "placeholders.api-password": "${defaultDbConfig.password}",
      "placeholders.api-user": "${apiUsername}",
      "placeholders.autovacuumFreezeMaxAgeInsertOnly": 100000,
      "placeholders.autovacuumVacuumInsertThresholdCryptoTransfer": 18000000,
      "placeholders.autovacuumVacuumInsertThresholdTokenTransfer": 2000,
      "placeholders.autovacuumVacuumInsertThresholdTransaction": 6000000,
      "placeholders.chunkIdInterval": 10000,
      "placeholders.chunkTimeInterval": 604800000000000,
      "placeholders.compressionAge": 9007199254740991,
      "placeholders.cronSchedule": "'@daily'",
      "placeholders.db-name": "${dbName}",
      "placeholders.db-user": "${dbConnectionParams.user}",
      "placeholders.partitionIdInterval": "'1000000'",
      "placeholders.partitionStartDate": "'0 days'",
      "placeholders.partitionTimeInterval": "'1 year'",
      "placeholders.topicRunningHashV2AddedTimestamp": 0,
      "placeholders.schema": "public",
      "placeholders.shardCount": 2,
      "target": "latest",
      "url": "jdbc:postgresql://${dbConnectionParams.host}:${dbConnectionParams.port}/${dbName}",
      "user": "${dbConnectionParams.user}"
    },
    "version": "9.16.3",
    "downloads": {
      "storageDirectory": "${flywayDataPath}"
    }
  }`;

  fs.mkdirSync(flywayDataPath, {recursive: true});
  fs.writeFileSync(flywayConfigPath, flywayConfig);
  logger.info(`Added ${flywayConfigPath} to file system for flyway CLI`);

  const maxRetries = 10;
  let retries = maxRetries;
  const retryMsDelay = 2000;

  while (retries-- > 0) {
    try {
      execSync(`node ${exePath} -c ${flywayConfigPath} migrate`);
      logger.info('Successfully executed all Flyway migrations');
      break;
    } catch (e) {
      logger.warn(`Error running flyway during attempt #${maxRetries - retries}: ${e}`);
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  if (isV2Schema()) {
    fs.rmSync(locations, {force: true, recursive: true});
  }

  markDbMigrated();
};

const getCleanupSql = async () => {
  if (!isV2Schema()) {
    return cleanupSql.v1;
  }

  if (cleanupSql.v2) {
    return cleanupSql.v2;
  }

  // The query returns the tables without partitions or the parent tables of the partitions. This is to reduce the
  // exact amount of time caused by trying to delete from partitions. The cleanup sql for v2 is generated once for
  // each jest worker, it's done this way because the query to find the correct table names is also slow.
  const {rows} = await pool.queryQuietly(`
      select table_name
      from information_schema.tables
               left join time_partitions on partition::text = table_name::text
      where table_schema = 'public'
        and table_type <> 'VIEW'
        and table_name !~ '.*(flyway|transaction_type|citus_|_\\d+).*'
        and partition is null
      order by table_name`);
  cleanupSql.v2 = rows
    .map(
      (row) => `delete
                                     from ${row.table_name};`
    )
    .join('\n');
  return cleanupSql.v2;
};

const getMigratedFilename = () => path.join(process.env.MIGRATION_TMP_DIR, `.${process.env.JEST_WORKER_ID}.migrated`);

const getMigrationScriptLocation = (locations) => {
  if (!isV2Schema()) {
    return locations;
  }

  // Creating a temp directory for v2, without the repeatable partitioning file.
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), 'migration-scripts-'));
  logger.info(`Created temp directory for v2 migration scripts - ${dest}`);
  fs.readdirSync(locations)
    .filter((filename) => filename !== 'R__maintain_partitions.sql' && filename !== 'R__create_partitions.sql')
    .forEach((filename) => {
      const srcFile = path.join(locations, filename);
      const dstFile = path.join(dest, filename);
      fs.copyFileSync(srcFile, dstFile);
    });

  return dest;
};

const isDbMigrated = () => fs.existsSync(getMigratedFilename());

const markDbMigrated = () => fs.closeSync(fs.openSync(getMigratedFilename(), 'w'));

export default {
  cleanUp,
  createPool,
  flywayMigrate,
};
