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
var results;

/**
 * Run node fetch based tests against api endpoint
 * Each class manages its tests and returns a class rsults object
 * A single combined result object covering transaction, accounts and balances is returnred
 */
const runFetchTests = async function(server) {
    if (undefined === server) {
        console.log(`server is undefined, skipping ....`)
        return
    }
    
    results = acctestutils.getMonitorClassResult();
    var transactionResults = transactionFetchTests.runTransactionTests(server);

    var accountResults = accountFetchTests.runAccountTests(server);

    var balanceResults = balanceFetchTests.runBalanceTests(server);

    return await Promise.all([transactionResults, accountResults, balanceResults]).then((res) => {
        combineResults(res[0]);
        combineResults(res[1]);
        combineResults(res[2]);

        results.message = `${results.numPassedTests} / ${results.numPassedTests + results.numFailedTests} tests succeeded`;
        results.success = results.numFailedTests == 0 ? true : false;

        // output results to terminal for processing
        return results;
    });
}

/**
 * Combine the provided result opject with the overall class result object
 * Tests passed and failed numbers are incrment
 * Each test result is appended to class results
 * Success is set based on current and new results being true
 * @param {Object} newresults 
 */
const combineResults = function(newresults) {
    results.numFailedTests += newresults.numFailedTests;
    results.numPassedTests += newresults.numPassedTests;
    for (const res of newresults.testResults) {
        results.testResults.push(res);
    }

    results.success = results.success && newresults.success;
}

module.exports = {
    runFetchTests: runFetchTests
}