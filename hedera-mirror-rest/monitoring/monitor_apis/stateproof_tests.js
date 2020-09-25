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

const {
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkMandatoryParams,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} = require('./utils');

const transactionsPath = '/transactions';
const transactionsJsonKey = 'transactions';
const mandatoryParams = ['record_file', 'address_books', 'signature_files'];

const stateproofPath = (transactionId) => `${transactionsPath}/${transactionId}/stateproof`;

/**
 * Checks state proof for the latest successful transaction.
 *
 * @param {String} server
 * @return {{url: String, passed: boolean, message: String}}
 */
const checkStateproofForValidTransaction = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: 1, order: 'desc', result: 'success'});
  const transactions = await getAPIResponse(url, transactionsJsonKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} is not 1`,
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const transactionId = transactions[0].transaction_id;
  url = getUrl(server, stateproofPath(transactionId));
  const stateproof = await getAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'stateproof is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'stateproof object is missing some mandatory fields',
    })
    .run(stateproof);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called stateproof for the latest successful transaction ${transactionId}`,
  };
};

/**
 * Checks state proof for a failed transaction, expects 404 not found.
 *
 * @param {String} server
 * @return {{url: String, passed: boolean, message: String}}
 */
const checkStateproofForFailedTransaction = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: 1, order: 'desc', result: 'fail'});
  const transactions = await getAPIResponse(url, transactionsJsonKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} is not 1`,
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const transactionId = transactions[0].transaction_id;
  url = getUrl(server, stateproofPath(transactionId));
  const stateproof = await getAPIResponse(url);

  result = new CheckRunner().withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 404}).run(stateproof);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called stateproof for a failed transaction and got expected 404}`,
  };
};

/**
 * Checks state proof for a non-existing transaction, expects 404 not found.
 *
 * @param {String} server
 * @return {{url: String, passed: boolean, message: String}}
 */
const checkStateproofForNonExistingTransaction = async (server) => {
  const transactionId = '0.0.19652-10-123456789';
  const url = getUrl(server, stateproofPath(transactionId));
  const stateproof = await getAPIResponse(url);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 404})
    .run(stateproof);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called stateproof for non-existing transaction ${transactionId} and got expected 404`,
  };
};

/**
 * Run all stateproof tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {String} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runTests = async (server, classResults) => {
  const tests = [];
  const runTest = testRunner(server, classResults);
  tests.push(runTest(checkStateproofForValidTransaction));
  tests.push(runTest(checkStateproofForFailedTransaction));
  tests.push(runTest(checkStateproofForNonExistingTransaction));

  return Promise.all(tests);
};

module.exports = {
  runTests,
};
