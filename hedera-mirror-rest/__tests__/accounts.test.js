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

const request = require('supertest');
const server = require('../server');
const testutils = require('./testutils.js');

beforeAll(async () => {
  jest.setTimeout(1000);
});

afterAll(() => {
});

// Validation functions
/**
 * Validate length of the accounts returned by the api
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function (accounts, len) {
  return accounts.accounts.length === len;
};

/**
 * Validate the range of account ids in the accounts returned by the api
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function (accounts, low, high) {
  let ret = true;
  let offender = null;
  for (const acc of accounts.accounts) {
    const accNum = acc.account.split('.')[2];
    if (accNum < low || accNum > high) {
      offender = accNum;
      ret = false;
    }
  }
  if (!ret) {
    console.log(`validateAccNumRange check failed: ${offender} is not between ${low} and ${high}`);
  }
  return ret;
};


/**
 * Validate that account ids in the accounts returned by the api are in the list of valid account ids
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {Array} list of valid account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumInArray = function (accounts, potentialValues) {
  let ret = true;
  let offender = null;
  for (const acc of accounts.accounts) {
    const accNum = acc.account.split('.')[2];
    if (!potentialValues.includes(Number(accNum))) {
      offender = accNum;
      ret = false;
    }
  }
  if (!ret) {
    console.log(`validateAccNumRange check failed: ${offender} is not in ${potentialValues}`);
  }
  return ret;
};

/**
 * Validate the range of account balances in the accounts returned by the api
 * @param {Array} balances Array of accounts returned by the rest api
 * @param {Number} low Expected low limit of the balances
 * @param {Number} high Expected high limit of the balances
 * @return {Boolean}  Result of the check
 */
const validateBalanceRange = function (accounts, low, high) {
  let ret = true;
  let offender = null;
  for (const acc of accounts.accounts) {
    if (acc.balance.balance < low || acc.balance.balance > high) {
      offender = acc.balance.balance;
      ret = false;
    }
  }
  if (!ret) {
    console.log(`validateBalanceRange check failed: ${offender} is not between ${low} and ${high}`);
  }
  return ret;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} accounts Array of accounts returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function (accounts) {
  let ret = true;

  // Assert that the accounts is an array
  ret = ret && Array.isArray(accounts.accounts);

  // Assert that all mandatory fields are present in the response
  ['balance', 'account', 'expiry_timestamp', 'auto_renew_period', 'key', 'deleted'].forEach((field) => {
    ret = ret && accounts.accounts[0].hasOwnProperty(field);
  });

  // Assert that the balances object has the mandatory fields
  if (ret) {
    ['timestamp', 'balance'].forEach((field) => {
      ret = ret && accounts.accounts[0].balance.hasOwnProperty(field);
    });
  }

  if (!ret) {
    console.log(`validateFields check failed: A mandatory parameter is missing`);
  }
  return ret;
};

/**
 * Validate the order of timestamps in the accounts returned by the api
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function (accounts, order) {
  let ret = true;
  let offenderAcc = null;
  let offenderVal = null;
  let direction = order === 'desc' ? -1 : 1;
  const toAccNum = (acc) => acc.split('.')[2];
  let val = toAccNum(accounts.accounts[0].account) - direction;
  for (const acc of accounts.accounts) {
    if (val * direction > toAccNum(acc.account) * direction) {
      offenderAcc = toAccNum(acc);
      offenderVal = val;
      ret = false;
    }
    val = toAccNum(acc.account);
  }
  if (!ret) {
    console.log(`validateOrder check failed: ${offenderAcc} - previous account number ${offenderVal} Order  ${order}`);
  }
  return ret;
};

/**
 * This is the list of individual tests. Each test validates one query parameter
 * such as timestamp=1234 or account.id=gt:5678.
 * Definition of each test consists of the url string that is used in the query, and an
 * array of checks to be performed on the resultant SQL query.
 * These individual tests can be combined to form complex combinations as shown in the
 * definition of combinedtests below.
 * NOTE: To add more tests, just give it a unique name, specifiy the url query string, and
 * a set of checks you would like to perform on the resultant SQL query.
 */
