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

const transactionFetchTests = require('./transactionFetchTests');
const accountFetchTests = require('./accountFetchTests');
const balanceFetchTests = require('./balanceFetchTests');
const server = process.env.TARGET;
var results;

// console.log(`*********** server is ${server}`)
// console.log(`*********** process.argv[2] is ${process.argv}`)
const runFetchTests = async function() {

    if (undefined === server) {
        console.log(`*********** server is undefined, skipping ....`)
        return
    }
    
    results = await transactionFetchTests.runTransactionTests();

    var accountResults = await accountFetchTests.runAccountTests();
    combineResults(accountResults);

    var balanceResults = await balanceFetchTests.runBalnceTests();
    combineResults(balanceResults);

    results.message = `${results.numPassedTests} / ${results.numPassedTests + results.numFailedTests} tests succeeded`;

    console.log(JSON.stringify(results));
    return results;
}

const combineResults = function(newresults) {
    results.numFailedTests += newresults.numFailedTests;
    results.numPassedTests += newresults.numPassedTests;
    results.testResults.push(newresults.testResults);
    results.success = results.success && newresults.success;
}

runFetchTests()