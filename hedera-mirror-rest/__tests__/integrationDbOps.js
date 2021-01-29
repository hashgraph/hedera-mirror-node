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

const config = require('../config.js');
const {GenericContainer} = require('testcontainers');
const {exec} = require('child_process');
const path = require('path');
const fs = require('fs');
const SqlConnectionPool = require('pg').Pool;
const {randomString} = require('../utils');
const {isDockerInstalled} = require('./integrationUtils');

let oldPool;
let dockerDb;
let sqlConnection;

config.db.name = process.env.POSTGRES_DB || 'mirror_node_integration';
const dbUser = process.env.POSTGRES_USER || config.db.username + '_admin';
const dbPassword = process.env.POSTGRES_PASSWORD || randomString(16);

const v1SchemaConfigs = {
  docker: {
    imageName: 'postgres',
    tagName: '9.6-alpine',
  },
  flyway: {
    baselineVersion: '0',
    locations: 'hedera-mirror-importer/src/main/resources/db/migration/v1',
    target: '1.999.999',
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
    target: '2.999.999',
  },
};

// if v2 schema is set in env use it, else default to v1
const schemaConfigs = process.env.MIRROR_NODE_INT_DB === 'v2' ? v2SchemaConfigs : v1SchemaConfigs;

/**
 * Instantiate sqlConnection by either pointing at a DB specified by environment variables or instantiating a
 * testContainers/dockerized postgresql instance.
 */
const instantiateDatabase = async function () {
  if (!process.env.CIRCLECI && !process.env.CI_CONTAINERS) {
    if (!(await isDockerInstalled())) {
      console.log('Docker not found. Integration tests will fail.');
      return;
    }

    dockerDb = await new GenericContainer(schemaConfigs.docker.imageName, schemaConfigs.docker.tagName)
      .withEnv('POSTGRES_DB', config.db.name)
      .withEnv('POSTGRES_USER', dbUser)
      .withEnv('POSTGRES_PASSWORD', dbPassword)
      .withExposedPorts(config.db.port)
      .start();
    config.db.port = dockerDb.getMappedPort(config.db.port);
    config.db.host = dockerDb.getHost();
    console.log(`Started dockerized PostgreSQL ${schemaConfigs.docker.imageName}:${schemaConfigs.docker.tagName}`);
  }

  console.log(`sqlConnection will use postgresql://${config.db.host}:${config.db.port}/${config.db.name}`);
  sqlConnection = new SqlConnectionPool({
    user: dbUser,
    host: config.db.host,
    database: config.db.name,
    password: dbPassword,
    port: config.db.port,
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
const flywayMigrate = function () {
  console.log('Using flyway CLI to construct schema');
  const exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  const flywayDataPath = '.node-flywaydb';
  const flywayConfigPath = path.join(flywayDataPath, 'config.json');
  const locations = path.join('..', schemaConfigs.flyway.locations);
  const flywayConfig = `
{
  "flywayArgs": {
    "baselineVersion": "${schemaConfigs.flyway.baselineVersion}",
    "locations": "filesystem:${locations}",
    "password": "${dbPassword}",
    "placeholders.api-password": "${config.db.password}",
    "placeholders.api-user": "${config.db.username}",
    "placeholders.chunkIdInterval": 10000,
    "placeholders.chunkTimeInterval": 604800000000000,
    "placeholders.compressionAge": 604800000000000,
    "placeholders.db-name": "${config.db.name}",
    "placeholders.db-user": "${dbUser}",
    "placeholders.topicRunningHashV2AddedTimestamp": 0,
    "target": "${schemaConfigs.flyway.target}",
    "url": "jdbc:postgresql://${config.db.host}:${config.db.port}/${config.db.name}",
    "user": "${dbUser}"
  },
  "version": "6.5.7",
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
          console.log(stdout);
          resolve();
        }
      });
    });
  });
};

const closeConnection = function () {
  if (sqlConnection) {
    sqlConnection.end();
    sqlConnection = null;
  }
  if (dockerDb) {
    dockerDb.stop({
      removeVolumes: false,
    });
    dockerDb = null;
  }
  if (oldPool !== null) {
    global.pool = oldPool;
    oldPool = null;
  }
};

const cleanUp = async function () {
  if (sqlConnection) {
    await sqlConnection.query(cleanupSql);
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

const runSqlQuery = async function (query, params) {
  return await sqlConnection.query(query, params);
};

module.exports = {
  cleanUp,
  closeConnection,
  instantiateDatabase,
  runSqlQuery,
};
