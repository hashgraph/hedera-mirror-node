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

import _ from 'lodash';
import config from './config';

import {
  checkAccountId,
  checkAPIResponseError,
  checkMandatoryParams,
  checkResourceFreshness,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

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
  const singleBalance = await getAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

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
  const now = new Date().getTime() / 1000;
  return checkResourceFreshness(server, balancesPath, resource, (data) => data.timestamp, undefined, {
    timestamp: now,
    limit: 1,
    order: 'desc',
  });
};

/**
 * Run all balance tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([runTest(getBalancesCheck), runTest(getSingleBalanceById), runTest(checkBalanceFreshness)]);
};

export default {
  resource,
  runTests,
};
