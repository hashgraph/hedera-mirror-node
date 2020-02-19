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
 *
 * Tests instantiate the database schema via a flywaydb wrapper using the flyway CLI to clean and migrate the
 * schema (using sql files in the ../src/resources/db/migration directory).
 *
 * Before each test the DB is cleared out.
 *
 * Tests are loaded from the specs directory via JSON files.
 * Each file has a 'setup' section for setting up database data, a `url` to call, and expected results JSON.
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

/**
 * {entity_shard: 0, entity_realm: 0, entity_num: 27} to '0.0.27'
 * @param account
 * @returns {string}
 */
const getAccountId = function(account) {
  return account.entity_shard + '.' + account.entity_realm + '.' + account.entity_num;
};

/**
 * '0.0.27' to {entity_shard: 0, entity_realm: 0, entity_num: 27}
 * @param str
 * @returns {{entity_realm: (*|string), entity_num: (*|string), entity_shard: (*|string)}}
 */
const toAccount = function(str) {
  let tokens = str.split('.');
  return {
    entity_shard: tokens[0],
    entity_realm: tokens[1],
    entity_num: tokens[2]
  };
};

const consensusTimestampDecimalToBignum = function(decimalValue) {
  let tokens = decimalValue.split('.', 2);
  let result = math.bignumber(tokens[0]).times(1000000000);
  if (tokens.length > 1) {
    result = result.plus(math.bignumber(tokens[1]));
  }
  return result;
};

const addAccount = async function(account) {
  account = Object.assign({entity_shard: 0, entity_realm: 0, exp_time_ns: null}, account);
  let e = accountEntityIds[getAccountId(account)];
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

/**
 * Insert an array of {account_id: '0.0.123', balances: [consensus_timestamp: 123456789000000000, balance: 100] } into
 * the account_balances table.
 * @param accountBalances
 * @returns {Promise<*|string>}
 */
const addAccountBalances = async function(accountBalances) {
  let account = toAccount(accountBalances.account_id);
  for (let i = 0; i < accountBalances.balances.length; ++i) {
    let ab = accountBalances.balances[i];
    await sqlConnection.query(
      'insert into account_balances (account_realm_num, account_num, balance, consensus_timestamp) values ($1, $2, $3, $4);',
      [
        account.entity_realm,
        account.entity_num,
        ab.balance,
        consensusTimestampDecimalToBignum(ab.consensus_timestamp).toString()
      ]
    );
  }
};

const aggregateTransfers = function(transaction) {
  let set = new Set();
  transaction.transfers.forEach(transfer => {
    let accountId = getAccountId(transfer); // Get '0.0.x' account ID from the transfer.
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
      accountEntityIds[transaction.payer_account_id],
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
  if (!('sender_account_id' in cryptoTransfer)) {
    cryptoTransfer.sender_account_id = cryptoTransfer.payer_account_id;
  }
  let sender = toAccount(cryptoTransfer.sender_account_id);
  let recipient = toAccount(cryptoTransfer.recipient_account_id);
  if (!('transfers' in cryptoTransfer)) {
    let payer = toAccount(cryptoTransfer.payer_account_id);
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

  //
  // Setup data in the database for the test.
  //
  if ('setup' in spec) {
    test(`DB integration test - ${file} - ${spec.url}`, async () => {
      console.log(`Processing - ${file}`);
      for (let i = 0; i < spec.setup.length; ++i) {
        let args = spec.setup[i];
        let func = Object.keys(args)[0];
        let funcArgs = args[func];
        console.log('Setup ' + func + ' ' + JSON.stringify(funcArgs));
        switch (func) {
          case 'add_account':
            await addAccount(funcArgs);
            break;
          case 'add_transaction':
            await addTransaction(funcArgs);
            break;
          case 'add_crypto_transfer':
            await addCryptoTransfer(funcArgs);
            break;
          case 'add_account_balances':
            await addAccountBalances(funcArgs);
            break;
        }
      }

      let response = await request(server).get(spec.url);

      expect(response.status).toEqual(spec.responseStatus);
      expect(JSON.parse(response.text)).toEqual(spec.responseJson);
    });
  }
});

// Process a /api/v1/transactions request returning a mix of R3 and R4-style transfer lists.
let url = '/api/v1/transactions';
let n = 1000;
test(`DB integration test - ${n} txns - ${url}`, async () => {
  await addAccount({entity_num: 1234});
  await addAccount({entity_num: 9876});
  let start = math.bignumber('12345678000000000');
  for (let i = 0; i < n; ++i) {
    await addCryptoTransfer({
      consensus_timestamp: start.plus(i),
      payer_account_id: '0.0.9876',
      recipient_account_id: '0.0.1234',
      amount: i,
      aggregate_transfers: i >= n / 2, // First half have R3-style pre-itemized-by-HAPI transfer lists, last half R4 aggregated.
      include_non_fee_transfers: 0 == i % 2 // Starting with 0, odd have non_fee_transfers rows, even do not.
    });
  }

  let response = await request(server).get(url);

  expect(response.status).toEqual(200);

  let transactions = JSON.parse(response.text).transactions;
  expect(transactions.length).toEqual(n); // Received all the expected transactions

  transactions.forEach((transaction, i) => {
    let actual = consensusTimestampDecimalToBignum(transaction.consensus_timestamp);
    expect(actual.toString()).toBe(start.plus(n - i - 1).toString());

    expect(transaction.transfers.length).toBeGreaterThan(2);
  });
});
