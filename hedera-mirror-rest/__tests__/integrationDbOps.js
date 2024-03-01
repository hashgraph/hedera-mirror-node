/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import {getModuleDirname, isV2Schema} from './testutils';
import {getPoolClass} from '../utils';
import {PostgreSqlContainer} from '@testcontainers/postgresql';

const {db: defaultDbConfig} = config;
const Pool = getPoolClass();

const dbName = 'mirror_node';
const readOnlyUser = 'mirror_rest';
const readOnlyPassword = 'mirror_rest_pass';
const workerId = process.env.JEST_WORKER_ID;
const v1DatabaseImage = 'postgres:14-alpine';
const v2DatabaseImage = 'gcr.io/mirrornode/citus:12.1.1';

const cleanupSql = fs.readFileSync(
  path.join(
    getModuleDirname(import.meta),
    '..',
    '..',
    'hedera-mirror-common',
    'src',
    'test',
    'resources',
    'cleanup.sql'
  ),
  'utf8'
);

const v1SchemaConfigs = {
  baselineVersion: '0',
  locations: '../hedera-mirror-importer/src/main/resources/db/migration/v1',
};
const v2SchemaConfigs = {
  baselineVersion: '1.999.999',
  locations: '../hedera-mirror-importer/src/main/resources/db/migration/v2',
};

const schemaConfigs = isV2Schema() ? v2SchemaConfigs : v1SchemaConfigs;

const dbUrlRegex = /^postgres:\/\/(.*):(.*)@(.*):(\d+)/;

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
  await ownerPool.query(cleanupSql);
};

const createDbContainer = async () => {
  const image = isV2Schema() ? v2DatabaseImage : v1DatabaseImage;
  const initSqlPath = path.join('..', 'hedera-mirror-common', 'src', 'test', 'resources', 'init.sql');
  const initSqlCopy = {
    source: initSqlPath,
    target: '/docker-entrypoint-initdb.d/init.sql',
  };

  const dockerDb = await new PostgreSqlContainer(image)
    .withCopyFilesToContainer([initSqlCopy])
    .withDatabase(dbName)
    .withLabels({
      // used to differentiate between containers so that Jest workers do not use a container that is already in use by another worker.
      workerId: workerId,
      // used to remove the containers after the tests have completed.
      tearDownProcessId: process.env.TEARDOWN_PROCESS_ID,
    })
    .withPassword('mirror_node_pass')
    .withReuse()
    .withUsername('mirror_node')
    .start();
  logger.info(`Started PostgreSQL container with image ${image}`);

  return dockerDb.getConnectionUri();
};

const createPool = async () => {
  const connectionUri = await createDbContainer();
  await flywayMigrate(connectionUri);
  const dbConnectionParams = {
    ...extractDbConnectionParams(connectionUri),
    database: dbName,
    sslmode: 'DISABLE',
  };

  global.ownerPool = new Pool(dbConnectionParams);
  global.pool = new Pool({
    ...dbConnectionParams,
    password: readOnlyPassword,
    user: readOnlyUser,
  });
};

/**
 * Run the SQL (non-java) based migrations stored in the Importer project against the target database.
 */
const flywayMigrate = async (connectionUri) => {
  logger.info(`Using flyway CLI to construct schema for jest worker ${workerId}`);
  const dbConnectionParams = extractDbConnectionParams(connectionUri);
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = path.join('.', 'build', 'flyway');
  const flywayConfigPath = path.join(os.tmpdir(), `config_worker_${workerId}.json`); // store configs in temp dir
  const locations = getMigrationScriptLocation(schemaConfigs.locations);

  const flywayConfig = `{
    "flywayArgs": {
      "baselineOnMigrate": "true",
      "baselineVersion": "${schemaConfigs.baselineVersion}",
      "locations": "filesystem:${locations}",
      "password": "${dbConnectionParams.password}",
      "placeholders.api-password": "${defaultDbConfig.password}",
      "placeholders.api-user": "${defaultDbConfig.user}",
      "placeholders.db-name": "${dbName}",
      "placeholders.db-user": "${dbConnectionParams.user}",
      "placeholders.hashShardCount": 2,
      "placeholders.partitionStartDate": "'1970-01-01'",
      "placeholders.partitionTimeInterval": "'10 years'",
      "placeholders.topicRunningHashV2AddedTimestamp": 0,
      "placeholders.schema": "public",
      "placeholders.shardCount": 2,
      "placeholders.tempSchema": "temporary",
      "target": "latest",
      "url": "jdbc:postgresql://${dbConnectionParams.host}:${dbConnectionParams.port}/${dbName}",
      "user": "${dbConnectionParams.user}"
    },
    "version": "9.22.3",
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

  fs.rmSync(locations, {force: true, recursive: true});
};

const getMigrationScriptLocation = (locations) => {
  // Creating a temp directory for v2, without the repeatable partitioning file.
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), 'migration-scripts-'));
  const ignoredMigrations = ['R__01_temp_tables.sql', 'R__02_temp_table_distribution.sql'];
  logger.info(`Created temp directory for v2 migration scripts - ${dest}`);
  fs.readdirSync(locations)
    .filter((filename) => ignoredMigrations.indexOf(filename) === -1)
    .forEach((filename) => {
      const srcFile = path.join(locations, filename);
      const dstFile = path.join(dest, filename);
      fs.copyFileSync(srcFile, dstFile);
    });

  return dest;
};

export default {
  cleanUp,
  createPool,
};
