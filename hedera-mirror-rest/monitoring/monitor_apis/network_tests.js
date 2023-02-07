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

import {
  checkAPIResponseError,
  checkRespObjDefined,
  checkMandatoryParams,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} from './utils';

const networkStakePath = '/network/stake';
const resource = 'network';
const jsonRespKey = '';
const mandatoryParams = [
  'max_staking_reward_rate_per_hbar',
  'node_reward_fee_fraction',
  'stake_total',
  'staking_period',
  'staking_period_duration',
  'staking_periods_stored',
  'staking_reward_fee_fraction',
  'staking_reward_rate',
  'staking_start_threshold',
];

/**
 * Verify network stake call
 * @param {Object} server API host endpoint
 * @returns {{url: string, passed: boolean, message: string}}
 */
const getNetworkStake = async (server) => {
  let url = getUrl(server, networkStakePath);
  const networkStake = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'network stake is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'network stake object is missing some mandatory fields',
    })
    .run(networkStake);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called networkStake',
  };
};

/**
 * Run all network tests in an asynchronous fashion waiting for all tests to complete
 * @param {Object} server object provided by the user
 * @param {ServerTestResult} testResult shared server test result object capturing tests for given endpoint
 */
const runTests = async (server, testResult) => {
  const runTest = testRunner(server, testResult, resource);
  return Promise.all([runTest(getNetworkStake)]);
};

export default {
  resource,
  runTests,
};
