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
const acctestutils = require('./monitortest_utils');

const balancesPath = '/balances';
const balanceFileUpdateRefreshTime = 900;
const resource = 'balance';

/**
 * Makes a call to the rest-api and returns the balances object from the response
 * @param {String} url
 * @param {Object} currentTestResult
 * @return {Object} Transactions object from response
 */
const getBalances = (url, currentTestResult) => {
  return acctestutils
    .getAPIResponse(url)
    .then((json) => {
      return json.balances;
    })
    .catch((error) => {
      currentTestResult.failureMessages.push(error);
    });
};

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Balance JSON object
 */
const checkMandatoryParams = (entry) => {
  let check = true;
  ['account', 'balance'].forEach((field) => {
    check = check && entry.hasOwnProperty(field);
  });

  return check;
};

/**
 * Verify base balances call
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getBalancesCheck = async (server, classResults) => {
  const currentTestResult = acctestutils.getMonitorTestResult();

  const query = {};
  const {maxLimit, isGlobal} = acctestutils.getMaxLimit(resource);
  if (!isGlobal) {
    query.limit = maxLimit;
  }

  const url = acctestutils.getUrl(server, balancesPath, query);
  currentTestResult.url = url;
  const balances = await getBalances(url, currentTestResult);

  if (undefined === balances) {
    const message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== maxLimit) {
    const message = `balances.length of ${balances.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (!checkMandatoryParams(balances[0])) {
    const message = `balance object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called balances and performed account check`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify balances call with time and limit query params provided
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getBalancesWithTimeAndLimitParams = async (server, classResults) => {
  const currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, balancesPath, {limit: 1});
  currentTestResult.url = url;
  let balancesResponse;
  try {
    balancesResponse = await acctestutils.getAPIResponse(url);
  } catch (error) {
    currentTestResult.failureMessages.push(error);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let balances = balancesResponse.balances;

  if (undefined === balances) {
    const message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== 1) {
    const message = `balances.length of ${balances.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const plusOne = math.add(math.bignumber(balancesResponse.timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(balancesResponse.timestamp), math.bignumber(1));
  url = acctestutils.getUrl(server, balancesPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  currentTestResult.url = url;
  balances = await getBalances(url, currentTestResult);

  if (undefined === balances) {
    const message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== 1) {
    const message = `balances.length of ${balances.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called balances with time and limit params`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify single balance can be retrieved
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getSingleBalanceById = async (server, classResults) => {
  const currentTestResult = acctestutils.getMonitorTestResult();

  const limit = 10;
  let url = acctestutils.getUrl(server, balancesPath, {limit});
  currentTestResult.url = url;
  const balances = await getBalances(url, currentTestResult);

  if (undefined === balances) {
    const message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== limit) {
    const message = `balances.length of ${balances.length} is less than limit ${limit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (!checkMandatoryParams(balances[0])) {
    const message = `account object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const highestAcc = _.max(_.map(balances, (balance) => acctestutils.toAccNum(balance.account)));

  url = acctestutils.getUrl(server, balancesPath, {'account.id': acctestutils.fromAccNum(highestAcc)});
  currentTestResult.url = url;
  const singleBalance = await getBalances(url, currentTestResult);

  if (undefined === singleBalance || undefined === singleBalance[0]) {
    const message = `singleBalance is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleBalance[0].account !== acctestutils.fromAccNum(highestAcc)) {
    const message = `Highest acc check was not found`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called balances and performed account check`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verfiy the freshness of balances returned by the api
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const checkBalanceFreshness = async (server, classResults) => {
  const currentTestResult = acctestutils.getMonitorTestResult();

  const url = acctestutils.getUrl(server, balancesPath, {limit: 1});
  currentTestResult.url = url;
  let balancesResponse;
  try {
    balancesResponse = await acctestutils.getAPIResponse(url);
  } catch (error) {
    currentTestResult.failureMessages.push(error);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  // Check for freshness of data
  const txSec = balancesResponse.timestamp.split('.')[0];
  const currSec = Math.floor(new Date().getTime() / 1000);
  const delta = currSec - txSec;
  const freshnessThreshold = balanceFileUpdateRefreshTime * 2;

  if (delta > freshnessThreshold) {
    const message = `balance was stale, ${delta} seconds old`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully retrieved balance from with ${freshnessThreshold} seconds ago`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Run all tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runBalanceTests = async (server, classResults) => {
  const tests = [];
  tests.push(getBalancesCheck(server, classResults));
  tests.push(getBalancesWithTimeAndLimitParams(server, classResults));
  tests.push(getSingleBalanceById(server, classResults));
  tests.push(checkBalanceFreshness(server, classResults));

  return Promise.all(tests);
};

module.exports = {
  runBalanceTests,
};
