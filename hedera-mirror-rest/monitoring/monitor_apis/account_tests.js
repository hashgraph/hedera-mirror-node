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

const _ = require('lodash');
const math = require('mathjs');
const {
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkAccountNumber,
  checkMandatoryParams,
  getAPIResponse,
  getMaxLimit,
  getUrl,
  fromAccNum,
  testRunner,
  toAccNum,
  CheckRunner,
} = require('./monitortest_utils');

const accountsPath = '/accounts';
const resource = 'account';
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
  const {maxLimit, isGlobal} = getMaxLimit(resource);
  let url = getUrl(server, accountsPath, !isGlobal ? {limit: maxLimit} : undefined);
  const accounts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'account is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: maxLimit,
      message: (accts, limit) => `accounts.length of ${accts.length}  is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAcc = _.max(_.map(accounts, (acct) => toAccNum(acct.account)));
  url = getUrl(server, accountsPath, {
    'account.id': highestAcc,
    type: 'credit',
    limit: 1,
  });
  const singleAccount = await getAPIResponse(url, jsonRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'singleAccount is undefined'})
    .withCheckSpec(checkAccountNumber, {accountNumber: highestAcc, message: 'Highest acc check was not found'})
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
  let url = getUrl(server, accountsPath, {limit: 1});
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
  const {maxLimit, isGlobal} = getMaxLimit(resource);
  let url = getUrl(server, accountsPath, !isGlobal ? {limit: maxLimit} : undefined);
  const accounts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: maxLimit,
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

  const highestAcc = _.max(_.map(accounts, (acct) => toAccNum(acct.account)));
  url = getUrl(server, `${accountsPath}/${fromAccNum(highestAcc)}`);
  const singleAccount = await getAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkAccountNumber, {accountNumber: highestAcc, message: 'Highest account number was not found'})
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
 * Run all tests in an asynchronous fashion waiting for all tests to complete
 * @param {String} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runAccountTests = async (server, classResults) => {
  const tests = [];
  const runTest = testRunner(server, classResults);
  tests.push(runTest(getAccountsWithAccountCheck));
  tests.push(runTest(getAccountsWithTimeAndLimitParams));
  tests.push(runTest(getSingleAccount));

  return Promise.all(tests);
};

module.exports = {
  runAccountTests,
};
