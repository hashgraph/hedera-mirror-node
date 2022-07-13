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

'use strict';

import crypto from 'crypto';
import {execSync} from 'child_process';
import fs from 'fs';
import log4js from 'log4js';
import path from 'path';
import {GenericContainer} from 'testcontainers';
import {db as defaultDbConfig} from '../config';
import {isDockerInstalled} from './integrationUtils';
import {getPoolClass} from '../utils';
import os from 'os';

const logger = log4js.getLogger();

const Pool = getPoolClass();

let oldPool;

defaultDbConfig.name = process.env.POSTGRES_DB || 'mirror_node_integration';
const dbAdminUser = process.env.POSTGRES_USER || `${defaultDbConfig.username}_admin`;
const dbAdminPassword = process.env.POSTGRES_PASSWORD || crypto.randomBytes(16).toString('hex');

const v1SchemaConfigs = {
  docker: {
    imageName: 'postgres',
    tagName: '14-alpine',
  },
  flyway: {
    baselineVersion: '0',
    locations: 'hedera-mirror-importer/src/main/resources/db/migration/v1',
  },
};
const v2SchemaConfigs = {
  docker: {
    imageName: 'citusdata/citus',
    tagName: '10.2.2-alpine',
  },
  flyway: {
    baselineVersion: '1.999.999',
    locations: 'hedera-mirror-importer/src/main/resources/db/migration/v2',
  },
};

// if v2 schema is set in env use it, else default to v1
const schemaConfigs = process.env.MIRROR_NODE_SCHEMA === 'v2' ? v2SchemaConfigs : v1SchemaConfigs;

const getConnection = (dbSessionConfig) => {
  logger.info(
    `sqlConnection will use postgresql://${dbSessionConfig.host}:${dbSessionConfig.port}/${dbSessionConfig.name}?sslmode=${dbSessionConfig.sslMode}`
  );
  const sqlConnection = new Pool({
    user: dbAdminUser,
    host: dbSessionConfig.host,
    database: dbSessionConfig.name,
    password: dbAdminPassword,
    port: dbSessionConfig.port,
    sslmode: dbSessionConfig.sslMode,
  });

  // Until "server", "pool" and everything else is made non-static...
  oldPool = global.pool;
  global.pool = sqlConnection;

  return sqlConnection;
};

/**
 * Instantiate sqlConnection by either pointing at a DB specified by environment variables or instantiating a
 * testContainers/dockerized postgresql instance.
 * Returns a dbConfig object for db state orchestration by test classes
 * @return {Promise<Object>} {dbSessionConfig, dockerContainer, sqlConnection} Db session details, dockerContainerInstance and sql connection
 */
const instantiateDatabase = async () => {
  if (!(await isDockerInstalled())) {
    throw new Error('Docker not found');
  }

  const image = `${schemaConfigs.docker.imageName}:${schemaConfigs.docker.tagName}`;
  logger.info(`Starting PostgreSQL docker container with image ${image}`);
  const dbSessionConfig = {...defaultDbConfig};
  const dockerDb = await new GenericContainer(image)
    .withEnv('POSTGRES_DB', dbSessionConfig.name)
    .withEnv('POSTGRES_USER', dbAdminUser)
    .withEnv('POSTGRES_PASSWORD', dbAdminPassword)
    .withExposedPorts(dbSessionConfig.port)
    .start();
  dbSessionConfig.port = dockerDb.getMappedPort(defaultDbConfig.port);
  dbSessionConfig.host = dockerDb.getHost();
  logger.info(`Started dockerized PostgreSQL at ${dbSessionConfig.host}:${dbSessionConfig.port}`);

  return flywayMigrate(dbSessionConfig).then(() => {
    return {
      dbSessionConfig: dbSessionConfig,
      dockerContainer: dockerDb,
      sqlConnection: getConnection(dbSessionConfig),
    };
  });
};

/**
 * Run the SQL (non-java) based migrations stored in the Importer project against the target database.
 */
const flywayMigrate = async (dbSessionConfig) => {
  logger.info('Using flyway CLI to construct schema');
  logger.info(
    `flywayMigrate will connect using postgresql://${dbSessionConfig.host}:${dbSessionConfig.port}/${dbSessionConfig.name}`
  );
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = '.node-flywaydb';
  const flywayConfigPath = path.join(os.tmpdir(), `config_${dbSessionConfig.port}.json`); // store configs in temp dir
  const locations = path.join('..', schemaConfigs.flyway.locations);
  const flywayConfig = `{
    "flywayArgs": {
      "baselineVersion": "${schemaConfigs.flyway.baselineVersion}",
      "locations": "filesystem:${locations}",
      "password": "${dbAdminPassword}",
      "placeholders.api-password": "${dbSessionConfig.password}",
      "placeholders.api-user": "${dbSessionConfig.username}",
      "placeholders.autovacuumFreezeMaxAgeInsertOnly": 100000,
      "placeholders.autovacuumVacuumInsertThresholdCryptoTransfer": 18000000,
      "placeholders.autovacuumVacuumInsertThresholdTokenTransfer": 2000,
      "placeholders.autovacuumVacuumInsertThresholdTransaction": 6000000,
      "placeholders.chunkIdInterval": 10000,
      "placeholders.chunkTimeInterval": 604800000000000,
      "placeholders.compressionAge": 9007199254740991,
      "placeholders.db-name": "${dbSessionConfig.name}",
      "placeholders.db-user": "${dbAdminUser}",
      "placeholders.topicRunningHashV2AddedTimestamp": 0,
      "target": "latest",
      "url": "jdbc:postgresql://${dbSessionConfig.host}:${dbSessionConfig.port}/${dbSessionConfig.name}",
      "user": "${dbAdminUser}"
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
      execSync(`node ${exePath} -c ${flywayConfigPath} clean`, {stdio: 'inherit'});
    } catch (e) {
      logger.debug(`Error running flyway cleanup, error: ${e}. Retries left ${retries}. Waiting 2s before retrying.`);
      await new Promise((resolve) => setTimeout(resolve, retryMsDelay));
    }
  }

  execSync(`node ${exePath} -c ${flywayConfigPath} migrate`, {stdio: 'inherit'});
};

const closeConnection = async (dbConfig) => {
  if (dbConfig.sqlConnection) {
    await dbConfig.sqlConnection.end();
  }
  if (dbConfig.dockerContainer) {
    await dbConfig.dockerContainer.stop();
  }
  if (oldPool) {
    global.pool = oldPool;
    oldPool = null;
  }
};

const cleanupSql = fs.readFileSync(
  path.join(
    __dirname,
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

const cleanUp = async (sqlConnection) => {
  if (sqlConnection) {
    await sqlConnection.query(cleanupSql);
  }
};

const runSqlQuery = async (sqlConnection, query, params) => {
  return sqlConnection.query(query, params);
};

export default {
  cleanUp,
  closeConnection,
  getConnection,
  instantiateDatabase,
  runSqlQuery,
};
