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

const request = require('supertest');
const server = require('../server');
const testutils = require('./testutils.js');
const utils = require('../utils.js');

beforeAll(async () => {
  jest.setTimeout(1000);
});

afterAll(() => {});

const timeNow = Math.floor(new Date().getTime() / 1000);
const timeOneHourAgo = timeNow - 60 * 60;

// Validation functions
/**
 * Validate length of the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function (transactions, len) {
  return transactions.length === len;
};

/**
 * Validate the range of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} low Expected low limit of the timestamps
 * @param {Number} high Expected high limit of the timestamps
 * @return {Boolean}  Result of the check
 */
const validateTsRange = function (transactions, low, high) {
  let ret = true;
  let offender = null;
  for (const tx of transactions) {
    if (tx.consensus_timestamp < low || tx.consensus_timestamp > high) {
      offender = tx;
      ret = false;
    }
  }
  if (!ret) {
    console.log(`validateTsRange check failed: ${offender.consensus_timestamp} is not between ${low} and  ${high}`);
  }
  return ret;
};

/**
 * Validate the range of account ids in the transactions returned by the api
 * At least one transfer in a transaction should match the expected range
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function (transactions, low, high) {
  let ret = false;
  for (const tx of transactions) {
    for (const xfer of tx.transfers) {
      const accNum = xfer.account.split('.')[2];
      if (accNum >= low && accNum <= high) {
        // if at least one transfer is valid move to next transaction
        ret = true;
        break;
      }
    }

    if (!ret) {
      console.log(
        `validateAccNumRange check failed: No transfer with account between ${low} and ${high} was found in transaction : ${JSON.stringify(
          tx
        )}`
      );
      return false;
    }

    // reset ret
    ret = false;
  }

  return true;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} transactions Array of transactions returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function (transactions) {
  let ret = true;

  // Assert that all mandatory fields are present in the response
  [
    'consensus_timestamp',
    'valid_start_timestamp',
    'charged_tx_fee',
    'transaction_id',
    'memo_base64',
    'result',
    'name',
    'node',
    'transfers',
    'valid_duration_seconds',
    'max_fee',
    'transaction_hash',
  ].forEach((field) => {
    ret = ret && transactions[0].hasOwnProperty(field);
  });

  // Assert that the transfers is an array
  ret = ret && Array.isArray(transactions[0].transfers);

  // Assert that the transfers array has the mandatory fields
  if (ret) {
    ['account', 'amount'].forEach((field) => {
      ret = ret && transactions[0].transfers[0].hasOwnProperty(field);
    });
  }
  if (!ret) {
    console.log(`validateFields check failed: A mandatory parameter is missing`);
  }
  return ret;
};

/**
 * Validate the order of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function (transactions, order) {
  let ret = true;
  let offenderTx = null;
  let offenderVal = null;
  let direction = order === 'desc' ? -1 : 1;
  let val = transactions[0].consensus_timestamp - direction;
  for (const tx of transactions) {
    if (val * direction > tx.consensus_timestamp * direction) {
      offenderTx = tx;
      offenderVal = val;
      ret = false;
    }
    val = tx.consensus_timestamp;
  }
  if (!ret) {
    console.log(
      `validateOrder check failed: ${offenderTx.consensus_timestamp} - previous timestamp ${offenderVal} Order  ${order}`
    );
  }
  return ret;
};

/**
 * This is the list of individual tests. Each test validates one query parameter
 * such as timestamp=1234 or account.id=gt:5678.
 * Definition of each test consists of the url string that is used in the query, and an
 * array of checks to be performed on the resultant SQL query.
 * These individual tests can be combined to form complex combinations as shown in the
 * definition of combinedTests below.
 * NOTE: To add more tests, just give it a unique name, specify the url query string, and
 * a set of checks you would like to perform on the resultant SQL query.
 */
