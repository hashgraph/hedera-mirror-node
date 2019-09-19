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
const config = require('./config/config.js');

let testAccounts;

/**
 * Base url for the accounts API
 * @param {Object} server The server to run the test against
 * @return {String} Base query string for Hedera mirror node REST API
 */
const getBaseUrl = (server) => {
	return (`http://${server.ip}:${server.port}/api/v1/accounts`);
}

/**
 * Executes the /accounts API with no parameters
 * Expects the response to have config.limits.RESPONSE_ROWS entries, and 
 * timestamp in the last n minutes as specified in the config.fileUpdateRefreshTimes
 * @param {Object} server The server to run the test against
 * @return {} None. updates testAccounts variable
 */
const getAccountsNoParams = async (server) => {
	const url = getBaseUrl(server);
    const response = await fetch(url);
	const data = await response.json();
	
	common.logResult (server, url, 'getAccountsNoParams',
		(data.accounts.length === config.limits.RESPONSE_ROWS) ?
		{result: true, msg: `Received ${config.limits.RESPONSE_ROWS} accounts`} : 
		{result: false, msg: `Received less than ${config.limits.RESPONSE_ROWS} accounts`});

	const txSec = data.accounts[0].balance.timestamp.split('.')[0];
	const currSec = Math.floor(new Date().getTime() / 1000);
	const delta = currSec - txSec;
	
	common.logResult (server, url, 'getAccountsNoParams',
		(delta < (2 * config.fileUpdateRefreshTimes.balances)) ?
		{result: true, msg: `Freshness: Received accounts from ${delta} seconds ago`} : 
        {result: false, msg: `Freshness: Got stale accounts from ${delta} seconds ago`}
    );
	
	testAccounts = data.accounts;
}

/**
 * Executes the /accounts API for querying one single account
 * Expects the account id to match the requested account id
 * @param {Object} server The server to run the test against
 * @return {} None.
 */
const getOneAccount = async (server) => {
	const accId = testAccounts[0].account;
	const url = getBaseUrl(server) + '/' + accId;
    const response = await fetch(url);
	const data = await response.json();

	common.logResult (server, url, 'getOneAccount',
		(data.account === accId) ?
		{result: true, msg: 'Received correct account'} : 
		{result: false, msg: 'Did not receive correct account'});
}

module.exports = {
    testAccounts: testAccounts,
    getAccountsNoParams: getAccountsNoParams,
    getOneAccount: getOneAccount
}