/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import request from 'supertest';

import subject from '../accounts';
import base32 from '../base32';
import * as constants from '../constants';
import server from '../server';
import * as testutils from './testutils';

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
    logger.warn(`validateAccNumRange check failed: ${offender} is not between ${low} and ${high}`);
  }
  return ret;
};

/**
 * Validate that account ids in the accounts returned by the api are in the list of valid account ids
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {Array} list of valid account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumInArray = function (accounts, ...potentialValues) {
  return testutils.validateAccNumInArray(accounts.accounts, potentialValues);
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
    logger.warn(`validateBalanceRange check failed: ${offender} is not between ${low} and ${high}`);
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
    logger.warn(`validateFields check failed: A mandatory parameter is missing`);
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
  const direction = order === 'desc' ? -1 : 1;
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
    checks: [{field: 'id', operator: '>=', value: 1111}],
    checkFunctions: [
      {func: validateAccNumRange, args: [1111, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  accountid_higherlimit: {
    urlparam: 'account.id=lt:0.0.2222',
    checks: [{field: 'id', operator: '<', value: 2222}],
    checkFunctions: [
      {func: validateAccNumRange, args: [0, 2222]},
      {func: validateFields, args: []},
    ],
  },
  accountid_equal: {
    urlparam: 'account.id=0.0.3333',
    checks: [{field: 'id', operator: 'in', value: 3333}],
    checkFunctions: [
      {func: validateAccNumInArray, args: [3333]},
      {func: validateFields, args: []},
    ],
  },
  accountid_multiple: {
    urlparam: 'account.id=0.0.3333&account.id=0.0.3334',
    checks: [
      {field: 'id', operator: 'in', value: '3333'},
      {field: 'id', operator: 'in', value: '3334'},
    ],
    checkFunctions: [
      {func: validateAccNumInArray, args: [3333, 3334]},
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
  ['accountid_lowerlimit', 'accountbalance_higherlimit'],
  ['accountbalance_lowerlimit', 'accountbalance_higherlimit'],
  ['accountid_higherlimit', 'accountbalance_lowerlimit', 'limit'],
  ['accountid_equal', 'order_desc'],
  ['limit', 'order_desc'],
];

describe('Accounts tests', () => {
  const api = '/api/v1/accounts';

  // First, execute the single tests
  for (const [name, item] of Object.entries(singletests)) {
    test(`Accounts single test: ${name} - URL: ${item.urlparam}`, async () => {
      const response = await request(server).get([api, item.urlparam].join('?'));

      expect(response.status).toEqual(200);
      const accounts = JSON.parse(response.text);
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
          check = check && cf.func.apply(null, [accounts].concat(cf.args));
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
    test(`Accounts combination test: ${combtest.names} - URL: ${comburl}`, async () => {
      const response = await request(server).get([api, comburl].join('?'));
      expect(response.status).toEqual(200);
      const accounts = JSON.parse(response.text);
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
          check = check && cf.func.apply(null, [accounts].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // Negative testing
  testutils.testBadParams(request, server, api, 'account.id', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.balance', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.publickey', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'balance', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'limit', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'order', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'timestamp', testutils.badParamsList());
});

describe('processRow', () => {
  const inputAccount = {
    alias: base32.decode('WWDOGNX3TXHD2'),
    auto_renew_period: 7890000,
    balance: 123456789,
    consensus_timestamp: 9876500123456789n,
    created_timestamp: 10123456789n,
    decline_reward: false,
    ethereum_nonce: 1,
    evm_address: Buffer.from('ac384c53f03855fa1b3616052f8ba32c6c2a2fec', 'hex'),
    deleted: false,
    expiration_timestamp: '999876500123456789',
    id: 1250,
    key: Buffer.from([1, 2, 3, 4, 5, 6]),
    max_automatic_token_associations: 100,
    memo: 'entity memo',
    pending_reward: 10,
    receiver_sig_required: false,
    staked_account_id: 0,
    staked_node_id: -1,
    stake_period_start: -1,
    token_balances: [
      {
        token_id: '1500',
        balance: 2000,
      },
    ],
    type: constants.entityTypes.ACCOUNT,
  };
  const inputContract = {
    ...inputAccount,
    alias: null,
    memo: 'contract memo',
    receiver_sig_required: null,
    type: constants.entityTypes.CONTRACT,
  };
  const expectedAccount = {
    account: '0.0.1250',
    alias: 'WWDOGNX3TXHD2',
    auto_renew_period: 7890000,
    balance: {
      balance: 123456789,
      timestamp: '9876500.123456789',
      tokens: [
        {
          token_id: '0.0.1500',
          balance: 2000,
        },
      ],
    },
    created_timestamp: '10.123456789',
    decline_reward: false,
    deleted: false,
    ethereum_nonce: 1,
    evm_address: '0xac384c53f03855fa1b3616052f8ba32c6c2a2fec',
    expiry_timestamp: '999876500.123456789',
    key: {
      _type: 'ProtobufEncoded',
      key: '010203040506',
    },
    max_automatic_token_associations: 100,
    memo: 'entity memo',
    pending_reward: 10,
    receiver_sig_required: false,
    staked_account_id: null,
    staked_node_id: null,
    stake_period_start: null,
  };
  const expectedContract = {
    ...expectedAccount,
    alias: null,
    memo: 'contract memo',
    receiver_sig_required: null,
  };

  test('with balance', () => {
    expect(subject.processRow(inputAccount)).toEqual(expectedAccount);
  });

  test('undefined balance', () => {
    const inputBalanceUndefined = {
      ...inputAccount,
      balance: undefined,
      consensus_timestamp: undefined,
      token_balances: undefined,
    };
    const expectedNoBalance = {
      ...expectedAccount,
      balance: null,
    };
    expect(subject.processRow(inputBalanceUndefined)).toEqual(expectedNoBalance);
  });

  test('null balance', () => {
    const inputNullBalance = {
      ...inputAccount,
      balance: null,
      consensus_timestamp: null,
      token_balances: null,
    };
    const expectedNullBalance = {
      ...expectedAccount,
      balance: {
        balance: null,
        timestamp: null,
        tokens: [],
      },
    };
    expect(subject.processRow(inputNullBalance)).toEqual(expectedNullBalance);
  });

  test('null auto_renew_period', () => {
    expect(subject.processRow({...inputAccount, auto_renew_period: null})).toEqual({
      ...expectedAccount,
      auto_renew_period: null,
    });
  });

  test('null key', () => {
    expect(subject.processRow({...inputAccount, key: null})).toEqual({...expectedAccount, key: null});
  });

  test('null alias', () => {
    expect(subject.processRow({...inputAccount, alias: null})).toEqual({...expectedAccount, alias: null});
  });

  test('staked account id', () => {
    expect(subject.processRow({...inputAccount, staked_account_id: 10})).toEqual({
      ...expectedAccount,
      staked_account_id: '0.0.10',
    });
  });

  test('staked account id and stake period start', () => {
    expect(subject.processRow({...inputAccount, staked_account_id: 10, stake_period_start: 30})).toEqual({
      ...expectedAccount,
      staked_account_id: '0.0.10',
    });
  });

  test('null staked account id', () => {
    expect(subject.processRow({...inputAccount, staked_account_id: null})).toEqual(expectedAccount);
  });

  test('staked node id', () => {
    expect(subject.processRow({...inputAccount, staked_node_id: 2, stake_period_start: 30})).toEqual({
      ...expectedAccount,
      staked_node_id: 2,
      stake_period_start: '2592000.000000000',
    });

    expect(subject.processRow({...inputAccount, staked_node_id: 2, stake_period_start: 19162})).toEqual({
      ...expectedAccount,
      staked_node_id: 2,
      stake_period_start: '1655596800.000000000',
    });

    expect(subject.processRow({...inputAccount, staked_node_id: 2, stake_period_start: -1})).toEqual({
      ...expectedAccount,
      staked_node_id: 2,
      stake_period_start: null,
    });
  });

  test('default contract', () => {
    expect(subject.processRow(inputContract)).toEqual(expectedContract);
  });

  test('contract with parsable evm address', () => {
    expect(subject.processRow({...inputContract, evm_address: null})).toEqual({
      ...expectedContract,
      evm_address: '0x00000000000000000000000000000000000004e2',
    });
  });

  test('null created_timestamp', () => {
    expect(subject.processRow({...inputAccount, created_timestamp: null})).toEqual({
      ...expectedAccount,
      created_timestamp: null,
    });
  });
});

describe('getBalanceParamValue', () => {
  const key = constants.filterKeys.BALANCE;
  test('default', () => {
    expect(subject.getBalanceParamValue({})).toBeTrue();
  });
  test('single value true', () => {
    expect(subject.getBalanceParamValue({[key]: 'true'})).toBeTrue();
  });
  test('single value false', () => {
    expect(subject.getBalanceParamValue({[key]: 'false'})).toBeFalse();
  });
  test('array last value true', () => {
    expect(subject.getBalanceParamValue({[key]: ['false', 'true']})).toBeTrue();
  });
  test('array last value false', () => {
    expect(subject.getBalanceParamValue({[key]: ['true', 'false']})).toBeFalse();
  });
});
