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
const transactionsPath = '/transactions';
const maxLimit = config.api.maxLimit;
const recordsFileUpdateRefreshTime = 5;

/**
 * Makes a call to the rest-api and returns the transacations object from the response
 * @param {String} pathandquery
 * @return {Object} Transactions object from response
 */
const getTransactions = (pathandquery, currentTestResult) => {
  return acctestutils
    .getAPIResponse(pathandquery)
    .then(json => {
      return json.transactions;
    })
    .catch(error => {
      currentTestResult.failureMessages.push(error);
    });
};

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Transaction JSON object
 */
const checkMandatoryParams = entry => {
  let check = true;
  [
    'consensus_timestamp',
    'valid_start_timestamp',
    'charged_tx_fee',
    'transaction_id',
    'memo_base64',
    'result',
    'name',
    'node',
    'transfers'
  ].forEach(field => {
    check = check && entry.hasOwnProperty(field);
  });
  return check;
};

/**
 * Verify base transactions call
 * Also ensure an account mentioned in the transaction can be connected with the said transaction
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getTransactionsWithAccountCheck = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, transactionsPath);
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== maxLimit) {
    var message = `transactions.length of ${transactions.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let mandatoryParamCheck = checkMandatoryParams(transactions[0]);
  if (mandatoryParamCheck == false) {
    var message = `transaction object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  var accNum = 0;
  var highestAcc = 0;
  for (let xfer of transactions[0].transfers) {
    if (xfer.amount > 0) {
      accNum = acctestutils.toAccNum(xfer.account);
      if (accNum > highestAcc) {
        highestAcc = accNum;
      }
    }
  }

  if (accNum === 0) {
    var message = `accNum is 0`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (undefined === transactions[0].consensus_timestamp) {
    var message = `transactions[0].consensus_timestamp is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  url = acctestutils.getUrl(server, `${transactionsPath}?account.id=${highestAcc}&type=credit&limit=1`);
  currentTestResult.url = url;

  let accTransactions = await getTransactions(url, currentTestResult);
  if (undefined === accTransactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accTransactions.length !== 1) {
    var message = `accTransactions.length of ${transactions.length} is not 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let check = false;
  for (let xfer of accTransactions[0].transfers) {
    if (acctestutils.toAccNum(xfer.account) === highestAcc) {
      check = true;
    }
  }

  if (check == false) {
    var message = `Highest acc check was not found`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called transactions and performed account check`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify transactions call with order query params provided
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getTransactionsWithOrderParam = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${transactionsPath}?order=asc`);
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== maxLimit) {
    var message = `transactions.length of ${transactions.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let prevSeconds = 0;
  for (let txn of transactions) {
    if (acctestutils.secNsToSeconds(txn.seconds) < prevSeconds) {
      check = false;
    }
    prevSeconds = acctestutils.secNsToSeconds(txn.seconds);
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called transactions with order params only`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify transactions call with limit query params provided
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getTransactionsWithLimitParams = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${transactionsPath}?limit=10`);
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 10) {
    var message = `transactions.length of ${transactions.length} was expected to be 10`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called transactions with limit params only`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify transactions call with time and limit query params provided
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getTransactionsWithTimeAndLimitParams = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${transactionsPath}?limit=1`);
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    var message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let plusOne = math.add(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  let minusOne = math.subtract(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  let paq = `${transactionsPath}?timestamp=gt:${minusOne.toString()}` + `&timestamp=lt:${plusOne.toString()}&limit=1`;

  url = acctestutils.getUrl(server, paq);
  transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    var message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully called transactions with time and limit params`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verify single transactions can be retrieved
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const getSingleTransactionsById = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${transactionsPath}?limit=1`);
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    var message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let mandatoryParamCheck = checkMandatoryParams(transactions[0]);
  if (mandatoryParamCheck == false) {
    var message = `transaction object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  url = acctestutils.getUrl(server, `${transactionsPath}/${transactions[0].transaction_id}`);
  currentTestResult.url = url;

  let singleTransactions = await getTransactions(url, currentTestResult);
  if (undefined === singleTransactions) {
    var message = `singleTransactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleTransactions.length !== 1) {
    var message = `singleTransactions.length of ${transactions.length} is not 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  mandatoryParamCheck = checkMandatoryParams(transactions[0]);
  if (mandatoryParamCheck == false) {
    var message = `single transaction object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully retrieved single transactions by id`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Verfiy the freshness of transactions returned by the api
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const checkTransactionFreshness = async (server, classResults) => {
  var currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, `${transactionsPath}?limit=1`);
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    var message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    var message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  // Check for freshness of data
  const txSec = transactions[0].consensus_timestamp.split('.')[0];
  const currSec = Math.floor(new Date().getTime() / 1000);
  const delta = currSec - txSec;
  let freshnessThreshold = recordsFileUpdateRefreshTime * 10;

  if (delta > freshnessThreshold) {
    var message = `transactions was stale, ${delta} seconds old`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  currentTestResult.result = 'passed';
  currentTestResult.message = `Successfully retrieved transactions from with ${freshnessThreshold} seconds ago`;

  acctestutils.addTestResult(classResults, currentTestResult, true);
};

/**
 * Run all tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runTransactionTests = async (server, classResults) => {
  var tests = [];
  tests.push(getTransactionsWithAccountCheck(server, classResults));
  tests.push(getTransactionsWithOrderParam(server, classResults));
  tests.push(getTransactionsWithLimitParams(server, classResults));
  tests.push(getTransactionsWithTimeAndLimitParams(server, classResults));
  tests.push(getSingleTransactionsById(server, classResults));
  tests.push(checkTransactionFreshness(server, classResults));

  return Promise.all(tests);
};

module.exports = {
  runTransactionTests: runTransactionTests
};
