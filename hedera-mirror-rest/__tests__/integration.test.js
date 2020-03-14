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
});

let specPath = path.join(__dirname, 'specs');
fs.readdirSync(specPath).forEach(function(file) {
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

const specSetupSteps = async function(spec) {
  await integrationDomainOps.setUp(spec, sqlConnection);
};
