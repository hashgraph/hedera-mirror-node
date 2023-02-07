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

import * as math from 'mathjs';
import config from './config';

import {
  accountIdCompare,
  checkAccountId,
  checkAPIResponseError,
  checkElementsOrder,
  checkMandatoryParams,
  checkResourceFreshness,
  checkRespArrayLength,
  checkRespObjDefined,
  CheckRunner,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  hasEmptyList,
  testRunner,
} from './utils';

const tokensPath = '/tokens';
const resource = 'token';
const tokensLimit = config[resource].limit || DEFAULT_LIMIT;
const tokenIdFromConfig = config[resource].tokenId;
const tokensJsonRespKey = 'tokens';
const tokenMandatoryParams = ['token_id', 'symbol', 'admin_key'];

/**
 * Verifies base tokens call
 *
 * @param {String} server API host endpoint
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokensCheck = async (server) => {
  const url = getUrl(server, tokensPath, {limit: tokensLimit});
  const tokens = await getAPIResponse(url, tokensJsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: tokensLimit,
      message: (elements, limit) => `tokens.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called tokens',
  };
};

const getFirstTokenIdWithCheckResult = async (server) => {
  const url = getUrl(server, tokensPath, {limit: 1});
  const tokens = await getAPIResponse(url, tokensJsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `tokens.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .run(tokens);
  return {
    tokenId: result.passed ? tokens[0].token_id : null,
    result: {
      url,
      ...result,
    },
  };
};

/**
 * Verifies tokens call with limit param
 *
 * @param server API host endpoint
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokensWithLimitParam = async (server) => {
  const {result} = await getFirstTokenIdWithCheckResult(server);
  if (!result.passed) {
    return result;
  }

  return {
    ...result,
    message: 'Successfully called tokens',
  };
};

/**
 * Verifies tokens call with order 'asc' and 'desc'
 *
 * @param server API host endpoint
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokensWithOrderParam = async (server) => {
  let url = getUrl(server, tokensPath, {order: 'asc'});
  let tokens = await getAPIResponse(url, tokensJsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'tokens is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenMandatoryParams,
      message: 'token object is missing some mandatory fields',
    })
    .withCheckSpec(checkElementsOrder, {asc: true, compare: accountIdCompare, key: 'token_id', name: 'token ID'});
  let result = checkRunner.run(tokens);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, tokensPath, {order: 'desc'});
  tokens = await getAPIResponse(url, tokensJsonRespKey);

  result = checkRunner
    .resetCheckSpec(checkElementsOrder, {asc: false, compare: accountIdCompare, key: 'token_id', name: 'token ID'})
    .run(tokens);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called tokens with order "asc" and "desc"',
  };
};

const tokenInfoPath = (tokenId) => `${tokensPath}/${tokenId}`;
const tokenInfoMandatoryParams = [
  'token_id',
  'symbol',
  'admin_key',
  'auto_renew_account',
  'auto_renew_period',
  'created_timestamp',
  'decimals',
  'expiry_timestamp',
  'freeze_default',
  'freeze_key',
  'initial_supply',
  'kyc_key',
  'modified_timestamp',
  'name',
  'supply_key',
  'total_supply',
  'treasury_account_id',
  'wipe_key',
];

/**
 * Verifies token info call.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenInfoCheck = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const url = getUrl(server, tokenInfoPath(tokenId));
  const tokenInfo = await getAPIResponse(url);

  const tokenInfoResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token info is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: tokenInfoMandatoryParams,
      message: 'token info object is missing some mandatory fields',
    })
    .run(tokenInfo);
  if (!tokenInfoResult.passed) {
    return {url, ...tokenInfoResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token info',
  };
};

const {tokenBalancesLimit} = config[resource];
const tokenBalancesJsonRespKey = 'balances';
const tokenBalanceMandatoryParams = ['account', 'balance'];
const tokenBalancesPath = (tokenId) => `${tokensPath}/${tokenId}/balances`;

/**
 * Verifies token balances call.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesCheck = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const url = getUrl(server, tokenBalancesPath(tokenId), {limit: tokenBalancesLimit});
  const balances = await getAPIResponse(url, tokenBalancesJsonRespKey);

  const balancesResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: tokenBalancesLimit,
      message: (elements, limit) => `token balances.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenBalanceMandatoryParams,
      message: 'token balance object is missing some mandatory fields',
    })
    .run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances',
  };
};

/**
 * Verifies token balances call with limit param.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesWithLimitParam = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  const url = getUrl(server, tokenBalancesPath(tokenId), {limit: 1});
  const balances = await getAPIResponse(url, tokenBalancesJsonRespKey);

  const balancesResult = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'token balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `token balances.length of ${elements.length} was expected to be 1`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: tokenBalanceMandatoryParams,
      message: 'token balance object is missing some mandatory fields',
    })
    .run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances with limit param',
  };
};

/**
 * Verifies token balances call with timestamp param.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesWithTimestampParam = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  let url = getUrl(server, tokenBalancesPath(tokenId), {limit: 1});
  const resp = await getAPIResponse(url);
  let balances = resp instanceof Error ? resp : resp[tokenBalancesJsonRespKey];

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `token balances.length of ${elements.length} was expected to be 1`,
    });
  let balancesResult = checkRunner.run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  const {timestamp} = resp;
  const plusOne = math.add(math.bignumber(timestamp), math.bignumber(1));
  const minusOne = math.subtract(math.bignumber(timestamp), math.bignumber(1));
  url = getUrl(server, tokenBalancesPath(tokenId), {
    timestamp: [`gt:${minusOne.toString()}`, `lt:${plusOne.toString()}`],
    limit: 1,
  });
  balances = await getAPIResponse(url, tokenBalancesJsonRespKey);

  balancesResult = checkRunner.run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances with timestamp param',
  };
};

/**
 * Verifies token balances call for a single account.
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const getTokenBalancesForAccount = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  let url = getUrl(server, tokenBalancesPath(tokenId), {limit: 1});
  let balances = await getAPIResponse(url, tokenBalancesJsonRespKey, hasEmptyList(tokenBalancesJsonRespKey));

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'balances is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `token balances.length of ${elements.length} was expected to be 1`,
    });
  let balancesResult = checkRunner.run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  const accountId = balances[0].account;
  url = getUrl(server, tokenBalancesPath(tokenId), {'account.id': accountId});
  balances = await getAPIResponse(url, tokenBalancesJsonRespKey);

  balancesResult = checkRunner
    .withCheckSpec(checkAccountId, {accountId, message: 'account was not found'})
    .run(balances);
  if (!balancesResult.passed) {
    return {url, ...balancesResult};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called token balances and performed account check',
  };
};

/**
 * Verifies the freshness of token balances
 *
 * @param server
 * @return {Promise<{message: String, passed: boolean, url: String}>}
 */
const checkTokenBalanceFreshness = async (server) => {
  let tokenId = tokenIdFromConfig;
  if (!tokenId) {
    const {tokenId: tokenIdFromAPI, result} = await getFirstTokenIdWithCheckResult(server);
    if (!result.passed) {
      return result;
    }

    tokenId = tokenIdFromAPI;
  }

  return checkResourceFreshness(server, tokenBalancesPath(tokenId), resource, (data) => data.timestamp);
};

/**
 * Run all token tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    runTest(getTokensCheck),
    runTest(getTokensWithLimitParam),
    runTest(getTokensWithOrderParam),
    runTest(getTokenInfoCheck),
    runTest(getTokenBalancesCheck),
    runTest(getTokenBalancesWithLimitParam),
    runTest(getTokenBalancesWithTimestampParam),
    runTest(getTokenBalancesForAccount),
    runTest(checkTokenBalanceFreshness),
  ]);
};

export default {
  resource,
  runTests,
};
