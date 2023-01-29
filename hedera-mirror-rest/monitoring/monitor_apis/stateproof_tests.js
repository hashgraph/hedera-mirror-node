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

import {operation} from 'retry';

import config from "./config.js";
import {
  checkAPIResponseError,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  getAPIResponse,
  getUrl,
  testRunner,
} from './utils';

const resource = 'stateproof';
const {retry} = config[resource];
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

  const {transaction_id: transactionId, nonce, scheduled} = transactions[0];
  url = getUrl(server, stateproofPath(transactionId), {nonce, scheduled});

  result = await new Promise((resolve) => {
    const checkRunner = new CheckRunner()
      .withCheckSpec(checkAPIResponseError)
      .withCheckSpec(checkRespObjDefined, {message: 'stateproof is undefined'})
      .withCheckSpec(checkMandatoryParams, {
        params: mandatoryParams,
        message: 'stateproof object is missing some mandatory fields',
      });
    const retryOperation = operation({
      factor: 1,
      minTimeout: retry.backoff,
      retries: retry.maxAttempts - 1,
    });

    retryOperation.attempt(async (current) => {
      const stateproof = await getAPIResponse(url);
      const result = checkRunner.run(stateproof);
      if (retryOperation.retry(!result.passed)) {
        // retry when not passed and the max number of retries haven't been reached
        logger.warn(`Retry after attempt #${current} failed: ${result.message}`);
        return;
      }

      resolve(result);
    });
  })

  return {
    url,
    passed: result.passed,
    message: result.message ?? `Successfully called stateproof for the latest successful transaction ${transactionId}`,
  };
};

/**
 * Run all stateproof tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return runTest(checkStateproofForValidTransaction);
};

export default {
  resource,
  runTests,
};
