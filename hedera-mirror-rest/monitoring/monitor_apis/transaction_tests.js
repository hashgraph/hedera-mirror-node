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
  checkElementsOrder,
  checkRespObjDefined,
  checkRespArrayLength,
  checkMandatoryParams,
  checkResourceFreshness,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} = require('./utils');

const transactionsPath = '/transactions';
const resource = 'transaction';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'transactions';
const mandatoryParams = [
  'consensus_timestamp',
  'valid_start_timestamp',
  'charged_tx_fee',
  'transaction_id',
  'memo_base64',
  'result',
  'name',
  'node',
  'transfers',
];

const checkTransactionTransfers = (transactions, option) => {
  const {accountId, message} = option;
  const {transfers} = transactions[0];
  if (!transfers || !transfers.some((xfer) => xfer.account === accountId)) {
    return {
      passed: false,
      message,
    };
  }

  return {passed: true};
};

/**
 * Verify base transactions call
 * Also ensure an account mentioned in the transaction can be connected with the said transaction
 * @param {String} server API host endpoint
 */
const getTransactionsWithAccountCheck = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: resourceLimit, type: 'credit'});
  const transactions = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `transactions.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(
    _.map(
      _.filter(transactions[0].transfers, (xfer) => xfer.amount > 0),
      (xfer) => xfer.account
    )
  );

  if (highestAccount === undefined) {
    return {
      url,
      passed: false,
      message: 'accNum is 0',
    };
  }

  url = getUrl(server, transactionsPath, {
    'account.id': highestAccount,
    type: 'credit',
    limit: 1,
  });
  const accTransactions = await getAPIResponse(url, jsonRespKey);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} is not 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    })
    .withCheckSpec(checkTransactionTransfers, {
      accountId: highestAccount,
      message: 'Highest acc check was not found',
    })
    .run(accTransactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions and performed account check',
  };
};

/**
 * Verify transactions call with order query params provided
 * @param {String} server API host endpoint
 */
const getTransactionsWithOrderParam = async (server) => {
  const url = getUrl(server, transactionsPath, {order: 'asc', limit: resourceLimit});
  const transactions = await getAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `transactions.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    })
    .withCheckSpec(checkElementsOrder, {asc: true, key: 'consensus_timestamp', name: 'consensus timestamp'})
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions with order params only',
  };
};

/**
 * Verify transactions call with limit query params provided
 * @param {Object} server API host endpoint
 */
const getTransactionsWithLimitParams = async (server) => {
  const url = getUrl(server, transactionsPath, {limit: 10});
  const transactions = await getAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 10,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 10`,
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions with limit params only',
  };
};

/**
 * Verify transactions call with time and limit query params provided
 * @param {Object} server API host endpoint
 */
const getTransactionsWithTimeAndLimitParams = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: 1});
  let transactions = await getAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 1`,
    });
  let result = checkRunner.run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  const plusOne = math.add(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
  url = getUrl(server, transactionsPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  transactions = await getAPIResponse(url, jsonRespKey);

  result = checkRunner.run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called transactions with time and limit params',
  };
};

/**
 * Verify there is at least one successful transaction for the latest successful transaction
 * @param {Object} server API host endpoint
 */
const getSuccessfulTransactionById = async (server) => {
  // look for the latest successful transaction
  let url = getUrl(server, transactionsPath, {limit: 1, result: 'success'});
  const transactions = await getAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'transaction object is missing some mandatory fields',
    });
  let result = checkRunner.run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  // filter the scheduled transaction
  url = getUrl(server, `${transactionsPath}/${transactions[0].transaction_id}`, {scheduled: false});
  const singleTransactions = await getAPIResponse(url, jsonRespKey);

  // only verify the single successful transaction
  result = checkRunner
    .resetCheckSpec(checkRespArrayLength, {
      func: (length) => length >= 1,
      message: (elements) => `transactions.length of ${elements.length} was expected to be >= 1`,
    })
    .run(singleTransactions.filter((tx) => tx.result === 'SUCCESS'));
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully retrieved single transactions by id',
  };
};

/**
 * Verfiy the freshness of transactions returned by the api
 * @param {Object} server API host endpoint
 */
const checkTransactionFreshness = async (server) => {
  return checkResourceFreshness(server, transactionsPath, resource, (data) => data.consensus_timestamp, jsonRespKey);
};

/**
 * Run all transaction tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getTransactionsWithAccountCheck),
    runTest(getTransactionsWithOrderParam),
    runTest(getTransactionsWithLimitParams),
    runTest(getTransactionsWithTimeAndLimitParams),
    runTest(getSuccessfulTransactionById),
    runTest(checkTransactionFreshness),
  ]);
};

module.exports = {
  resource,
  runTests,
};
