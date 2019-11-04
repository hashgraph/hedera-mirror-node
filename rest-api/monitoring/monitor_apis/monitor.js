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

var shell = require('shelljs');
const common = require('./common.js');

/**
 * Main function to run the tests and save results
 * @param {} None
 * @return {} None
 */
const runEverything = async function () {
    try {
        const restservers = common.getServerList().servers;

        if (restservers.length === 0) {
            return;
        }
        
        for (const server of restservers) {
            // Execute the tests using shell.exec
            const cmd = `(cd ../.. && TARGET=https://${server.ip}:${server.port} node ./__acceptancetests__/acceptanceFetchTests.js)`;
            if (common.getProcess(server) == undefined) {
                // Execute the test and store the pid
                const pid = shell.exec(cmd, {
                    async: true
                }, (code, out, err) => {
                    let results;
                    try {
                        results = JSON.parse(out);
                    } catch (err) {
                        console.log('Error parsing cmd output: ' + err);
                        results = {}
                    }
                    
                    common.deleteProcess(server);
                    common.saveResults(server, results);
                });
                common.saveProcess(server, pid);
            } else {
                const results = createFailedResultJson(`Test result unavailable`,
                    `Previous tests are still running for: ${server.name}`);
                common.saveResults(server, results);
            }
        }
    } catch (err) {
        console.log('Error in runEverything: ' + err);
        console.log(err.stack)
        console.trace();
    }
}

/**
 * Helper function to create a json object for failed test results 
 * @param {String} title Title in the jest output
 * @param {String} msg Message in the jest output
 * @return {Object} Constructed failed result object
 */
const createFailedResultJson = function (title, msg) {
    const fail = {
        numPassedTests: 0,
        numFailedTests: 1,
        success: false,
        message: 'Prerequisite tests failed',
        testResults: [{
            at: (new Date().getTime() / 1000).toFixed(3),
            message: `${title}: ${msg}`,
            result: 'failed',
            "assertionResults": [{
                "ancestorTitles": title,
                "failureMessages": [],
                "fullName": `${title}: ${msg}`,
                "location": null,
                "status": 'failed',
                "title": msg
            }]
        }]
    };
    return (fail);
}


module.exports = {
    runEverything: runEverything
}
