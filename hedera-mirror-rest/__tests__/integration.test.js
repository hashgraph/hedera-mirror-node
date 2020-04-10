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

/**
 * Integration tests for the rest-api and postgresql database.
 * Tests will be performed using either a docker postgres instance managed by the testContainers module or
 * some other database (running locally, instantiated in the CI environment, etc).
 * Tests instantiate the database schema via a flywaydb wrapper using the flyway CLI to clean and migrate the
 * schema (using sql files in the ../src/resources/db/migration directory).
 *
 * * Test data for rest-api tests is created by:
 * 1) reading account id, balance, expiration and crypto transfer information from *.spec.json
 * 2) applying account creations, balance sets and transfers to the integration DB
 *
 * Test data for database tests is created by:
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
const transactions = require('../transactions.js');
const path = require('path');
const request = require('supertest');
const server = require('../server');
const fs = require('fs');
const integrationDbOps = require('./integrationDbOps.js');
const integrationDomainOps = require('./integrationDomainOps.js');
let sqlConnection;

beforeAll(async () => {
  jest.setTimeout(20000);
  sqlConnection = await integrationDbOps.instantiateDatabase();
});

afterAll(() => {
  integrationDbOps.closeConnection();
});

beforeEach(async () => {
  await integrationDbOps.cleanUp();
  await setupData();
});

//
// TEST DATA
// shard 0, realm 15, accounts 1-10
// 3 balances per account
// several transactions

const shard = 0;
const realm = 15;

/**
 * Setup test data in the postgres instance.
 */

const setupData = async function () {
  const testDataPath = path.join(__dirname, 'integration_test_data.json');
  const testData = fs.readFileSync(testDataPath);
  const testDataJson = JSON.parse(testData);

  await integrationDomainOps.setUp(testDataJson.setup, sqlConnection);

  console.log('Finished initializing DB data');
};

/**
 * Add a crypto transfer from A to B with A also paying 1 tinybar to account number 2 (fee)
 * @param consensusTimestamp
 * @param payerAccountId
 * @param recipientAccountId
 * @param amount
 * @returns {Promise<void>}
 */
const addCryptoTransferTransaction = async function (
  consensusTimestamp,
  payerAccountId,
  recipientAccountId,
  amount,
  validDurationSeconds,
  maxFee,
  result = 22,
  type = 14,
  nodeAccountId = '0.15.3',
  treasuryAccountId = '0.15.98'
) {
  await integrationDomainOps.addCryptoTransaction({
    consensus_timestamp: consensusTimestamp,
    payerAccountId: payerAccountId,
    recipientAccountId: recipientAccountId,
    amount: amount,
    valid_duration_seconds: validDurationSeconds,
    max_fee: maxFee,
    result: result,
    type: type,
    nodeAccountId: nodeAccountId,
    treasuryAccountId: treasuryAccountId,
  });
};

const createAndPopulateNewAccount = async (id, realm, ts, bal) => {
  await integrationDomainOps.addAccount({
    entity_num: id,
    entity_realm: realm,
  });

  await integrationDomainOps.setAccountBalance({
    timestamp: ts,
    id: id,
    realm_num: realm,
    balance: bal,
  });
};

/**
 * Map a DB transaction/cryptotransfer result to something easily comparable in a test assert/expect.
 * @param rows
 * @returns {*}
 */
function mapTransactionResults(rows) {
  return rows.map(function (v) {
    return '@' + v['consensus_ns'] + ': account ' + v['account_num'] + ' \u0127' + v['amount'];
  });
}

function extractDurationAndMaxFeeFromTransactionResults(rows) {
  return rows.map(function (v) {
    return '@' + v['valid_duration_seconds'] + ',' + v['max_fee'];
  });
}

function extractNameAndResultFromTransactionResults(rows) {
  return rows.map(function (v) {
    return '@' + v['name'] + ',' + v['result'];
  });
}

//
// TESTS
//

test('DB integration test - transactions.reqToSql - no query string - 3 txn 9 xfers', async () => {
  let sql = transactions.reqToSql({query: {}});
  let res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(res.rowCount).toEqual(9);
  expect(mapTransactionResults(res.rows).sort()).toEqual([
    '@1050: account 10 \u0127-11',
    '@1050: account 9 \u012710',
    '@1050: account 98 \u01271',
    '@1051: account 10 \u0127-21',
    '@1051: account 9 \u012720',
    '@1051: account 98 \u01271',
    '@1052: account 8 \u0127-31',
    '@1052: account 9 \u012730',
    '@1052: account 98 \u01271',
  ]);
});

