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

const acctestutils = require('./fetchtest_utils.js');
const transactionFetchTests = require('./transactionFetchTests');
const accountFetchTests = require('./accountFetchTests');
const balanceFetchTests = require('./balanceFetchTests');

/**
 * Run node fetch based tests against api endpoint
 * Each class manages its tests and returns a class rsults object
 * A single combined result object covering transaction, accounts and balances is returned
 * @param {Object} server API host endpoint
 * @return {Object} results object capturing tests for given endpoint
 */
const runFetchTests = (server) => {
    if (undefined === server) {
        console.log(`server is undefined, skipping ....`)
        return
    }
    
    let results = acctestutils.getMonitorClassResult();

    // results are passed by reference to avoid async calls modifying same result sets for single endpoint
    var transactionResults = transactionFetchTests.runTransactionTests(server, results);

    var accountResults = accountFetchTests.runAccountTests(server, results);

    var balanceResults = balanceFetchTests.runBalanceTests(server, results);

    return Promise.all([transactionResults, accountResults, balanceResults]).then(() => {
        return results;
    });
}

module.exports = {
    runFetchTests: runFetchTests
}