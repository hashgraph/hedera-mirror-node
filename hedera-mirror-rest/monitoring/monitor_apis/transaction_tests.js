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
const config = require('./config');
const {
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkMandatoryParams,
  checkRespDataFreshness,
  getAPIResponse,
  getUrl,
  testRunner,
  toAccNum,
  CheckRunner,
} = require('./utils');

const transactionsPath = '/transactions';
const recordsFileUpdateRefreshTime = 5;
const resource = 'transaction';
const resourceLimit = config[resource].limit;
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
  const {accountNumber, message} = option;
  const {transfers} = transactions[0];
  if (!transfers || !transfers.some((xfer) => toAccNum(xfer.account) === accountNumber)) {
    return {
      passed: false,
      message,
    };
  }

  return {passed: true};
};

const checkTransactionsConsensusTimestampOrder = (transactions, option) => {
  const {asc} = option;
  let previous = asc ? '0' : 'A';
  for (const txn of transactions) {
    const timestamp = txn.consensus_timestamp;
    if (asc && timestamp <= previous) {
      return {passed: false, message: 'consensus timestamps are not in ascending order'};
    }
    if (!asc && timestamp >= previous) {
      return {passed: false, message: 'consensus timestamps are not in descending order'};
    }
    previous = timestamp;
  }

  return {passed: true};
};

/**
 * Verify base transactions call
 * Also ensure an account mentioned in the transaction can be connected with the said transaction
 * @param {String} server API host endpoint
 */
const getTransactionsWithAccountCheck = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: resourceLimit});
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

  const highestAcc = _.max(
    _.map(
      _.filter(transactions[0].transfers, (xfer) => xfer.amount > 0),
      (xfer) => toAccNum(xfer.account)
    )
  );

  if (highestAcc === undefined) {
    return {
      url,
      passed: false,
      message: 'accNum is 0',
    };
  }

  url = getUrl(server, transactionsPath, {
    'account.id': highestAcc,
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
      accountNumber: highestAcc,
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
    .withCheckSpec(checkTransactionsConsensusTimestampOrder, {asc: true})
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
 * Verify single transactions can be retrieved
 * @param {Object} server API host endpoint
 */
const getSingleTransactionsById = async (server) => {
  let url = getUrl(server, transactionsPath, {limit: 1});
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

  url = getUrl(server, `${transactionsPath}/${transactions[0].transaction_id}`);
  const singleTransactions = await getAPIResponse(url, jsonRespKey);

  result = checkRunner.run(singleTransactions);
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
  const freshnessThreshold = recordsFileUpdateRefreshTime * 10;

  const url = getUrl(server, transactionsPath, {limit: 1});
  const transactions = await getAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'transactions is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `transactions.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkRespDataFreshness, {
      timestamp: (data) => data.consensus_timestamp,
      threshold: freshnessThreshold,
      message: (delta) => `balance was stale, ${delta} seconds old`,
    })
    .run(transactions);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully retrieved transactions from with ${freshnessThreshold} seconds ago`,
  };
};

/**
 * Run all tests in an asynchronous fashion waiting for all tests to complete
 * @param {String} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runTransactionTests = async (server, classResults) => {
  const tests = [];
  const runTest = testRunner(server, classResults);
  tests.push(runTest(getTransactionsWithAccountCheck));
  tests.push(runTest(getTransactionsWithOrderParam));
  tests.push(runTest(getTransactionsWithLimitParams));
  tests.push(runTest(getTransactionsWithTimeAndLimitParams));
  tests.push(runTest(getSingleTransactionsById));
  tests.push(runTest(checkTransactionFreshness));

  return Promise.all(tests);
};

module.exports = {
  runTransactionTests,
};
