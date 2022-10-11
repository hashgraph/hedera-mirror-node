/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkMandatoryParams,
  checkResourceFreshness,
  DEFAULT_LIMIT,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} from './utils';

const blocksPath = '/blocks';
const resource = 'block';
const resourceLimit = config[resource].limit || DEFAULT_LIMIT;
const jsonRespKey = 'blocks';
const mandatoryParams = ['count', 'gas_used', 'hapi_version', 'hash', 'name', 'number', 'previous_hash', 'size'];

/**
 * Verify base blocks call
 * @param {String} server API host endpoint
 */
const getBlockCheck = async (server) => {
  console.log('MYK: debug: starting getBlockCheck');
  const url = getUrl(server, blocksPath, {limit: resourceLimit});
  const blocks = await getAPIResponse(url, jsonRespKey);

  const result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'blocks is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `blocks.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'block object is missing some mandatory fields',
    })
    .run(blocks);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called blocks and performed account check',
  };
};

/**
 * Run all block tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([
    /*
    runTest(getBlockCheck),
*/
  ]);
};

export default {
  resource,
  runTests,
};
