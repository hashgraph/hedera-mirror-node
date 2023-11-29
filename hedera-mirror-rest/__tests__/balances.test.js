/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import request from 'supertest';
import sinon from 'sinon';

import balances from '../balances';
import {MAX_LONG} from '../constants';
import EntityId from '../entityId';
import server from '../server';
import * as testutils from './testutils';
import {opsMap} from '../utils';

// Validation functions
/**
 * Validate length of the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function (balances, len) {
  return balances.balances.length === len;
};

/**
 * Validate the range of account ids in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function (balances, low, high) {
  let ret = true;
  let offender = null;
  for (const bal of balances.balances) {
    const accNum = bal.account.split('.')[2];
    if (accNum < low || accNum > high) {
      offender = accNum;
      ret = false;
    }
  }
  if (!ret) {
    logger.warn(`validateAccNumRange check failed: ${offender} is not between ${low} and  ${high}`);
  }
  return ret;
};

/**
 * Validate that account ids in the balances returned by the api are in the list of valid account ids
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Array} list of valid account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumInArray = function (balances, ...potentialValues) {
  return testutils.validateAccNumInArray(balances.balances, potentialValues);
};

/**
 * Validate the range of account balances in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} low Expected low limit of the balances
 * @param {Number} high Expected high limit of the balances
 * @return {Boolean}  Result of the check
 */
const validateBalanceRange = function (balances, low, high) {
  let ret = true;
  let offender = null;
  for (const bal of balances.balances) {
    if (bal.balance < low || bal.balance > high) {
      offender = bal.balance;
      ret = false;
    }
  }
  if (!ret) {
    logger.warn(`validateBalanceRange check failed: ${offender} is not between ${low} and  ${high}`);
  }
  return ret;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} balances Array of balances returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function (balances) {
  let ret = true;

  // Assert that the balances is an array
  ret = ret && Array.isArray(balances.balances);

  // Assert that all mandatory fields are present in the response
  ['timestamp', 'balances'].forEach((field) => {
    ret = ret && balances.hasOwnProperty(field);
  });

  // Assert that the balances array has the mandatory fields
  if (ret) {
    ['account', 'balance'].forEach((field) => {
      ret = ret && balances.balances[0].hasOwnProperty(field);
    });
  }

  if (!ret) {
    logger.warn(`validateFields check failed: A mandatory parameter is missing`);
  }
  return ret;
};

