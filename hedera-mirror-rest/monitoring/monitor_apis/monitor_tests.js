/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import common from './common';
import config from './config';
import * as utils from './utils';

import accountTests from './account_tests';
import balanceTests from './balance_tests';
import blockTests from './block_tests';
import networkTests from './network_tests';
import stateproofTests from './stateproof_tests';
import tokenTests from './token_tests';
import topicmessageTests from './topicmessage_tests';
import transactionTests from './transaction_tests';

const allTestModules = [
  accountTests,
  balanceTests,
  blockTests,
  networkTests,
  stateproofTests,
  tokenTests,
  topicmessageTests,
  transactionTests,
];

const counters = {};

/**
 * Run node based tests against api endpoint
 * Each class manages its tests and returns a class results object
 * A single combined result object covering all test resources is returned
 *
 * @param {Object} server object provided by the user
 * @return {Object} results object capturing tests for given endpoint
 */
const runTests = async (server) => {
  const counter = server.name in counters ? counters[server.name] : 0;
  const skippedResource = [];
  const enabledTestModules = allTestModules.filter((testModule) => {
    const {enabled} = config[testModule.resource];
    return enabled == null || enabled;
  });
  const testModules = enabledTestModules.filter((testModule) => {
    const {intervalMultiplier} = config[testModule.resource];
    if (counter % intervalMultiplier === 0) {
      return true;
    }

    skippedResource.push(testModule.resource);
    return false;
  });
  counters[server.name] = counter + 1;

  const serverTestResult = new utils.ServerTestResult();
  if (skippedResource.length !== 0) {
    const currentResults = common.getServerCurrentResults(server.name);
    for (const testResult of currentResults.testResults) {
      if (skippedResource.includes(testResult.resource)) {
        serverTestResult.addTestResult(testResult);
      }
    }
  }

  if (testModules.length === 0) {
    serverTestResult.finish();
    return Promise.resolve(serverTestResult.result);
  }

  return Promise.all(testModules.map((testModule) => testModule.runTests(server, serverTestResult))).then(() => {
    // set class results endTime
    serverTestResult.finish();
    return serverTestResult.result;
  });
};

export {runTests};
