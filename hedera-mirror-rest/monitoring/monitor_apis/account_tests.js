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
const accountsPath = '/accounts';
const maxLimit = config.api.maxLimit;

/**
 * Makes a call to the rest-api and returns the accounts object from the response
 * @param {String} pathandquery
 * @return {Object} Accounts object from response
 */
const getAccounts = (pathandquery, currentTestResult) => {
  return acctestutils
    .getAPIResponse(pathandquery)
    .then(json => {
      return json.accounts;
    })
    .catch(error => {
      currentTestResult.failureMessages.push(error);
    });
};

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Account JSON object
 */
const checkMandatoryParams = entry => {
  let check = true;
  ['balance', 'account', 'expiry_timestamp', 'auto_renew_period', 'key', 'deleted'].forEach(field => {
    check = check && entry.hasOwnProperty(field);
  });

  ['timestamp', 'balance'].forEach(field => {
    check = check && entry.hasOwnProperty('balance') && entry.balance.hasOwnProperty(field);
  });

  return check;
};

/**
 * Verify base accounts call
 * Also ensure an account mentioned in the accounts can be confirmed as exisitng
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getAccountsWithAccountCheck = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, accountsPath);
  currentTestResult.url = url;
  let accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    var message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== maxLimit) {
    var message = `accounts.length of ${accounts.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    return currentTestResult;
  }

  let mandatoryParamCheck = checkMandatoryParams(accounts[0]);
  if (mandatoryParamCheck == false) {
    var message = `account object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  var highestAcc = 0;
  for (let acc of accounts) {
    var accnum = acctestutils.toAccNum(acc.account);
    if (accnum > highestAcc) {
      highestAcc = accnum;
    }
  }

  url = acctestutils.getUrl(server, `${accountsPath}?account.id=${highestAcc}&type=credit&limit=1`);
  currentTestResult.url = url;

  let singleAccount = await getAccounts(url, currentTestResult);

  if (undefined === singleAccount) {
    var message = `singleAccount is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleAccount.length !== 1) {
    var message = `singleAccount.length of ${singleAccount.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let check = false;
  if (singleAccount[0].account === acctestutils.fromAccNum(highestAcc)) {
    check = true;
  }

  if (check == false) {
    var message = `Highest acc check was not found`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called accounts and performed account check`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify accounts call with time and limit query params provided
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getAccountsWithTimeAndLimitParams = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${accountsPath}?limit=1`);
  currentTestResult.url = url;
  let accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    var message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== 1) {
    var message = `accounts.length of ${accounts.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let plusOne = math.add(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  let minusOne = math.subtract(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  let paq = `${accountsPath}?timestamp=gt:${minusOne.toString()}` + `&timestamp=lt:${plusOne.toString()}&limit=1`;

  url = acctestutils.getUrl(server, paq);
  currentTestResult.url = url;
  accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    var message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== 1) {
    var message = `accounts.length of ${accounts.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called accounts with time and limit params`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify single account can be retrieved
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getSingleAccount = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${accountsPath}`);
  currentTestResult.url = url;
  let accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    var message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== maxLimit) {
    var message = `accounts.length of ${accounts.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let mandatoryParamCheck = checkMandatoryParams(accounts[0]);
  if (mandatoryParamCheck == false) {
    var message = `account object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  var highestAcc = 0;
  for (let acc of accounts) {
    var accnum = acctestutils.toAccNum(acc.account);
    if (accnum > highestAcc) {
      highestAcc = accnum;
    }
  }

  url = acctestutils.getUrl(server, `${accountsPath}/${acctestutils.fromAccNum(highestAcc)}`);
  currentTestResult.url = url;

  let singleAccount;
  try {
    singleAccount = await acctestutils.getAPIResponse(url);
  } catch (error) {
    currentTestResult.failureMessages.push(error);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (undefined === singleAccount) {
    var message = `singleAccount is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let check = false;
  if (singleAccount.account === acctestutils.fromAccNum(highestAcc)) {
    check = true;
  }

  if (check == false) {
    var message = `Highest acc check was not found`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called accounts for single account`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Run all tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runAccountTests = async (server, classResults) => {
  var tests = [];
  tests.push(getAccountsWithAccountCheck(server, classResults));
  tests.push(getAccountsWithTimeAndLimitParams(server, classResults));
  tests.push(getSingleAccount(server, classResults));

  return Promise.all(tests);
};

module.exports = {
  runAccountTests: runAccountTests
};
