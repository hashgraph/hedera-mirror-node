/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

const acctestutils = require('./monitortest_utils.js');
const config = require('../../config.js');
const math = require('mathjs');
const balancesPath = '/balances';
const maxLimit = config.api.maxLimit;
const balanceFileUpdateRefreshTime = 900;

/**
 * Makes a call to the rest-api and returns the balances object from the response
 * @param {String} pathandquery
 * @return {Object} Transactions object from response
 */
const getBalances = (pathandquery, currentTestResult) => {
  return acctestutils
    .getAPIResponse(pathandquery)
    .then(json => {
      return json.balances;
    })
    .catch(error => {
      currentTestResult.failureMessages.push(error);
    });
};

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Balance JSON object
 */
const checkMandatoryParams = entry => {
  let check = true;
  ['account', 'balance'].forEach(field => {
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
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, balancesPath);
  currentTestResult.url = url;
  let balances = await getBalances(url, currentTestResult);

  if (undefined === balances) {
    var message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== maxLimit) {
    var message = `balances.length of ${balances.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let mandatoryParamCheck = checkMandatoryParams(balances[0]);
  if (mandatoryParamCheck == false) {
    var message = `balance object is missing some mandatory fields`;
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
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${balancesPath}?limit=1`);
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
    var message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== 1) {
    var message = `balances.length of ${balances.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let plusOne = math.add(math.bignumber(balancesResponse.timestamp), math.bignumber(1));
  let minusOne = math.subtract(math.bignumber(balancesResponse.timestamp), math.bignumber(1));
  let paq = `${balancesPath}?timestamp=gt:${minusOne.toString()}` + `&timestamp=lt:${plusOne.toString()}&limit=1`;

  url = acctestutils.getUrl(server, paq);
  currentTestResult.url = url;
  balances = await getBalances(url, currentTestResult);

  if (undefined === balances) {
    var message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== 1) {
    var message = `balances.length of ${balances.length} was expected to be 1`;
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
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${balancesPath}?limit=10`);
  currentTestResult.url = url;
  let balances = await getBalances(url, currentTestResult);

  if (undefined === balances) {
    var message = `balances is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (balances.length !== 10) {
    var message = `balances.length of ${balances.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return currentTestResult;
  }

  let mandatoryParamCheck = checkMandatoryParams(balances[0]);
  if (mandatoryParamCheck == false) {
    var message = `account object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return currentTestResult;
  }

  var highestAcc = 0;
  for (let acc of balances) {
    var accnum = acctestutils.toAccNum(acc.account);
    if (accnum > highestAcc) {
      highestAcc = accnum;
    }
  }

  url = acctestutils.getUrl(server, `${balancesPath}?account.id=${acctestutils.fromAccNum(highestAcc)}`);
  currentTestResult.url = url;
  let singleBalance = await getBalances(url, currentTestResult);

  if (undefined === singleBalance || undefined === singleBalance[0]) {
    var message = `singleBalance is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let check = false;
  if (singleBalance[0].account === acctestutils.fromAccNum(highestAcc)) {
    check = true;
  }

  if (check == false) {
    var message = `Highest acc check was not found`;
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
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${balancesPath}?limit=1`);
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
    var message = `balance was stale, ${delta} seconds old`;
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
  var tests = [];
  tests.push(getBalancesCheck(server, classResults));
  tests.push(getBalancesWithTimeAndLimitParams(server, classResults));
  tests.push(getSingleBalanceById(server, classResults));
  tests.push(checkBalanceFreshness(server, classResults));

  return Promise.all(tests);
};

module.exports = {
  runBalanceTests: runBalanceTests
};
