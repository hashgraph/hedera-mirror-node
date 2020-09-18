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

const accountsPath = '/accounts';
const resource = 'account';

/**
 * Makes a call to the rest-api and returns the accounts object from the response
 * @param {String} url
 * @param {Object} currentTestResult
 * @return {Object} Accounts object from response
 */
const getAccounts = (url, currentTestResult) => {
  return acctestutils
    .getAPIResponse(url)
    .then((json) => {
      return json.accounts;
    })
    .catch((error) => {
      currentTestResult.failureMessages.push(error);
    });
};

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Account JSON object
 */
const checkMandatoryParams = (entry) => {
  let check = true;
  ['balance', 'account', 'expiry_timestamp', 'auto_renew_period', 'key', 'deleted'].forEach((field) => {
    check = check && entry.hasOwnProperty(field);
  });

  ['timestamp', 'balance'].forEach((field) => {
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  const query = {};
  const {maxLimit, isGlobal} = acctestutils.getMaxLimit(resource);
  if (!isGlobal) {
    query.limit = maxLimit;
  }

  let url = acctestutils.getUrl(server, accountsPath, query);
  currentTestResult.url = url;
  const accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    const message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== maxLimit) {
    const message = `accounts.length of ${accounts.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    return;
  }

  if (!checkMandatoryParams(accounts[0])) {
    const message = `account object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const highestAcc = _.max(_.map(accounts, (acct) => acctestutils.toAccNum(acct.account)));

  url = acctestutils.getUrl(server, accountsPath, {
    "account.id": highestAcc,
    type: 'credit',
    limit: 1
  });
  currentTestResult.url = url;

  const singleAccount = await getAccounts(url, currentTestResult);

  if (undefined === singleAccount) {
    const message = `singleAccount is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleAccount.length !== 1) {
    const message = `singleAccount.length of ${singleAccount.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleAccount[0].account !== acctestutils.fromAccNum(highestAcc)) {
    const message = `Highest acc check was not found`;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, accountsPath, {limit: 1});
  currentTestResult.url = url;
  let accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    const message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== 1) {
    const message = `accounts.length of ${accounts.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const plusOne = math.add(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));

  url = acctestutils.getUrl(server, accountsPath, {
    timestamp: [
      `gt:${minusOne.toString()}`,
      `lt:${plusOne.toString()}`
      ],
    limit: 1
  });
  currentTestResult.url = url;
  accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    const message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== 1) {
    const message = `accounts.length of ${accounts.length} was expected to be 1`;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  const query = {};
  const {maxLimit, isGlobal} = acctestutils.getMaxLimit(resource);
  if (!isGlobal) {
    query.limit = maxLimit;
  }

  let url = acctestutils.getUrl(server, accountsPath, query);
  currentTestResult.url = url;
  const accounts = await getAccounts(url, currentTestResult);

  if (undefined === accounts) {
    const message = `accounts is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accounts.length !== maxLimit) {
    const message = `accounts.length of ${accounts.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (!checkMandatoryParams(accounts[0])) {
    const message = `account object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const highestAcc = _.max(_.map(accounts, (acct) => acctestutils.toAccNum(acct.account)));

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
    const message = `singleAccount is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleAccount.account !== acctestutils.fromAccNum(highestAcc)) {
    const message = `Highest account number was not found`;
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
  const tests = [];
  tests.push(getAccountsWithAccountCheck(server, classResults));
  tests.push(getAccountsWithTimeAndLimitParams(server, classResults));
  tests.push(getSingleAccount(server, classResults));

  return Promise.all(tests);
};

module.exports = {
  runAccountTests,
};