/**
 * Validate the order of timestamps in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function (balances, order) {
  let ret = true;
  let offenderAcc = null;
  let offenderVal = null;
  const direction = order === 'desc' ? -1 : 1;
  const toAccNum = (acc) => EntityId.parse(acc).num;
  let val = toAccNum(balances.balances[0].account) - direction;
  for (const bal of balances.balances) {
    if (val * direction > toAccNum(bal.account) * direction) {
      offenderAcc = toAccNum(bal);
      offenderVal = toAccNum(val);
      ret = false;
    }
    val = bal;
  }
  if (!ret) {
    logger.warn(`validateOrder check failed: ${offenderAcc} - previous account number ${offenderVal} Order  ${order}`);
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
      {func: validateAccNumInArray, args: [3333]},
      {func: validateFields, args: []},
    ],
  },
  accountid_multiple: {
    urlparam: 'account.id=0.0.3333&account.id=0.0.3334',
    checks: [
      {field: 'account_id', operator: 'in', value: '3333'},
      {field: 'account_id', operator: 'in', value: '3334'},
    ],
    checkFunctions: [{func: validateAccNumInArray, args: [3333, 3334]}],
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
        field: 'public_key',
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
  ['accountid_equal', 'accountbalance_lowerlimit', 'limit'],
  ['limit', 'order_asc'],
];

// Start of tests
describe('Balances tests', () => {
  const api = '/api/v1/balances';

  // First, execute the single tests
  for (const [name, item] of Object.entries(singletests)) {
    test(`Balances single test: ${name} - URL: ${item.urlparam}`, async () => {
      const response = await request(server).get([api, item.urlparam].join('?'));

      expect(response.status).toEqual(200);
      const balances = JSON.parse(response.text);
      const {parsedparams} = JSON.parse(response.text).sqlQuery;

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
          check = check && cf.func.apply(null, [balances].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // And now, execute the combined tests
  for (const combination of combinedtests) {
    // Combine the individual (single) checks as specified in the combinedtests array
    const combtest = {urls: [], checks: [], names: ''};
    for (const testname of combination) {
      if (testname in singletests) {
        combtest.names += `${testname} `;
        combtest.urls.push(singletests[testname].urlparam);
        combtest.checks = combtest.checks.concat(singletests[testname].checks);
      }
    }
    const comburl = combtest.urls.join('&');
    test(`Balances combination test: ${combtest.names} - URL: ${comburl}`, async () => {
      const response = await request(server).get([api, comburl].join('?'));
      expect(response.status).toEqual(200);
      const balances = JSON.parse(response.text);
      const {parsedparams} = JSON.parse(response.text).sqlQuery;

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
          check = check && cf.func.apply(null, [balances].concat(cf.args));
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

describe('getOptimizedTimestampRange', () => {
  let clock;
  // Set now, so Date.now() returns the expected epoch millis
  const now = Date.parse('2022-10-15T08:10:15.333Z');

  beforeAll(() => {
    clock = sinon.useFakeTimers({now});
  });

  afterAll(() => {
    clock.restore();
  });

  const toNs = (value) => {
    if (typeof value !== 'string') {
      return value;
    }

    return BigInt(Date.parse(value)) * 10n ** 6n;
  };

  const convertBound = (bound) => {
    if (typeof bound !== 'object') {
      return toNs(bound);
    }

    const {value, delta} = bound;
    return toNs(value) + (delta ?? 0n);
  };

  const {lt, lte, gt, gte, ne} = opsMap;
  const spec = [
    {operators: [], params: [], expected: {lowerBound: '2022-09-01Z', upperBound: MAX_LONG, neParams: []}},
    {operators: [gt, lt], params: ['2022-10-11Z', '2022-10-10Z'], expected: {}},
    {
      operators: [gte, lte],
      params: ['2022-01-05Z', '2022-07-12Z'],
      expected: {lowerBound: '2022-01-05Z', upperBound: '2022-07-12Z', neParams: []},
    },
    {
      operators: [gt],
      params: ['2022-10-10Z'],
      expected: {lowerBound: {value: '2022-10-10Z', delta: 1n}, upperBound: MAX_LONG, neParams: []},
    },
    {
      operators: [gt],
      params: ['2022-01-01Z'],
      expected: {lowerBound: '2022-09-01Z', upperBound: MAX_LONG, neParams: []},
    },
    {
      operators: [gte],
      params: ['2022-10-10Z'],
      expected: {lowerBound: '2022-10-10Z', upperBound: MAX_LONG, neParams: []},
    },
    {
      operators: [lt],
      params: ['2022-12-15Z'],
      expected: {lowerBound: '2022-09-01Z', upperBound: {value: '2022-12-15Z', delta: -1n}, neParams: []},
    },
    {
      operators: [lt, ne],
      params: ['2022-10-10Z', '2022-10-02Z'],
      expected: {lowerBound: '2022-09-01Z', upperBound: {value: '2022-10-10Z', delta: -1n}, neParams: ['2022-10-02Z']},
    },
    {
      operators: [lte],
      params: ['2022-10-10Z'],
      expected: {lowerBound: '2022-09-01Z', upperBound: '2022-10-10Z', neParams: []},
    },
    {
      operators: [lte],
      params: ['2022-09-20Z'],
      expected: {lowerBound: '2022-08-01Z', upperBound: '2022-09-20Z', neParams: []},
    },
    {
      operators: [gt, gte, lt, lte],
      params: ['2022-01-05Z', '2022-02-10Z', '2022-10-02Z', '2022-09-20Z'],
      expected: {lowerBound: '2022-02-10Z', upperBound: '2022-09-20Z', neParams: []},
    },
  ].map((s) => ({
    query: s.operators.map((op) => `consensus_timestamp ${op} ?`).join(' and '),
    params: s.params.map(toNs),
    expected: {
      lowerBound: convertBound(s.expected.lowerBound),
      upperBound: convertBound(s.expected.upperBound),
      neParams: s.expected.neParams?.map(toNs),
    },
  }));

  test.each(spec)('$query', ({query, params, expected}) => {
    const actual = balances.getOptimizedTimestampRange(query, params);
    expect(actual).toEqual(expected);
  });
});
