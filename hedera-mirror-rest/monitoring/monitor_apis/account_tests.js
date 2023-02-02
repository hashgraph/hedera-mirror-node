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

import _ from 'lodash';
import * as math from 'mathjs';
import config from './config';

import {
  checkAccountId,
  checkAPIResponseError,
  checkMandatoryParams,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

const accountsPath = '/accounts';
const resource = 'account';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const tokenRelationshipEnabled = config[resource].tokenRelationshipEnabled || false;
const stakingRewardAccountId = config[resource].stakingRewardAccountId;
const jsonRespKey = 'accounts';
const mandatoryParams = [
  'balance',
  'account',
  'expiry_timestamp',
  'auto_renew_period',
  'key',
  'deleted',
  'balance.timestamp',
  'balance.balance',
];

/**
 * Verify base accounts call
 * Also ensure an account mentioned in the accounts can be confirmed as existing
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getAccountsWithAccountCheck = async (server) => {
  let url = getUrl(server, accountsPath, {limit: resourceLimit});
  const accounts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'account is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (accts, limit) => `accounts.length of ${accts.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(accounts, (acct) => acct.account));
  url = getUrl(server, accountsPath, {
    'account.id': highestAccount,
    type: 'credit',
    limit: 1,
  });
  const singleAccount = await getAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'singleAccount is undefined'})
    .withCheckSpec(checkAccountId, {accountId: highestAccount, message: 'Highest acc check was not found'})
    .run(singleAccount);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts and performed account check',
  };
};

/**
 * Verify accounts call with time and limit query params provided
 * @param {Object} server API host endpoint
 */
const getAccountsWithTimeAndLimitParams = async (server) => {
  let url = getUrl(server, accountsPath, {
    'account.balance': 'gte:0',
    limit: 1,
  });
  let accounts = await getAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (accts) => `accounts.length of ${accts.length} was expected to be 1`,
    });
  let result = checkRunner.run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const plusOne = math.add(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
  url = getUrl(server, accountsPath, {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  accounts = await getAPIResponse(url, jsonRespKey, hasEmptyList(jsonRespKey));

  result = checkRunner.run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts with time and limit params',
  };
};

/**
 * Verify single account can be retrieved
 * @param {Object} server API host endpoint
 */
const getSingleAccount = async (server) => {
  let url = getUrl(server, accountsPath, {limit: resourceLimit});
  const accounts = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (accts, limit) => `accounts.length of ${accts.length} was expected to be ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'account object is missing some mandatory fields',
    })
    .run(accounts);
  if (!result.passed) {
    return {url, ...result};
  }

  const highestAccount = _.max(_.map(accounts, (acct) => acct.account));
  url = getUrl(server, `${accountsPath}/${highestAccount}`);
  const singleAccount = await getAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'accounts is undefined'})
    .withCheckSpec(checkAccountId, {accountId: highestAccount, message: 'Highest account number was not found'})
    .run(singleAccount);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts for single account',
  };
};

/**
 * Verify that we return token relationships for an existing account
 * @param {Object} server API host endpoint
 */
const getSingleAccountTokenRelationships = async (server) => {
  // Call the get /tokens endpoint to get a list of tokens.
  const tokensPath = '/tokens';
  const tokensJsonRespKey = 'tokens';
  const tokensLimit = 1;
  const tokenMandatoryParams = ['token_id'];
  const token_url = getUrl(server, tokensPath, {limit: tokensLimit, order: 'asc'});
  const tokens = await getAPIResponse(token_url, tokensJsonRespKey);

  const tokenResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  if (tokens.length === 0) {
    tokenResult.passed = true;
    tokenResult.message = 'No tokens were returned';
    return {url: token_url, ...tokenResult};
  }
  if (!tokenResult.passed) {
    return {url: token_url, ...tokenResult};
  }

  // get balances for a token and retrieve an  account id from it.

  const token_id = tokens[0].token_id;
  const tokenBalancesPath = (tokenId) => `${tokensPath}/${tokenId}/balances`;
  const tokenBalancesJsonRespKey = 'balances';

  const balancesLimit = 1;
  let balances_url = getUrl(server, tokenBalancesPath(token_id), {limit: balancesLimit});
  let balances = await getAPIResponse(balances_url, tokenBalancesJsonRespKey);
  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'});
  let balancesResult = checkRunner.run(balances);
  if (balances.length === 0) {
    balancesResult.passed = true;
    balancesResult.message = 'No balances were returned';
    return {url: balances_url, ...balancesResult};
  }
  if (!balancesResult.passed) {
    return {url: balances_url, ...balancesResult};
  }
  const accountId = balances[0].account;

  // Use that account id to call the /accounts/${accountId}/tokens endpoint
  const accountsTokenPath = `/accounts/${accountId}/tokens`;
  const accountsLimit = 1;
  let url = getUrl(server, accountsTokenPath, {limit: accountsLimit, order: 'asc'});
  const tokenRelationships = await getAPIResponse(url, tokensJsonRespKey, hasEmptyList(tokensJsonRespKey));
  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined '})
    .withCheckSpec(checkRespArrayLength, {
      limit: accountsLimit,
      message: (accts, limit) => `tokens.length of ${accts.length} was expected to be ${limit}`,
    })
    .run(tokenRelationships);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called accounts for token relationships',
  };
};

const getAccountStakingRewards = async (server) => {
  const stakingRewardsPath = `${accountsPath}/${stakingRewardAccountId}/rewards`;
  const rewardsJsonRespKey = 'rewards';
  let url = getUrl(server, stakingRewardsPath, {limit: resourceLimit});
  const rewardMandatoryParams = ['account_id', 'amount', 'timestamp'];
  const rewards = await getAPIResponse(url, rewardsJsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'rewards is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: rewardMandatoryParams,
      message: 'reward object is missing some mandatory fields',
    })
    .run(rewards);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called account staking rewards for account ${stakingRewardAccountId}.',
  };
};

/**
 * Run all account tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getAccountsWithAccountCheck),
    runTest(getAccountsWithTimeAndLimitParams),
    runTest(getSingleAccount),
    tokenRelationshipEnabled ? runTest(getSingleAccountTokenRelationships) : '',
    stakingRewardAccountId !== null ? runTest(getAccountStakingRewards) : '',
  ]);
};

export default {
  resource,
  runTests,
};
