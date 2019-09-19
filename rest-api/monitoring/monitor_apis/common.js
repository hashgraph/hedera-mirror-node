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

const fs = require('fs');

let currentResults = {}; // Results of current tests are stored here
let testResultsSnapshot = {}; // A snapshot of the last results - used for returning values in the API

/**
 * Copies the current results to the results snapshot, and initializes 
 * the current results object to get ready for next iteration of testing
 * @param {Object} server The server under test
 * @return {} None. Updates currentResults
 */
const initResults = (server) => {
    testResultsSnapshot[server.name] = 
        currentResults[server.name] === undefined ? {} :
        JSON.parse(JSON.stringify(currentResults[server.name]));
    currentResults[server.name] = {
		ip: server.ip,
		port: server.port,
		results: []
	};
}

/**
 * Log results of a single test to the current results
 * @param {Object} server The server to run the test against
 * @param {String} url The URL of the test
 * @param {String} funcName Name of the test function that produced this result
 * @param {Object} result Result of the test (pass/fail + message)
 * @return {} None. Updates currentResults
 */
const logResult = (server, url, funcName, result) => {
	console.log ((result.result ? 'PASSED' : 'FAILED') +
		' (' + JSON.stringify(server) + ')' + 
		': ' + url + ":: " + result.msg);

	currentResults[server.name].results.push({
		at: (new Date().getTime() / 1000).toFixed(3),
		result: result.result,
		url: url,
		message: funcName + ': ' + result.msg
	});
}

/**
 * Prints the results 
 * @param {} None
 * @return {} None
 */
const printResults = async function () {
	console.log ("Results:");

	for (let servername in currentResults) {
		console.log ("Server: " + servername);
		await currentResults[servername].results.forEach ((result) => {
			console.log (JSON.stringify(result));
			});
		console.log ("--------------");
	}
}

/**
 * Getter for a snapshot of results
 * @param {} None
 * @return {Object} Snapshot of results from the latest completed round of tests
 */
const getStatus = () => {
    return (testResultsSnapshot);
}

/**
 * Getter for a snapshot of results for a server specified in the HTTP request
 * @param {HTTPRequest} req HTTP Request object
 * @param {HTTPResponse} res HTTP Response object
 * @return {Object} Snapshot of results from the latest completed round of tests for 
 *      the specified server
 */
const getStatusWithId = (req, res) => {
    const net = req.params.id;

    if ((net == undefined) ||
        (net == null)) {
        res.status(404)
            .send(`Not found. Net: ${net}`);
        return;
    }

    if (!(testResultsSnapshot.hasOwnProperty(net)) ||
        (testResultsSnapshot.hasOwnProperty(net) == undefined)) {
        res.status(404)
            .send(`Test results unavailable for Net: ${net}`);
        return;
    }

    if (! (testResultsSnapshot[net].hasOwnProperty('results'))) {
        res.status(404)
            .send(`Test results unavailable for Net: ${net}`);
        return;
    }

    let cntPass = 0;
    let cntFail = 0;
    let cntTotal = 0;
    
    for (let row of testResultsSnapshot[net].results) {
        if (row.result) {
            cntPass ++;
        } else {
            cntFail ++;
        }
    }
    cntTotal = cntPass + cntFail;
 
    if (cntFail > 0) {
        res.status(409)
        .send(`{pass: ${cntPass}, fail: ${cntFail}, total: ${cntTotal}}`);
        return;
    }
    return (testResultsSnapshot[net]);
}


/**
 * Read the servers list file
 * @param {} None
 * @return {Object} config The configuration object
 */
const getServerList = () => {
    const SERVERLIST_FILE = './config/serverlist.json';

    try {
        const configtext = fs.readFileSync(SERVERLIST_FILE);
        const config = JSON.parse(configtext);
        return (config);
    } catch (err) {
        return ({
            "api": {
                "ip": "localhost", 
                "port": 80
            },
            "servers": [],
            "interval": 30
        });
    }
}

module.exports = {
    initResults: initResults,
    logResult: logResult,
    printResults: printResults,
    getStatus: getStatus,
    getStatusWithId: getStatusWithId,
    getServerList: getServerList
}