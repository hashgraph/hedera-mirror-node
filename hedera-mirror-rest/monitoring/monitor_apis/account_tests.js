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

const _ = require('lodash');
const math = require('mathjs');
const config = require('./config');
const {
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkAccountId,
  checkMandatoryParams,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} = require('./utils');

const accountsPath = '/accounts';
const resource = 'account';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'accounts';
const mandatoryParams = [
  'balance',
  'account',
  'expiry_timestamp',
  'auto_renew_period',
  'key',
  'deleted',
  'balance.timestamp',
  'balance.balance',
];

/**
 * Verify base accounts call
 * Also ensure an account mentioned in the accounts can be confirmed as existing
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getAccountsWithAccountCheck = async (server) => {
  let url = getUrl(server, accountsPath, {limit: resourceLimit});
  const accounts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'account is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (accts, limit) => `accounts.length of ${accts.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(accounts, (acct) => acct.account));
  url = getUrl(server, accountsPath, {
    'account.id': highestAccount,
    type: 'credit',
    limit: 1,
  });
  const singleAccount = await getAPIResponse(url, jsonRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'singleAccount is undefined'})
    .withCheckSpec(checkAccountId, {accountId: highestAccount, message: 'Highest acc check was not found'})
    .run(singleAccount);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts and performed account check',
  };
};

/**
 * Verify accounts call with time and limit query params provided
 * @param {Object} server API host endpoint
 */
const getAccountsWithTimeAndLimitParams = async (server) => {
  let url = getUrl(server, accountsPath, {
    'account.balance': 'gte:0',
    limit: 1,
  });
  let accounts = await getAPIResponse(url, jsonRespKey);

  const checkRunnder = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (accts) => `accounts.length of ${accts.length} was expected to be 1`,
    });
  let result = checkRunnder.run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const plusOne = math.add(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  url = getUrl(server, accountsPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  accounts = await getAPIResponse(url, jsonRespKey);

  result = checkRunnder.run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts with time and limit params',
  };
};

/**
 * Verify single account can be retrieved
 * @param {Object} server API host endpoint
 */
const getSingleAccount = async (server) => {
  let url = getUrl(server, accountsPath, {limit: resourceLimit});
  const accounts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (accts, limit) => `accounts.length of ${accts.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(accounts, (acct) => acct.account));
  url = getUrl(server, `${accountsPath}/${highestAccount}`);
  const singleAccount = await getAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkAccountId, {accountId: highestAccount, message: 'Highest account number was not found'})
    .run(singleAccount);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts for single account',
  };
};

/**
 * Run all account tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getAccountsWithAccountCheck),
    runTest(getAccountsWithTimeAndLimitParams),
    runTest(getSingleAccount),
  ]);
};

module.exports = {
  resource,
  runTests,
};
