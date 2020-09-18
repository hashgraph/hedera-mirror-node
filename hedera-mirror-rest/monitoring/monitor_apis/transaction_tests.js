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

const transactionsPath = '/transactions';
const recordsFileUpdateRefreshTime = 5;
const resource = 'transaction';

/**
 * Makes a call to the rest-api and returns the transacations object from the response
 * @param {String} url
 * @param {Object} currentTestResult
 * @return {Object} Transactions object from response
 */
const getTransactions = (url, currentTestResult) => {
  return acctestutils
    .getAPIResponse(url)
    .then((json) => {
      return json.transactions;
    })
    .catch((error) => {
      currentTestResult.failureMessages.push(error);
    });
};

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Transaction JSON object
 */
const checkMandatoryParams = (entry) => {
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
    'transfers',
  ].forEach((field) => {
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  const query = {};
  const {maxLimit, isGlobal} = acctestutils.getMaxLimit(resource);
  if (!isGlobal) {
    query.limit = maxLimit;
  }

  let url = acctestutils.getUrl(server, transactionsPath, query);
  currentTestResult.url = url;
  const transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== maxLimit) {
    const message = `transactions.length of ${transactions.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (!checkMandatoryParams(transactions[0])) {
    const message = `transaction object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const highestAcc = _.max(
    _.map(
      _.filter(transactions[0].transfers, (xfer) => xfer.amount > 0),
      (xfer) => acctestutils.toAccNum(xfer.account)
    )
  );

  if (highestAcc === undefined) {
    const message = `accNum is 0`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (undefined === transactions[0].consensus_timestamp) {
    const message = `transactions[0].consensus_timestamp is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  url = acctestutils.getUrl(server, transactionsPath, {
    'account.id': highestAcc,
    type: 'credit',
    limit: 1,
  });
  currentTestResult.url = url;

  const accTransactions = await getTransactions(url, currentTestResult);
  if (undefined === accTransactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (accTransactions.length !== 1) {
    const message = `accTransactions.length of ${transactions.length} is not 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const {transfers} = accTransactions[0];
  if (!transfers || !transfers.some((xfer) => acctestutils.toAccNum(xfer.account) === highestAcc)) {
    const message = `Highest acc check was not found`;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  const query = {order: 'asc'};
  const {maxLimit, isGlobal} = acctestutils.getMaxLimit(resource);
  if (!isGlobal) {
    query.limit = maxLimit;
  }

  const url = acctestutils.getUrl(server, transactionsPath, query);
  currentTestResult.url = url;
  const transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== maxLimit) {
    const message = `transactions.length of ${transactions.length} is less than limit ${maxLimit}`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  let previousConsensusTimestamp = '0';
  for (const txn of transactions) {
    if (txn.consensus_timestamp <= previousConsensusTimestamp) {
      currentTestResult.failureMessages.push('consensus timestamps are not in ascending order');
      acctestutils.addTestResult(classResults, currentTestResult, false);
      return;
    }
    previousConsensusTimestamp = txn.consensus_timestamp;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  const url = acctestutils.getUrl(server, transactionsPath, {limit: 10});
  currentTestResult.url = url;
  const transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 10) {
    const message = `transactions.length of ${transactions.length} was expected to be 10`;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, transactionsPath, {limit: 1});
  currentTestResult.url = url;
  let transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    const message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  const plusOne = math.add(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  url = acctestutils.getUrl(server, transactionsPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  currentTestResult.url = url;
  transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    const message = `transactions.length of ${transactions.length} was expected to be 1`;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  let url = acctestutils.getUrl(server, transactionsPath, {limit: 1});
  currentTestResult.url = url;
  const transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    const message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (!checkMandatoryParams(transactions[0])) {
    const message = `transaction object is missing some mandatory fields`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  url = acctestutils.getUrl(server, `${transactionsPath}/${transactions[0].transaction_id}`);
  currentTestResult.url = url;

  const singleTransactions = await getTransactions(url, currentTestResult);
  if (undefined === singleTransactions) {
    const message = `singleTransactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (singleTransactions.length !== 1) {
    const message = `singleTransactions.length of ${transactions.length} is not 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (!checkMandatoryParams(transactions[0])) {
    const message = `single transaction object is missing some mandatory fields`;
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
  const currentTestResult = acctestutils.getMonitorTestResult();

  const url = acctestutils.getUrl(server, transactionsPath, {limit: 1});
  currentTestResult.url = url;
  const transactions = await getTransactions(url, currentTestResult);

  if (undefined === transactions) {
    const message = `transactions is undefined`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  if (transactions.length !== 1) {
    const message = `transactions.length of ${transactions.length} was expected to be 1`;
    currentTestResult.failureMessages.push(message);
    acctestutils.addTestResult(classResults, currentTestResult, false);
    return;
  }

  // Check for freshness of data
  const txSec = transactions[0].consensus_timestamp.split('.')[0];
  const currSec = Math.floor(new Date().getTime() / 1000);
  const delta = currSec - txSec;
  const freshnessThreshold = recordsFileUpdateRefreshTime * 10;

  if (delta > freshnessThreshold) {
    const message = `transactions was stale, ${delta} seconds old`;
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
  const tests = [];
  tests.push(getTransactionsWithAccountCheck(server, classResults));
  tests.push(getTransactionsWithOrderParam(server, classResults));
  tests.push(getTransactionsWithLimitParams(server, classResults));
  tests.push(getTransactionsWithTimeAndLimitParams(server, classResults));
  tests.push(getSingleTransactionsById(server, classResults));
  tests.push(checkTransactionFreshness(server, classResults));

  return Promise.all(tests);
};

module.exports = {
  runTransactionTests,
};
