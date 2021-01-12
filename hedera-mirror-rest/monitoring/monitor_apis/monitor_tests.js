/*-
 *‌
 * Hedera Mirror Node
 *​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *​
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
 *‍
 */

'use strict';

const {getServerCurrentResults} = require('./common');
const config = require('./config');
const utils = require('./utils');

const allTestModules = [
  require('./account_tests'),
  require('./balance_tests'),
  require('./transaction_tests'),
  require('./stateproof_tests'),
  require('./topicmessage_tests'),
  require('./token_tests'),
];

const counters = {};

/**
 * Run node based tests against api endpoint
 * Each class manages its tests and returns a class results object
 * A single combined result object covering all test resources is returned
 *
 * @param {String} name server name
 * @param {String} address server address in the format of http://ip:port
 * @return {Object} results object capturing tests for given endpoint
 */
const runTests = (name, address) => {
  const counter = name in counters ? counters[name] : 0;
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
  counters[name] = counter + 1;

  const serverTestResult = new utils.ServerTestResult();
  if (skippedResource.length !== 0) {
    const currentResults = getServerCurrentResults(name);
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

  return Promise.all(testModules.map((testModule) => testModule.runTests(address, serverTestResult))).then(() => {
    // set class results endTime
    serverTestResult.finish();
    return serverTestResult.result;
  });
};

module.exports = {
  runTests,
};
