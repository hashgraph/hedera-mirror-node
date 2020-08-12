/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const {GenericContainer} = require('testcontainers');
const {exec} = require('child_process');
const path = require('path');
const fs = require('fs');
const SqlConnectionPool = require('pg').Pool;
const utils = require('../utils');
const {isDockerInstalled} = require('./integrationUtils');

//
// Docker & DB management
//

let oldPool;
let dockerDb;
let sqlConnection;

const defaultPostgresqlPort = 5432;
const defaultDbName = 'mirror_node_integration';
const dbUser = 'mirror_node';
const dbPassword = 'mirror_node_pass';
const dockerPostgresTag = '9.6-alpine';
let dbPort = defaultPostgresqlPort;
let dbHost = '127.0.0.1';
let dbName = defaultDbName;

const TREASURY_ACCOUNT_ID = utils.TREASURY_ACCOUNT_ID;
const NODE_ACCOUNT_ID = '0.0.3';
const NODE_FEE = 1;
const NETWORK_FEE = 2;
const SERVICE_FEE = 4;

let accountEntityIds = {};

/**
 * Instantiate sqlConnection by either pointing at a DB specified by environment variables or instantiating a
 * testContainers/dockerized postgresql instance.
 */
const instantiateDatabase = async function () {
  if (!process.env.TEST_DB_HOST) {
    if (!(await isDockerInstalled())) {
      dbPort = dbHost = dbName = null;
      console.log('Environment variable TEST_DB_HOST not set and docker not found. Integration tests will fail.');
      return;
    }

    dockerDb = await new GenericContainer('postgres', dockerPostgresTag)
      .withEnv('POSTGRES_DB', dbName)
      .withEnv('POSTGRES_USER', dbUser)
      .withEnv('POSTGRES_PASSWORD', dbPassword)
      .withExposedPorts(defaultPostgresqlPort)
      .start();
    dbPort = dockerDb.getMappedPort(defaultPostgresqlPort);
    console.log(
      `Setup testContainer (dockerized version of) postgres ${dockerPostgresTag}, listening on port ${dbPort}`
    );
  } else {
    dbHost = process.env.TEST_DB_HOST;
    dbPort = process.env.TEST_DB_PORT || defaultPostgresqlPort;
    dbName = process.env.TEST_DB_NAME || defaultDbName;
    console.log(`Using integration database ${dbHost}:${dbPort}/${dbName}`);
  }

  sqlConnection = new SqlConnectionPool({
    user: dbUser,
    host: dbHost,
    database: dbName,
    password: dbPassword,
    port: dbPort,
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
  let exePath = path.join('.', 'node_modules', 'node-flywaydb', 'bin', 'flyway');
  let configPath = path.join('config', '.node-flywaydb.integration.conf');
  let flywayEnv = {
    env: Object.assign(
      {},
      {
        FLYWAY_URL: `jdbc:postgresql://${dbHost}:${dbPort}/${dbName}`,
        FLYWAY_USER: dbUser,
        FLYWAY_PASSWORD: dbPassword,
        'FLYWAY_PLACEHOLDERS_db-name': dbName,
        'FLYWAY_PLACEHOLDERS_db-user': dbUser,
        'FLYWAY_PLACEHOLDERS_api-user': 'mirror_api',
        'FLYWAY_PLACEHOLDERS_api-password': 'mirror_api_pass',
        FLYWAY_PLACEHOLDERS_topicRunningHashV2AddedTimestamp: 0,
        FLYWAY_LOCATIONS:
          'filesystem:' + path.join('..', 'hedera-mirror-importer', 'src', 'main', 'resources', 'db', 'migration'),
      },
      process.env
    ),
  };
  return new Promise((resolve, reject) => {
    let args = ['node', exePath, '-c', configPath, 'clean'];
    exec(args.join(' '), flywayEnv, (err) => {
      if (err) {
        reject(err);
      }
      args = ['node', exePath, '-c', configPath, 'migrate'];
      exec(args.join(' '), flywayEnv, (err, stdout) => {
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
  if (process.env.CI) {
    let logPath = path.join(__dirname, '..', '..', 'logs', 'hedera_mirrornode_api_3000.log');
    console.log(logPath);
    if (fs.existsSync(logPath)) {
      console.log(fs.readFileSync(logPath, 'utf8'));
    }
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
  cleanUp: cleanUp,
  closeConnection: closeConnection,
  instantiateDatabase: instantiateDatabase,
  runSqlQuery: runSqlQuery,
};
