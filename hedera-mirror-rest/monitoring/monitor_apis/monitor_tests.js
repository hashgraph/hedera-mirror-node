/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

const acctestutils = require('./monitortest_utils.js');
const transactionTests = require('./transaction_tests');
const accountTests = require('./account_tests');
const balanceTests = require('./balance_tests');

/**
 * Run node based tests against api endpoint
 * Each class manages its tests and returns a class rsults object
 * A single combined result object covering transaction, accounts and balances is returned
 * @param {Object} server API host endpoint
 * @return {Object} results object capturing tests for given endpoint
 */
const runTests = server => {
  if (undefined === server) {
    console.log(`server is undefined, skipping ....`);
    return;
  }

  let results = acctestutils.getMonitorClassResult();

  // results are passed by reference to avoid async calls modifying same result sets for single endpoint
  var transactionResults = transactionTests.runTransactionTests(server, results);

  var accountResults = accountTests.runAccountTests(server, results);

  var balanceResults = balanceTests.runBalanceTests(server, results);

  return Promise.all([transactionResults, accountResults, balanceResults]).then(() => {
    // set class results endTime
    results.endTime = Date.now();
    return results;
  });
};

module.exports = {
  runTests: runTests
};
