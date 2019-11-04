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
const server = process.env.TARGET;

// console.log(`*********** server is ${server}`)
// console.log(`*********** process.argv[2] is ${process.argv}`)
const runFetchTests = async function() {

    if (undefined === server) {
        console.log(`*********** server is undefined, skipping ....`)
        return
    }

    // console.log(`*********** runFetchTests called for server is ${server}`)
    var results = await transactionFetchTests.runTransactionTests();
    // console.log(`*********** Return from getTransactionFetchResults is ${JSON.stringify(results)}`);
    results.message = `${results.numPassedTests} / ${results.numPassedTests + results.numFailedTests} tests succeeded`;
    // console.log(`*********** passed : ${results.numPassedTests}, failed : ${results.numFailedTests}, results.message is ${results.message}`)

    console.log(JSON.stringify(results));
    return results;
}

runFetchTests()