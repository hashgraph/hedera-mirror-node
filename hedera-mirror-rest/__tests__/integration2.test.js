/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

/**
 * Integration tests for the rest-api and postgresql database.
 * Tests will be performed using either a docker postgres instance managed by the testContainers module or
 * some other database (running locally, instantiated in the CI environment, etc).
 * Tests instantiate the database schema via a flywaydb wrapper using the flyway CLI to clean and migrate the
 * schema (using sql files in the ../src/resources/db/migration directory).
 *
 * Test data is created by:
 * 1) reading account id, balance, expiration and crypto transfer information from integration_test_data.json
 * 2) storing those accounts in integration DB
 * 3) creating 3 balances records per account at timestamp 1000, 2000, 3000 in the integration DB
 * 4) apply transfers (from integration_test_data.json) to the integration DB
 *
 * Tests are then run in code below (find TESTS all caps) and by comparing requests/responses from the server to data
 * in the specs/ dir.
 *
 * environment variables used:
 * TEST_DB_HOST (default: use testcontainers, examples: localhost, dbhost, 10.0.0.75)
 * TEST_DB_PORT (default: 5432)
 * TEST_DB_NAME (default: mirror_node_integration)
 */
const {GenericContainer} = require('testcontainers');
const exec = require('child_process').exec;
const path = require('path');
const request = require('supertest');
const math = require('mathjs');
const server = require('../server');
const utils = require('../utils');
const fs = require('fs');

let oldPool;

let dockerDb;
let SqlConnectionPool = require('pg').Pool;
let sqlConnection;

const defaultPostgresqlPort = 5432;
const defaultDbName = 'mirror_node_integration';
const dbUser = 'mirror_node';
const dbPassword = 'mirror_node_pass';
const dockerPostgresTag = '9.6.14-alpine';
let dbPort = defaultPostgresqlPort;
let dbHost = '127.0.0.1';
let dbName = defaultDbName;

const isDockerInstalled = function() {
  return new Promise(resolve => {
    exec('docker --version', err => {
      resolve(!err);
    });
  });
};

/**
 * Instantiate sqlConnection by either pointing at a DB specified by environment variables or instantiating a
 * testContainers/dockerized postgresql instance.
 */
const instantiateDatabase = async function() {
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
    port: dbPort
  });
  // Until "server", "pool" and everything else is made non-static...
  oldPool = global.pool;
  global.pool = sqlConnection;

  await flywayMigrate();
};

/**
 * Run the sql (non-java) based migrations stored in the importer project against the target database.
 * @returns {Promise}
 */