test('DB integration test - transactions.reqToSql - single valid account - 1 txn 3 xfers', async () => {
  let sql = transactions.reqToSql({query: {'account.id': `${shard}.${realm}.8`}});
  let res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(res.rowCount).toEqual(3);
  expect(mapTransactionResults(res.rows).sort()).toEqual([
    '@1052: account 8 \u0127-31',
    '@1052: account 9 \u012730',
    '@1052: account 98 \u01271',
  ]);
});

test('DB integration test - transactions.reqToSql - invalid account', async () => {
  let sql = transactions.reqToSql({query: {'account.id': '0.17.666'}});
  let res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(res.rowCount).toEqual(0);
});

test('DB integration test - transactions.reqToSql - null validDurationSeconds and maxFee inserts', async () => {
  await addCryptoTransferTransaction(1062, '0.15.5', '0.15.4', 50, 5, null); // null maxFee
  await addCryptoTransferTransaction(1063, '0.15.5', '0.15.4', 70, null, 777); // null validDurationSeconds
  await addCryptoTransferTransaction(1064, '0.15.5', '0.15.4', 70, null, null); // valid validDurationSeconds and maxFee

  let sql = transactions.reqToSql({query: {'account.id': '0.15.5'}});
  let res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(res.rowCount).toEqual(9);
  expect(extractDurationAndMaxFeeFromTransactionResults(res.rows).sort()).toEqual([
    '@5,null',
    '@5,null',
    '@5,null',
    '@null,777',
    '@null,777',
    '@null,777',
    '@null,null',
    '@null,null',
    '@null,null',
  ]);
});

test('DB integration test - transactions.reqToSql - Unknown transaction result and type', async () => {
  await addCryptoTransferTransaction(1070, '0.15.7', '0.15.1', 2, 11, 33, -1, -1);

  let sql = transactions.reqToSql({query: {timestamp: '0.000001070'}});
  let res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(res.rowCount).toEqual(3);
  expect(extractNameAndResultFromTransactionResults(res.rows).sort()).toEqual([
    '@UNKNOWN,UNKNOWN',
    '@UNKNOWN,UNKNOWN',
    '@UNKNOWN,UNKNOWN',
  ]);
});

test('DB integration test - transactions.reqToSql - Account range filtered transactions', async () => {
  await createAndPopulateNewAccount(13, 15, 5, 10);
  await createAndPopulateNewAccount(63, 15, 6, 50);
  await createAndPopulateNewAccount(82, 15, 7, 100);

  // create 3 transactions - 9 transfers
  await addCryptoTransferTransaction(2062, '0.15.13', '0.15.63', 50, 5000, 50);
  await addCryptoTransferTransaction(2063, '0.15.63', '0.15.82', 70, 7000, 777);
  await addCryptoTransferTransaction(2064, '0.15.82', '0.15.63', 20, 8000, -80);

  let sql = transactions.reqToSql({query: {'account.id': ['gte:0.15.70', 'lte:0.15.97']}});
  let res = await integrationDbOps.runSqlQuery(sql.query, sql.params);

  // 6 transfers are applicable. For each transfer negative amount from self, amount to recipient and fee to bank
  // Note bank is out of desired range but is expected in query result
  expect(res.rowCount).toEqual(6);
  expect(mapTransactionResults(res.rows).sort()).toEqual([
    '@2063: account 63 \u0127-71',
    '@2063: account 82 \u012770',
    '@2063: account 98 \u01271',
    '@2064: account 63 \u012720',
    '@2064: account 82 \u0127-21',
    '@2064: account 98 \u01271',
  ]);
});

let specPath = path.join(__dirname, 'specs');
fs.readdirSync(specPath).forEach(function (file) {
  let p = path.join(specPath, file);
  let specText = fs.readFileSync(p, 'utf8');
  var spec = JSON.parse(specText);
  test(`DB integration test - ${file} - ${spec.url}`, async () => {
    await specSetupSteps(spec.setup);

    let response = await request(server).get(spec.url);

    expect(response.status).toEqual(spec.responseStatus);
    expect(JSON.parse(response.text)).toEqual(spec.responseJson);
  });
});

const specSetupSteps = async function (spec) {
  await integrationDbOps.cleanUp();
  await integrationDomainOps.setUp(spec, sqlConnection);
};
