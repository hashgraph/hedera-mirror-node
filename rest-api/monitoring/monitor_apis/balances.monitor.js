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

const fetch = require("node-fetch");
const common = require('./common.js');

let testBalances;
let testBalancesTimestamp;

/**
 * Base url for the balances API
 * @param {Object} server The server to run the test against
 * @return {String} Base query string for Hedera mirror node REST API
 */
const getBaseUrl = (server) => {
    return (`http://${server.ip}:${server.port}/api/v1/balances`);
}

/**
 * Executes the /balances API with no parameters
 * Expects the response to have 1000 entries, and timestamp in 
 * the FRESHNESS_EXPECTATION minutes
 * @param {Object} server The server to run the test against
 * @return {} None. updates testBalances variable
 */
const getBalancesNoParams = async (server) => {
    const FRESHNESS_EXPECTATION = 20; // minutes
	const url = getBaseUrl(server);
    const response = await fetch(url);
	const data = await response.json();
	
	common.logResult (server, url, 'getBalancesNoParams',
		(data.balances.length === 1000) ?
		{result: true, msg: 'Received 1000 balances'} : 
		{result: false, msg: 'Received less than 1000 balances'});

	const balancesSec = data.timestamp.split('.')[0];
	const currSec = Math.floor(new Date().getTime() / 1000);
	const delta = currSec - balancesSec;
	
	common.logResult (server, url, 'getBalancesNoParams',
		(delta < (60 * FRESHNESS_EXPECTATION)) ?
		{result: true, msg: `Freshness: Received balances from ${delta} seconds ago`} : 
        {result: false, msg: `Freshness: Got stale balances from ${delta} seconds ago`}
    );
	
    testBalances = data.balances;
    testBalancesTimestamp = data.timestamp;
}

/**
 * Executes the /balances API with timestamp filter
 * Expects the response to have 1000 entries, and to have a timestamp 
 * that is within BALANCE_TS_QUERY_TOLERANCE minutes
 * @param {Object} server The server to run the test against
 * @return {} None. updates testBalances variable
 */
const checkBalancesWithTimestamp = async (server) => {
    const BALANCE_TS_QUERY_TOLERANCE = 30; // minutes
	const url = getBaseUrl(server) + '?timestamp=lt:' + testBalancesTimestamp;
    const response = await fetch(url);
	const data = await response.json();

	common.logResult (server, url, 'checkBalancesWithTimestamp', 
		(data.balances.length === 1000) ?
		{result: true, msg: 'Received 1000 balances'} : 
		{result: false, msg: 'Received less than 1000 balances'});

    common.logResult (server, url, 'checkBalancesWithTimestamp',
        ((data.timestamp < testBalancesTimestamp) && 
        ((testBalancesTimestamp - data.timestamp) < (60 * BALANCE_TS_QUERY_TOLERANCE)))
        ?
		{result: true, msg: 'Received older balances correctly'} : 
		{result: false, msg: 'Did not receive older balances correctly'});
}

/**
 * Executes the /balances API for querying one single account
 * Expects the account id to match the requested account id
 * @param {Object} server The server to run the test against
 * @return {} None.
 */
const getOneBalance = async (server) => {
	const accId = testBalances[0].account;
	const url = getBaseUrl(server) + '/?account.id=' + accId;
    const response = await fetch(url);
	const data = await response.json();

	common.logResult (server, url, 'getOneBalance',
		((data.balances.length === 1)  && 
		(data.balances[0].account === accId)) ?
		{result: true, msg: 'Received correct account balance'} : 
		{result: false, msg: 'Did not receive correct account balance'});
}

module.exports = {
    testBalances: testBalances,
    getBalancesNoParams: getBalancesNoParams,
    checkBalancesWithTimestamp: checkBalancesWithTimestamp,
    getOneBalance: getOneBalance
}