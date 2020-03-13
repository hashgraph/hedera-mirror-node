/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019-2020 Hedera Hashgraph, LLC
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
 *
 * environment variables used:
 * TEST_DB_HOST (default: use testcontainers, examples: localhost, dbhost, 10.0.0.75)
 * TEST_DB_PORT (default: 5432)
 * TEST_DB_NAME (default: mirror_node_integration)
 */
'use strict';

const {GenericContainer} = require('testcontainers');
const exec = require('child_process').exec;
const path = require('path');
const math = require('mathjs');
const utils = require('../utils');
const fs = require('fs');

//
// Docker & DB management
//

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

const TREASURY_ACCOUNT_ID = utils.TREASURY_ACCOUNT_ID;
const NODE_ACCOUNT_ID = '0.0.3';
const NODE_FEE = 1;
const NETWORK_FEE = 2;
const SERVICE_FEE = 4;

let accountEntityIds = {};
let recordFileId;

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

  return sqlConnection;
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

const closeConnection = function() {
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
};

//
// Test helpers
//

const setUp = async function(testDataJson) {
  await cleanUp();
  accountEntityIds = {};
  await addAccount(toAccount(TREASURY_ACCOUNT_ID));
  await addAccount(toAccount(NODE_ACCOUNT_ID));
  await addRecordFile(testDataJson['filename']);
  await loadAccounts(testDataJson['accounts']);
  await loadBalances(testDataJson['balances']);
  await loadCryptoTransfers(testDataJson['cryptotransfers']);
  await loadTransactions(testDataJson['transactions']);
};

const cleanUp = async function() {
  await sqlConnection.query(cleanupSql);
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

const runSqlQuery = async function(query, params) {
  return await sqlConnection.query(query, params);
};

const loadAccounts = async function(accounts) {
  if (accounts == null) {
    return;
  }

  for (let i = 0; i < accounts.length; ++i) {
    await addAccount(accounts[i]);
  }
};

const loadBalances = async function(balances) {
  if (balances == null) {
    return;
  }

  for (let i = 0; i < balances.length; ++i) {
    await setAccountBalance(balances[i]);
  }
};

const loadCryptoTransfers = async function(cryptoTransfers) {
  if (cryptoTransfers == null) {
    return;
  }

  for (let i = 0; i < cryptoTransfers.length; ++i) {
    await addCryptoTransaction(cryptoTransfers[i]);
  }
};

const loadTransactions = async function(transactions) {
  if (transactions == null) {
    return;
  }

  for (let i = 0; i < transactions.length; ++i) {
    await addTransaction(transactions[i]);
  }
};

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

const setAccountBalance = async function(account) {
  account = Object.assign({timestamp: 0, realm_num: 0, id: null, balance: 0}, account);
  await sqlConnection.query(
    'insert into account_balances (consensus_timestamp, account_realm_num, account_num, balance) values ($1, $2, $3, $4);',
    [account.timestamp, account.realm_num, account.id, account.balance]
  );
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

const addRecordFile = async function(recordFileName) {
  let res = await sqlConnection.query('insert into t_record_files (name) values ($1) returning id;', [recordFileName]);

  recordFileId = res.rows[0]['id'];
  return recordFileId;
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

const addCryptoTransaction = async function(cryptoTransfer) {
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

module.exports = {
  addAccount: addAccount,
  addCryptoTransaction: addCryptoTransaction,
  addRecordFile: addRecordFile,
  addTransaction: addTransaction,
  cleanUp: cleanUp,
  closeConnection: closeConnection,
  instantiateDatabase: instantiateDatabase,
  loadAccounts: loadAccounts,
  loadBalances: loadBalances,
  loadCryptoTransfers: loadCryptoTransfers,
  loadTransactions: loadTransactions,
  runSqlQuery: runSqlQuery,
  setAccountBalance: setAccountBalance,
  setUp: setUp,
  toAccount: toAccount
};
