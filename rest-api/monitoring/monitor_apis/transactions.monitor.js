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

let testTransactions;

/**
 * Base url for the transactions API
 * @param {Object} server The server to run the test against
 * @return {String} Base query string for Hedera mirror node REST API
 */
const getBaseUrl = (server) => {
    return (`http://${server.ip}:${server.port}/api/v1/transactions`);
}

/**
 * Executes the /transactions API with no parameters
 * Expects the response to have 1000 entries, and timestamp in 
 * the FRESHNESS_EXPECTATION minutes
 * @param {Object} server The server to run the test against
 * @return {} None. updates testTransactions variable
 */
const getTransactionsNoParams = async (server) => {
    const FRESHNESS_EXPECTATION = 5; // minutes
	const url = getBaseUrl(server);
    const response = await fetch(url);
	const data = await response.json();

	common.logResult (server, url, 'getTransactionsNoParams',
		(data.transactions.length == 1000) ?
		{result: true, msg: 'Received 1000 entries'} : 
		{result: false, msg: 'Received less than 1000 entries'});

	const txSec = data.transactions[0].consensus_timestamp.split('.')[0];
	const currSec = Math.floor(new Date().getTime() / 1000);
	const delta = currSec - txSec;
	
	common.logResult (server, url, 'getTransactionsNoParams',
		(delta < (60 * FRESHNESS_EXPECTATION)) ?
		{result: true, msg: `Freshness: Received transactions from ${delta} seconds ago`} : 
        {result: false, msg: `Freshness: Got stale transactions from ${delta} seconds ago`}
    );
	
	testTransactions = data.transactions;
}

/**
 * Executes the /transactions API with timestamp filter of 1 ns before the first
 * transaction received in the getTransactionsNoParams call.
 * Expects the response to have 1000 entries, and the returned transactions list 
 * to be offset by 1 initial transaction.
 * @param {Object} server The server to run the test against
 * @return {} None.
 */
const checkTransactionsWithTimestamp = async (server) => {
	const url = getBaseUrl(server) + '?timestamp=lt:' + testTransactions[0].consensus_timestamp;
    const response = await fetch(url);
    const data = await response.json();

	common.logResult (server, url, 'checkTransactionsWithTimestamp', 
		(data.transactions.length === 1000) ?
		{result: true, msg: 'Received 1000 entries'} : 
		{result: false, msg: 'Received less than 1000 entries'});


    common.logResult (server, url, 'checkTransactionsWithTimestamp',
		(data.transactions[0].transaction_id === 
		testTransactions[1].transaction_id) ?
		{result: true, msg: 'Transaction ids matched'} : 
		{result: false, msg: 'Transaction ids do not match'});
}

/**
 * Executes the /transactions API for querying one single transaction
 * Expects the transaction id to match the requested transaction id
 * @param {Object} server The server to run the test against
 * @return {} None.
 */
const getOneTransaction = async (server) => {
    const MAX_DUPLICATES = 39; // The user could have submitted duplicate transactions
	const txId = testTransactions[0].transaction_id;
	const url = getBaseUrl(server) + '/' + txId;
    const response = await fetch(url);
	const data = await response.json();
	
	common.logResult (server, url, 'getOneTransaction',
		((data.transactions.length <= MAX_DUPLICATES)  && 
		(data.transactions[0].transaction_id === txId)) ?
		{result: true, msg: 'Received correct transaction'} : 
		{result: false, msg: 'Did not receive correct transaction'});
}


module.exports = {
    testTransactions: testTransactions,
    getTransactionsNoParams: getTransactionsNoParams,
    checkTransactionsWithTimestamp: checkTransactionsWithTimestamp,
    getOneTransaction: getOneTransaction
}