const flywayMigrate = function() {
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
        FLYWAY_LOCATIONS:
          'filesystem:' + path.join('..', 'hedera-mirror-importer', 'src', 'main', 'resources', 'db', 'migration')
      },
      process.env
    )
  };
  return new Promise((resolve, reject) => {
    let args = ['node', exePath, '-c', configPath, 'clean'];
    exec(args.join(' '), flywayEnv, err => {
      if (err) {
        reject(err);
        return;
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

beforeAll(async () => {
  jest.setTimeout(30000);
  await instantiateDatabase();
});

afterAll(() => {
  if (sqlConnection) {
    sqlConnection.end();
    sqlConnection = null;
  }
  if (dockerDb) {
    dockerDb.stop({
      removeVolumes: false
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
});

//
// Test data helpers
//
const TREASURY_ACCOUNT_ID = utils.TREASURY_ACCOUNT_ID;
const NODE_ACCOUNT_ID = '0.0.3';
const NODE_FEE = 1;
const NETWORK_FEE = 2;
const SERVICE_FEE = 4;

// Local map of account id '0.0.2' => t_entities.id
let accountEntityIds = {};
let recordFileId;

const getAccountId = function(account) {
  return account.entity_shard + '.' + account.entity_realm + '.' + account.entity_num;
};

const toAccount = function(str) {
  let tokens = str.split('.');
  return {
    entity_shard: tokens[0],
    entity_realm: tokens[1],
    entity_num: tokens[2]
  };
};

const addAccount = async function(account) {
  account = Object.assign({entity_shard: 0, entity_realm: 0, exp_time_ns: null}, account);
  let e = accountEntityIds[account.entity_num];
  if (e) {
    return e;
  }
  let res = await sqlConnection.query(
    'insert into t_entities (fk_entity_type_id, entity_shard, entity_realm, entity_num, exp_time_ns) values ($1, $2, $3, $4, $5) returning id;',
    [1, account.entity_shard, account.entity_realm, account.entity_num, account.exp_time_ns]
  );
  e = res.rows[0]['id'];
  accountEntityIds[getAccountId(account)] = e;
  return e;
};

const aggregateTransfers = function(transaction) {
  let set = new Set();
  transaction.transfers.forEach(transfer => {
    let accountId = getAccountId(transfer);
    let val = set[accountId];
    if (undefined === val) {
      set[accountId] = transfer;
    } else {
      set[accountId].amount += transfer.amount;
    }
  });
  transaction.transfers = Object.values(set);
};

const addTransaction = async function(transaction) {
  transaction = Object.assign(
    {
      type: 14,
      result: 22,
      max_fee: 33,
      valid_duration_seconds: 11,
      transfers: [],
      non_fee_transfers: [],
      charged_tx_fee: NODE_FEE + NETWORK_FEE + SERVICE_FEE
    },
    transaction
  );
  transaction.consensus_timestamp = math.bignumber(transaction.consensus_timestamp);
  await sqlConnection.query(
    'insert into t_transactions (consensus_ns, valid_start_ns, fk_rec_file_id, fk_payer_acc_id, fk_node_acc_id, result, type, valid_duration_seconds, max_fee, charged_tx_fee) values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10);',
    [
      transaction.consensus_timestamp.toString(),
      transaction.consensus_timestamp.minus(1).toString(),
      recordFileId,
      accountEntityIds[transaction.payerAccountId],
      accountEntityIds[NODE_ACCOUNT_ID],
      transaction.result,
      transaction.type,
      transaction.valid_duration_seconds,
      transaction.max_fee,
      transaction.charged_tx_fee
    ]
  );
  if (transaction['aggregate_transfers']) {
    aggregateTransfers(transaction);
  }
  for (let i = 0; i < transaction.transfers.length; ++i) {
    let transfer = transaction.transfers[i];
    await sqlConnection.query(
      'insert into t_cryptotransferlists (consensus_timestamp, amount, realm_num, entity_num) values ($1, $2, $3, $4);',
      [transaction.consensus_timestamp.toString(), transfer.amount, transfer.entity_realm, transfer.entity_num]
    );
  }
  for (let i = 0; i < transaction.non_fee_transfers.length; ++i) {
    let transfer = transaction.non_fee_transfers[i];
    await sqlConnection.query(
      'insert into non_fee_transfers (consensus_timestamp, amount, realm_num, entity_num) values ($1, $2, $3, $4);',
      [transaction.consensus_timestamp.toString(), transfer.amount, transfer.entity_realm, transfer.entity_num]
    );
  }
};

const addCryptoTransfer = async function(cryptoTransfer) {
  if (!('senderAccountId' in cryptoTransfer)) {
    cryptoTransfer.senderAccountId = cryptoTransfer.payerAccountId;
  }
  let sender = toAccount(cryptoTransfer.senderAccountId);
  let recipient = toAccount(cryptoTransfer.recipientAccountId);
  if (!('transfers' in cryptoTransfer)) {
    let payer = toAccount(cryptoTransfer.payerAccountId);
    let node = toAccount(NODE_ACCOUNT_ID);
    let treasury = toAccount(TREASURY_ACCOUNT_ID);
    cryptoTransfer['transfers'] = [
      Object.assign({}, payer, {amount: 0 - NODE_FEE - SERVICE_FEE}),
      Object.assign({}, payer, {amount: 0 - NETWORK_FEE}),
      Object.assign({}, sender, {amount: 0 - cryptoTransfer.amount}),
      Object.assign({}, recipient, {amount: cryptoTransfer.amount}),
      Object.assign({}, node, {amount: NODE_FEE}),
      Object.assign({}, treasury, {amount: SERVICE_FEE}),
      Object.assign({}, treasury, {amount: NETWORK_FEE})
    ];
  }
  if (cryptoTransfer['include_non_fee_transfers']) {
    cryptoTransfer['non_fee_transfers'] = [
      Object.assign({}, sender, {amount: 0 - cryptoTransfer.amount}),
      Object.assign({}, recipient, {amount: cryptoTransfer.amount})
    ];
  }
  await addTransaction(cryptoTransfer);
};

const setupBasic = async function() {
  await addAccount(toAccount(TREASURY_ACCOUNT_ID));
  await addAccount(toAccount(NODE_ACCOUNT_ID));
  let res = await sqlConnection.query('insert into t_record_files (name) values ($1) returning id;', ['test']);
  recordFileId = res.rows[0]['id'];
  console.log('Clearing DB');
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
beforeEach(async () => {
  // Cleanup
  await sqlConnection.query(cleanupSql);
  accountEntityIds = {};
  await setupBasic();
});

let specPath = path.join(__dirname, 'specs');
fs.readdirSync(specPath).forEach(function(file) {
  let p = path.join(specPath, file);
  let specText = fs.readFileSync(p, 'utf8');
  let spec = JSON.parse(specText);
  if ('setup' in spec) {
    test(`DB integration test - ${file} - ${spec.url}`, async () => {
      for (let i = 0; i < spec.setup.length; ++i) {
        let args = spec.setup[i];
        let func = Object.keys(args)[0];
        let funcArgs = args[func];
        console.log('Setup ' + func + ' ' + JSON.stringify(funcArgs));
        switch (func) {
          case 'addAccount':
            await addAccount(funcArgs);
            break;
          case 'addTransaction':
            await addTransaction(funcArgs);
            break;
          case 'addCryptoTransfer':
            await addCryptoTransfer(funcArgs);
            break;
        }
      }

      let response = await request(server).get(spec.url);

      expect(response.status).toEqual(spec.responseStatus);
      expect(JSON.parse(response.text)).toEqual(spec.responseJson);
    });
  }
});

// With a mix of R3 and R4
let url = '/api/v1/transactions';
let n = 1000;
test(`DB integration test - ${n} txns  - ${url}`, async () => {
  await addAccount({entity_num: 1234});
  await addAccount({entity_num: 9876});
  for (let i = 0; i < n; ++i) {
    let start = math.bignumber('12345678000000000');
    await addCryptoTransfer({
      consensus_timestamp: start.plus(i),
      payerAccountId: '0.0.9876',
      recipientAccountId: '0.0.1234',
      amount: i,
      aggregate_transfers: i > 500,
      include_non_fee_transfers: 0 == i % 2
    });
  }

  let response = await request(server).get(url);

  expect(response.status).toEqual(200);
  expect(JSON.parse(response.text).transactions.length).toEqual(n);
});
