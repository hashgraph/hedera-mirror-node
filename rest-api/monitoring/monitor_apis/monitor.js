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

const transMonitor = require('./transactions.monitor.js');
const balancesMonitor = require('./balances.monitor.js');
const accountsMonitor = require('./accounts.monitor.js');
const common = require('./common.js');

// List of test functions
const funcs = [transMonitor.getTransactionsNoParams
	, transMonitor.checkTransactionsWithTimestamp
	, transMonitor.getOneTransaction
	, balancesMonitor.getBalancesNoParams
	, balancesMonitor.checkBalancesWithTimestamp
	, balancesMonitor.getOneBalance
	, accountsMonitor.getAccountsNoParams
    , accountsMonitor.getOneAccount];

/**
 * Recursive: Run all tests in the funcs array for one server sequentially
 * @param {Object} server The server to run the tests on
 * @param {Integer} testIndex Index of the first test to be run
 * @return {} None
 */
const runTests = async function (server, testIndex) {
	if (testIndex < (funcs.length - 1)) {
		const x = await funcs[testIndex](server)
		await runTests(server, testIndex + 1);
	} else {
		const x = await funcs[testIndex](server);
	}
}

/**
 * Run all tests in the funcs array on all the servers
 * @param {Array} restservers List of servers to run tests against
 * @return {} None
 */
const runOnAllServers = async function (restservers) {
	for (let server of restservers) {
		common.initResults(server);
		try {
			const a = await runTests(server, 0);
			console.log(`Tests completed for server: ${server.name}`);
		} catch (err) {
			console.log('Error in runOnAllServers: ' + err);
			console.trace();
		}
	}
}

/**
 * Main function to run all tests and print results
 * @param {} None
 * @return {} None
 */
const runEverything = async function () {
    try {
        const restservers = common.getServerList().servers;

        if (restservers.length === 0) {
            return;
        }

        await runOnAllServers(restservers);
        await common.printResults();
    } catch (err) {
        console.log('Error in runEverything: ' + err);
        console.log (err.stack)
        console.trace();
    }
}

module.exports = {
	runEverything: runEverything
}