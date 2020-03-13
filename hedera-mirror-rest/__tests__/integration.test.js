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
 * 1) reading account id, balance, expiration and crypto transfer information from *.spec.json
 * 2) apply account creations, balance sets and transfers to the integration DB
 *
 * Tests are then run in code below (find TESTS all caps) and by comparing requests/responses from the server to data
 * in the specs/ dir.
 *
 * environment variables used:
 * TEST_DB_HOST (default: use testcontainers, examples: localhost, dbhost, 10.0.0.75)
 * TEST_DB_PORT (default: 5432)
 * TEST_DB_NAME (default: mirror_node_integration)
 */
const path = require('path');
const request = require('supertest');
const server = require('../server');
const fs = require('fs');
const integrationDbOps = require('./integrationDbOps.js');

beforeAll(async () => {
  jest.setTimeout(20000);
  await integrationDbOps.instantiateDatabase();
});

afterAll(() => {
  integrationDbOps.closeConnection();
});

let accountEntityIds = {};
beforeEach(async () => {
  await integrationDbOps.cleanUp();
  accountEntityIds = {};
  await setupData();
});

/**
 * Setup test data in the postgres instance.
 */
const testDataPath = path.join(__dirname, 'integration_test_data.json');
const testData = fs.readFileSync(testDataPath);
const testDataJson = JSON.parse(testData);

const shard = 0;
const realm = 15;
const addAccount = async function(accountId, exp_tm_nanosecs = null) {
  let e = accountEntityIds[accountId];
  if (e) {
    return e;
  }
  let res = await integrationDbOps.runSqlQuery(
    'insert into t_entities (fk_entity_type_id, entity_shard, entity_realm, entity_num, exp_time_ns) values ($1, $2, $3, $4, $5) returning id;',
    [1, shard, realm, accountId, exp_tm_nanosecs]
  );
  e = res.rows[0]['id'];
  accountEntityIds[accountId] = e;
  return e;
};

const addTransaction = async function(
  consensusTimestamp,
  fileId,
  payerAccountId,
  transfers,
  validDurationSeconds = 11,
  maxFee = 33,
  result = 22,
  type = 14
) {
  await integrationDbOps.runSqlQuery(
    'insert into t_transactions (consensus_ns, valid_start_ns, fk_rec_file_id, fk_payer_acc_id, fk_node_acc_id, result, type, valid_duration_seconds, max_fee) values ($1, $2, $3, $4, $5, $6, $7, $8, $9);',
    [
      consensusTimestamp,
      consensusTimestamp - 1,
      fileId,
      accountEntityIds[payerAccountId],
      accountEntityIds[2],
      result,
      type,
      validDurationSeconds,
      maxFee
    ]
  );
  for (var i = 0; i < transfers.length; ++i) {
    let xfer = transfers[i];
    await integrationDbOps.runSqlQuery(
      'insert into t_cryptotransferlists (consensus_timestamp, amount, realm_num, entity_num) values ($1, $2, $3, $4);',
      [consensusTimestamp, xfer[1], realm, xfer[0]]
    );
  }
};

/**
 * Add a crypto transfer from A to B with A also paying 1 tinybar to account number 2 (fee)
 * @param consensusTimestamp
 * @param fileId
 * @param payerAccountId
 * @param recipientAccountId
 * @param amount
 * @returns {Promise<void>}
 */
const addCryptoTransferTransaction = async function(
  consensusTimestamp,
  fileId,
  payerAccountId,
  recipientAccountId,
  amount,
  validDurationSeconds,
  maxFee,
  bankId = 2
) {
  await addTransaction(
    consensusTimestamp,
    fileId,
    payerAccountId,
    [
      [payerAccountId, -1 - amount],
      [recipientAccountId, amount],
      [bankId, 1]
    ],
    validDurationSeconds,
    maxFee
  );
};

const setupData = async function() {
  let res = await integrationDbOps.runSqlQuery('insert into t_record_files (name) values ($1) returning id;', ['test']);
  let fileId = res.rows[0]['id'];
  console.log(`Record file id is ${fileId}`);

  const balancePerAccountCount = 3;
  const testAccounts = testDataJson['accounts'];
  console.log(`Adding ${testAccounts.length} accounts with ${balancePerAccountCount} balances per account`);
  for (let account of testAccounts) {
    await addAccount(account.id, account.expiration_ns);

    // Add 3 balances for each account.
    for (var ts = 0; ts < balancePerAccountCount; ++ts) {
      await integrationDbOps.runSqlQuery(
        'insert into account_balances (consensus_timestamp, account_realm_num, account_num, balance) values ($1, $2, $3, $4);',
        [ts * 1000, realm, account.id, account.balance]
      );
    }
  }

  console.log('Adding crypto transfer transactions');
  for (let transfer of testDataJson['transfers']) {
    await addCryptoTransferTransaction(transfer.time, fileId, transfer.from, transfer.to, transfer.amount);
  }

  console.log('Finished initializing DB data');
};

let specPath = path.join(__dirname, 'specs');
fs.readdirSync(specPath).forEach(function(file) {
  let p = path.join(specPath, file);
  let specText = fs.readFileSync(p, 'utf8');
  var spec = JSON.parse(specText);
  test(`DB integration test - ${file} - ${spec.url}`, async () => {
    let response = await request(server).get(spec.url);
    expect(response.status).toEqual(spec.responseStatus);
    expect(JSON.parse(response.text)).toEqual(spec.responseJson);
  });
});
