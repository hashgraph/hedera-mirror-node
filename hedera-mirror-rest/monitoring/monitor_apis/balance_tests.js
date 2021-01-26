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
  checkResourceFreshness,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} = require('./utils');

const balancesPath = '/balances';
const resource = 'balance';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'balances';
const mandatoryParams = ['account', 'balance'];

/**
 * Verify base balances call
 * @param {String} server API host endpoint
 */
const getBalancesCheck = async (server) => {
  const url = getUrl(server, balancesPath, {limit: resourceLimit});
  const balances = await getAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `balances.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'balance object is missing some mandatory fields',
    })
    .run(balances);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called balances and performed account check',
  };
};

/**
 * Verify balances call with time and limit query params provided
 * @param {String} server API host endpoint
 */
const getBalancesWithTimeAndLimitParams = async (server) => {
  let url = getUrl(server, balancesPath, {limit: 1});
  const resp = await getAPIResponse(url);
  let balances = resp instanceof Error ? resp : resp.balances;

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `balances.length of ${elements.length} was expected to be 1`,
    });
  let result = checkRunner.run(balances);
  if (!result.passed) {
    return {url, ...result};
  }

  const {timestamp} = resp;
  const plusOne = math.add(math.bignumber(timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(timestamp), math.bignumber(1));
  url = getUrl(server, balancesPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  balances = await getAPIResponse(url, jsonRespKey);

  result = checkRunner.run(balances);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called balances with time and limit params',
  };
};

/**
 * Verify single balance can be retrieved
 * @param {String} server API host endpoint
 */
const getSingleBalanceById = async (server) => {
  let url = getUrl(server, balancesPath, {limit: 10});
  const balances = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 10,
      message: (elements) => `balances.length of ${elements.length} was expected to be 10`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'balance object is missing some mandatory fields',
    })
    .run(balances);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(balances, (balance) => balance.account));
  url = getUrl(server, balancesPath, {'account.id': highestAccount});
  const singleBalance = await getAPIResponse(url, jsonRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `balances.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkAccountId, {accountId: highestAccount, message: 'Highest acc check was not found'})
    .run(singleBalance);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called balances and performed account check',
  };
};

/**
 * Verfiy the freshness of balances returned by the api
 * @param {String} server API host endpoint
 */
const checkBalanceFreshness = async (server) => {
  return checkResourceFreshness(server, balancesPath, resource, (data) => data.timestamp);
};

/**
 * Run all balance tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getBalancesCheck),
    runTest(getBalancesWithTimeAndLimitParams),
    runTest(getSingleBalanceById),
    runTest(checkBalanceFreshness),
  ]);
};

module.exports = {
  resource,
  runTests,
};
