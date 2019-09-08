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
 * Expects the response to have 1000 entries, and timestamp in 
 * the FRESHNESS_EXPECTATION minutes
 * @param {Object} server The server to run the test against
 * @return {} None. updates testAccounts variable
 */
const getAccountsNoParams = async (server) => {
    const FRESHNESS_EXPECTATION = 20; // minutes
	const url = getBaseUrl(server);
    const response = await fetch(url);
	const data = await response.json();
	
	common.logResult (server, url, 'getAccountsNoParams',
		(data.accounts.length === 1000) ?
		{result: true, msg: 'Received 1000 accounts'} : 
		{result: false, msg: 'Received less than 1000 accounts'});

	const txSec = data.accounts[0].balance.timestamp.split('.')[0];
	const currSec = Math.floor(new Date().getTime() / 1000);
	const delta = currSec - txSec;
	
	common.logResult (server, url, 'getAccountsNoParams',
		(delta < (60 * FRESHNESS_EXPECTATION)) ?
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