const singletests = {
  accountid_lowerlimit: {
    urlparam: 'account.id=gte:0.0.1111',
    checks: [{field: 'account_id', operator: '>=', value: 1111}],
    checkFunctions: [
      {func: validateAccNumRange, args: [1111, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  accountid_higherlimit: {
    urlparam: 'account.id=lt:0.0.2222',
    checks: [{field: 'account_id', operator: '<', value: 2222}],
    checkFunctions: [
      {func: validateAccNumRange, args: [0, 2222]},
      {func: validateFields, args: []},
    ],
  },
  accountid_equal: {
    urlparam: 'account.id=0.0.3333',
    checks: [{field: 'account_id', operator: 'in', value: 3333}],
    checkFunctions: [
      {func: validateAccNumInArray, args: [[3333]]},
      {func: validateFields, args: []},
    ],
  },
  accountid_multiple: {
    urlparam: 'account.id=0.0.3333&account.id=0.0.3334',
    checks: [
      {field: 'account_id', operator: 'in', value: '3333'},
      {field: 'account_id', operator: 'in', value: '3334'},
    ],
    checkFunctions: [
      {func: validateAccNumInArray, args: [[3333, 3334]]},
      {func: validateFields, args: []},
    ],
  },
  accountbalance_lowerlimit: {
    urlparam: 'account.balance=gte:54321',
    checks: [{field: 'balance', operator: '>=', value: 54321}],
    checkFunctions: [
      {func: validateBalanceRange, args: [54321, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  accountbalance_higherlimit: {
    urlparam: 'account.balance=lt:5432100',
    checks: [{field: 'balance', operator: '<', value: 5432100}],
    checkFunctions: [
      {func: validateBalanceRange, args: [0, 5432100]},
      {func: validateFields, args: []},
    ],
  },
  accountpublickey_equal: {
    urlparam: 'account.publickey=6bd7b31fd59fc1b51314ac90253dfdbffa18eec48c00051e92635fe964a08c9b',
    checks: [
      {
        field: 'ed25519_public_key_hex',
        operator: '=',
        value: '6bd7b31fd59fc1b51314ac90253dfdbffa18eec48c00051e92635fe964a08c9b',
      },
    ],
  },
  limit: {
    urlparam: 'limit=99',
    checks: [{field: 'limit', operator: '=', value: 99}],
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
};

/**
 * This list allows creation of combinations of individual tests to exercise presence
 * of mulitple query parameters. The combined query string is created by adding the query
 * strings of each of the individual tests, and all checks from all of the individual tests
 * are performed on the resultant SQL query
 * NOTE: To add more combined tests, just add an entry to following array using the
 * individual (single) tests in the object above.
 */
const combinedtests = [
  ['accountid_lowerlimit', 'accountid_higherlimit'],
  ['accountid_lowerlimit', 'accountbalance_higherlimit'],
  ['accountbalance_lowerlimit', 'accountbalance_higherlimit'],
  ['accountid_higherlimit', 'accountbalance_lowerlimit', 'limit'],
  ['accountid_equal', 'order_desc'],
  ['limit', 'order_desc'],
];

describe('Accounts tests', () => {
  let api = '/api/v1/accounts';

  // First, execute the single tests
  for (const [name, item] of Object.entries(singletests)) {
    test(`Accounts single test: ${name} - URL: ${item.urlparam}`, async () => {
      let response = await request(server).get([api, item.urlparam].join('?'));

      expect(response.status).toEqual(200);
      const accounts = JSON.parse(response.text);
      const parsedparams = JSON.parse(response.text).sqlQuery.parsedparams;

      // Verify the sql query against each of the specified checks
      let check = true;
      for (const checkitem of item.checks) {
        check = check && testutils.checkSql(parsedparams, checkitem);
      }
      expect(check).toBeTruthy();

      // Execute the specified functions to validate the output from the REST API
      check = true;
      if (item.hasOwnProperty('checkFunctions')) {
        for (const cf of item.checkFunctions) {
          check = check && cf.func.apply(null, [accounts].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // And now, execute the combined tests
  for (const combination of combinedtests) {
    // Combine the individual (single) checks as specified in the combinedtests array
    let combtest = {urls: [], checks: [], names: ''};
    for (const testname of combination) {
      if (testname in singletests) {
        combtest.names += testname + ' ';
        combtest.urls.push(singletests[testname].urlparam);
        combtest.checks = combtest.checks.concat(singletests[testname].checks);
      }
    }
    const comburl = combtest.urls.join('&');
    test(`Accounts combinationn test: ${combtest.names} - URL: ${comburl}`, async () => {
      let response = await request(server).get([api, comburl].join('?'));
      expect(response.status).toEqual(200);
      const accounts = JSON.parse(response.text);
      const parsedparams = JSON.parse(response.text).sqlQuery.parsedparams;

      // Verify the sql query against each of the specified checks
      let check = true;
      for (const checkitem of combtest.checks) {
        check = check && testutils.checkSql(parsedparams, checkitem);
      }
      expect(check).toBeTruthy();

      // Execute the specified functions to validate the output from the REST API
      check = true;
      if (combtest.hasOwnProperty('checkFunctions')) {
        for (const cf of combtest.checkFunctions) {
          check = check && cf.func.apply(null, [accounts].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // Negative testing
  testutils.testBadParams(request, server, api, 'timestamp', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.id', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.balance', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.publickey', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'limit', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'order', testutils.badParamsList());
});
