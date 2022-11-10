/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import {execSync} from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';

import config from '../config';
import {getDatabaseName} from './globalSetup';
import {getModuleDirname} from './testutils';
import {getPoolClass} from '../utils';

const {db: defaultDbConfig} = config;
const Pool = await getPoolClass();

const v1SchemaConfigs = {
  baselineVersion: '0',
  locations: 'hedera-mirror-importer/src/main/resources/db/migration/v1',
};
const v2SchemaConfigs = {
  baselineVersion: '1.999.999',
  locations: 'hedera-mirror-importer/src/main/resources/db/migration/v2',
};

// if v2 schema is set in env use it, else default to v1
const schemaConfigs = process.env.MIRROR_NODE_SCHEMA === 'v2' ? v2SchemaConfigs : v1SchemaConfigs;

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
  const workerId = process.env.JEST_WORKER_ID;
  logger.info(`Using flyway CLI to construct schema for jest worker ${workerId}`);
  const dbConnectionParams = extractDbConnectionParams(process.env.INTEGRATION_DATABASE_URL);
  const apiUsername = `${defaultDbConfig.username}_${workerId}`;
  const dbName = getDatabaseName();
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = '.node-flywaydb';
  const flywayConfigPath = path.join(os.tmpdir(), `config_worker_${workerId}.json`); // store configs in temp dir
  const scriptLocation = path.join('..', schemaConfigs.locations);
  const locations = process.env.MIRROR_NODE_SCHEMA === 'v2' ? V2CreateTempFolder(scriptLocation) : scriptLocation;

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
      "placeholders.db-name": "${dbName}",
      "placeholders.db-user": "${dbConnectionParams.user}",
      "placeholders.partitionIdInterval":"'5000'",
      "placeholders.partitionStartDate": "'3 years'",
      "placeholders.partitionTimeInterval":"'1 year'",
      "placeholders.cronSchedule": "'@daily'",
      "placeholders.topicRunningHashV2AddedTimestamp": 0,
      "target": "latest",
      "url": "jdbc:postgresql://${dbConnectionParams.host}:${dbConnectionParams.port}/${dbName}",
      "user": "${dbConnectionParams.user}"
    },
    "version": "8.5.9",
    "downloads": {
      "storageDirectory": "${flywayDataPath}"
    }
  }`;

  fs.mkdirSync(flywayDataPath, {recursive: true});
  fs.writeFileSync(flywayConfigPath, flywayConfig);
  logger.info(`Added ${flywayConfigPath} to file system for flyway CLI`);

  // retry logic on flyway info to ensure flyway is downloaded
  let retries = 3;
  const retryMsDelay = 2000;
  while (retries > 0) {
    retries--;
    try {
      execSync(`node ${exePath} -c ${flywayConfigPath} info`, {stdio: 'ignore'});
    } catch (e) {
      logger.debug(`Error running flyway info, error: ${e}. Retries left ${retries}. Waiting 2s before retrying.`);
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  execSync(`node ${exePath} -c ${flywayConfigPath} migrate`, {stdio: 'inherit'});
};

function V2CreateTempFolder(locations) {
  const destination = path.join(os.tmpdir(), 'temp');
  // Creating a temp folder without the repeatable partitioning file.
  if (!fs.existsSync(destination)) {
    fs.mkdirSync(destination);
    fs.readdirSync(locations).forEach((file) => {
      const destFile = path.join(destination, file);
      const srcFile = path.join(locations, file);
      const excludeFile1 = path.join(destination, 'R__maintain_partitions.sql');
      const excludeFile2 = path.join(destination, 'R__create_partitions.sql');
      if (destFile !== excludeFile1 && destFile !== excludeFile2) {
        fs.copyFileSync(srcFile, destFile);
      }
    });
  }
  return destination;
}

const cleanupSql = fs.readFileSync(
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
);

const cleanUp = async () => pool.query(cleanupSql);

export default {
  cleanUp,
  createPool,
  flywayMigrate,
};
