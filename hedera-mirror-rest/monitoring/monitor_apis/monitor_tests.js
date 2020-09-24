/*-
 *‌
 * Hedera Mirror Node
 *​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const accountTests = require('./account_tests');
const balanceTests = require('./balance_tests');
const transactionTests = require('./transaction_tests');
const stateproofTests = require('./stateproof_tests');
const topicmessageTests = require('./topicmessage_tests');
const utils = require('./utils');

/**
 * Run node based tests against api endpoint
 * Each class manages its tests and returns a class rsults object
 * A single combined result object covering transaction, accounts and balances is returned
 * @param {String} server API host endpoint
 * @return {Object} results object capturing tests for given endpoint
 */
const runTests = (server) => {
  if (undefined === server) {
    console.log(`server is undefined, skipping ....`);
    return;
  }

  const results = utils.getMonitorClassResult();

  const testModules = [accountTests, balanceTests, transactionTests, stateproofTests, topicmessageTests];
  return Promise.all(testModules.map((testModule) => testModule.runTests(server, results))).then(() => {
    // set class results endTime
    results.endTime = Date.now();
    return results;
  });
};

module.exports = {
  runTests,
};