const singleTests = {
  timestamp_lowerlimit: {
    urlparam: `timestamp=gte:${timeOneHourAgo}`,
    checks: [{field: 'consensus_ns', operator: '>=', value: timeOneHourAgo + '000000000'}],
    checkFunctions: [
      {func: validateTsRange, args: [timeOneHourAgo, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  timestamp_higherlimit: {
    urlparam: `timestamp=lt:${timeNow}`,
    checks: [{field: 'consensus_ns', operator: '<', value: timeNow + '000000000'}],
    checkFunctions: [
      {func: validateTsRange, args: [0, timeNow]},
      {func: validateFields, args: []},
    ],
  },
  accountid_lowerlimit: {
    urlparam: 'account.id=gte:0.0.1111',
    checks: [{field: 'entity_id', operator: '>=', value: '1111'}],
    checkFunctions: [
      {func: validateAccNumRange, args: [1111, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  accountid_higherlimit: {
    urlparam: 'account.id=lt:0.0.2222',
    checks: [{field: 'entity_id', operator: '<', value: '2222'}],
    checkFunctions: [
      {func: validateAccNumRange, args: [0, 2222]},
      {func: validateFields, args: []},
    ],
  },
  accountid_equal: {
    urlparam: 'account.id=0.0.3333',
    checks: [{field: 'entity_id', operator: '=', value: '3333'}],
    checkFunctions: [{func: validateAccNumRange, args: [3333, 3333]}],
  },
  limit: {
    urlparam: 'limit=99',
    checks: [{field: 'limit', operator: '=', value: '99'}],
    checkFunctions: [
      {func: validateLen, args: [99]},
      {func: validateFields, args: []},
    ],
  },
  order_asc: {
    urlparam: 'order=asc',
    checks: [{field: 'order', operator: '=', value: 'asc'}],
    checkFunctions: [{func: validateOrder, args: ['asc']}],
  },
  order_desc: {
    urlparam: 'order=desc',
    checks: [{field: 'order', operator: '=', value: 'desc'}],
    checkFunctions: [{func: validateOrder, args: ['desc']}],
  },
  result_fail: {
    urlparam: 'result=fail',
    checks: [{field: 'result', operator: '!=', value: '' + utils.TRANSACTION_RESULT_SUCCESS}],
  },
  result_success: {
    urlparam: 'result=success',
    checks: [{field: 'result', operator: '=', value: '' + utils.TRANSACTION_RESULT_SUCCESS}],
  },
};

/**
 * This list allows creation of combinations of individual tests to exercise presence
 * of mulitple query parameters. The combined query string is created by adding the query
 * strings of each of the individual tests, and all checks from all of the individual tests
 * are performed on the resultant SQL query
 * NOTE: To add more combined tests, just add an entry to following array using the
 * individual (single) tests in the object above.
 */
const combinedTests = [
  ['timestamp_lowerlimit', 'timestamp_higherlimit'],
  ['accountid_lowerlimit', 'accountid_higherlimit'],
  ['timestamp_lowerlimit', 'timestamp_higherlimit', 'accountid-lowerlimit', 'accountid_higherlimit'],
  ['timestamp_lowerlimit', 'accountid_higherlimit', 'limit'],
  ['timestamp_higherlimit', 'accountid_lowerlimit', 'result_fail'],
  ['limit', 'result_success', 'order_asc'],
];

// Start of tests
describe('Transaction tests', () => {
  let api = '/api/v1/transactions';

  // First, execute the single tests
  for (const [name, item] of Object.entries(singleTests)) {
    test(`Transactions single test: ${name} - URL: ${item.urlparam}`, async () => {
      let response = await request(server).get([api, item.urlparam].join('?'));

      expect(response.status).toEqual(200);
      const transactions = JSON.parse(response.text).transactions;
      const parsedParams = JSON.parse(response.text).sqlQuery.parsedparams;

      // Verify the sql query against each of the specified checks
      expect(parsedParams).toEqual(expect.arrayContaining(item.checks));

      // Execute the specified functions to validate the output from the REST API
      let check = true;
      if (item.hasOwnProperty('checkFunctions')) {
        for (const cf of item.checkFunctions) {
          check = check && cf.func.apply(null, [transactions].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // And now, execute the combined tests
  for (const combination of combinedTests) {
    // Combine the individual (single) checks as specified in the combinedTests array
    let combtest = {urls: [], checks: [], checkFunctions: [], names: ''};
    for (const testname of combination) {
      if (testname in singleTests) {
        combtest.names += testname + ' ';
        combtest.urls.push(singleTests[testname].urlparam);
        combtest.checks = combtest.checks.concat(singleTests[testname].checks);
        combtest.checkFunctions = combtest.checkFunctions.concat(
          singleTests[testname].hasOwnProperty('checkFunctions') ? singleTests[testname].checkFunctions : []
        );
      }
    }
    const comburl = combtest.urls.join('&');

    test(`Transactions combination test: ${combtest.names} - URL: ${comburl}`, async () => {
      let response = await request(server).get([api, comburl].join('?'));
      expect(response.status).toEqual(200);
      const parsedParams = JSON.parse(response.text).sqlQuery.parsedparams;
      const transactions = JSON.parse(response.text).transactions;

      // Verify the sql query against each of the specified checks
      expect(parsedParams).toEqual(expect.arrayContaining(combtest.checks));

      // Execute the specified functions to validate the output from the REST API
      let check = true;
      if (combtest.hasOwnProperty('checkFunctions')) {
        for (const cf of combtest.checkFunctions) {
          check = check && cf.func.apply(null, [transactions].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // Negative testing
  testutils.testBadParams(request, server, api, 'timestamp', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.id', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'limit', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'order', testutils.badParamsList());
});
