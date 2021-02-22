/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const {exec} = require('child_process');
const fs = require('fs');
const log4js = require('log4js');
const path = require('path');
const SqlConnectionPool = require('pg').Pool;
const {GenericContainer} = require('testcontainers');
const {db: dbConfig} = require('../config');
const {isDockerInstalled} = require('./integrationUtils');
const {randomString} = require('../utils');

const logger = log4js.getLogger();

let oldPool;
let dockerDb;
let sqlConnection;

dbConfig.name = process.env.POSTGRES_DB || 'mirror_node_integration';
const dbAdminUser = process.env.POSTGRES_USER || `${dbConfig.username}_admin`;
const dbAdminPassword = process.env.POSTGRES_PASSWORD || randomString(16);

const v1SchemaConfigs = {
  docker: {
    imageName: 'postgres',
    tagName: '9.6-alpine',
  },
  flyway: {
    baselineVersion: '0',
    locations: 'hedera-mirror-importer/src/main/resources/db/migration/v1',
  },
};
const v2SchemaConfigs = {
  docker: {
    imageName: 'timescaledev/timescaledb-ha',
    tagName: 'pg12.5-ts2.0.0-p0',
  },
  flyway: {
    baselineVersion: '1.999.999',
    locations: 'hedera-mirror-importer/src/main/resources/db/migration/v2',
  },
};

// if v2 schema is set in env use it, else default to v1
const schemaConfigs = process.env.MIRROR_NODE_INT_DB === 'v2' ? v2SchemaConfigs : v1SchemaConfigs;

/**
 * Instantiate sqlConnection by either pointing at a DB specified by environment variables or instantiating a
 * testContainers/dockerized postgresql instance.
 */
const instantiateDatabase = async () => {
  if (!process.env.CIRCLECI && !process.env.CI_CONTAINERS) {
    if (!(await isDockerInstalled())) {
      throw new Error('Docker not found');
    }

    const image = `${schemaConfigs.docker.imageName}:${schemaConfigs.docker.tagName}`;
    logger.info(`Starting PostgreSQL docker container with image ${image}`);
    dockerDb = await new GenericContainer(image)
      .withEnv('POSTGRES_DB', dbConfig.name)
      .withEnv('POSTGRES_USER', dbAdminUser)
      .withEnv('POSTGRES_PASSWORD', dbAdminPassword)
      .withExposedPorts(dbConfig.port)
      .start();
    dbConfig.port = dockerDb.getMappedPort(dbConfig.port);
    dbConfig.host = dockerDb.getHost();
    logger.info('Started dockerized PostgreSQL');
  }

  logger.info(`sqlConnection will use postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.name}`);
  sqlConnection = new SqlConnectionPool({
    user: dbAdminUser,
    host: dbConfig.host,
    database: dbConfig.name,
    password: dbAdminPassword,
    port: dbConfig.port,
  });

  // Until "server", "pool" and everything else is made non-static...
  oldPool = global.pool;
  global.pool = sqlConnection;

  await flywayMigrate();

  return sqlConnection;
};

/**
 * Run the sql (non-java) based migrations stored in the importer project against the target database.
 * @returns {Promise}
 */
const flywayMigrate = () => {
  logger.info('Using flyway CLI to construct schema');
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = '.node-flywaydb';
  const flywayConfigPath = path.join(flywayDataPath, 'config.json');
  const locations = path.join('..', schemaConfigs.flyway.locations);
  const flywayConfig = `
{
  "flywayArgs": {
    "baselineVersion": "${schemaConfigs.flyway.baselineVersion}",
    "locations": "filesystem:${locations}",
    "password": "${dbAdminPassword}",
    "placeholders.api-password": "${dbConfig.password}",
    "placeholders.api-user": "${dbConfig.username}",
    "placeholders.chunkIdInterval": 10000,
    "placeholders.chunkTimeInterval": 604800000000000,
    "placeholders.compressionAge": 604800000000000,
    "placeholders.db-name": "${dbConfig.name}",
    "placeholders.db-user": "${dbAdminUser}",
    "placeholders.topicRunningHashV2AddedTimestamp": 0,
    "url": "jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/${dbConfig.name}",
    "user": "${dbAdminUser}"
  },
  "version": "7.1.1",
  "downloads": {
    "storageDirectory": "${flywayDataPath}"
  }
}
`;

  fs.mkdirSync(flywayDataPath, {recursive: true});
  fs.writeFileSync(flywayConfigPath, flywayConfig);

  return new Promise((resolve, reject) => {
    let args = ['node', exePath, '-c', flywayConfigPath, 'clean'];
    exec(args.join(' '), {}, (err) => {
      if (err) {
        reject(err);
      }
      args = ['node', exePath, '-c', flywayConfigPath, 'migrate'];
      exec(args.join(' '), {}, (err, stdout) => {
        if (err) {
          reject(err);
        } else {
          logger.info(stdout);
          resolve();
        }
      });
    });
  });
};

const closeConnection = async () => {
  if (sqlConnection) {
    await sqlConnection.end();
    sqlConnection = null;
  }
  if (dockerDb) {
    await dockerDb.stop();
    dockerDb = null;
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
    'main',
    'resources',
    'db',
    'scripts',
    'cleanup.sql'
  ),
  'utf8'
);

const cleanUp = async () => {
  if (sqlConnection) {
    await sqlConnection.query(cleanupSql);
  }
};

const runSqlQuery = async (query, params) => {
  return sqlConnection.query(query, params);
};

module.exports = {
  cleanUp,
  closeConnection,
  instantiateDatabase,
  runSqlQuery,
};
