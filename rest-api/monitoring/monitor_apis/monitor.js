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
const fetchTests = require('./fetchTests.js')

/**
 * Main function to run the tests and save results
 * @param {} None
 * @return {} None
 */
const runEverything = async function (servers) {
    try {
        const restservers = undefined === servers ? common.getServerList().servers : servers;

        if (restservers.length === 0) {
            return;
        }
        
        let shellConfig = common.getServerList().shell;
        const shellFlag = undefined === shellConfig ? true : shellConfig;
        for (const server of restservers) {           
            if (common.getProcess(server) == undefined) {
                // based on the presence of a shell flag in serverlist.json run tests through single node thread or as seperate shell processes
                if (!shellFlag) {
                    // execute test and store name
                    fetchTests.runFetchTests(`http://${server.ip}:${server.port}`).then((outJson) => {
                        let results = {};
                        if (outJson.hasOwnProperty('startTime') &&
                            outJson.hasOwnProperty('testResults')) {

                            ['numPassedTests', 'numFailedTests', 'success','testResults','message', 'testNums'].forEach((k) => {
                                results[k] = outJson[k];
                            });
                        } else {
                            results = createFailedResultJson(`Test result unavailable`,
                                `Test results not available for: ${server.name}`);
                        }
                        
                        common.deleteProcess(server);
                        common.saveResults(server, results);
                    });
                } else {
                    // Execute the tests using shell.exec
                    const cmd = `(cd ../.. && TARGET=http://${server.ip}:${server.port} node ./__acceptancetests__/acceptanceFetchTests.js)`;

                    // Execute the test and store the pid
                    const pid = shell.exec(cmd, {
                        async: true
                    }, (code, out, err) => {
                        let outJson;
                        try {
                            outJson = JSON.parse(out);
                        } catch (err) {
                            console.log('Error parsing cmd output: ' + err);
                            outJson = {}
                        }

                        let results = {};
                        if (outJson.hasOwnProperty('startTime') &&
                            outJson.hasOwnProperty('testResults')) {

                            ['numPassedTests', 'numFailedTests', 'success','testResults','message'].forEach((k) => {
                                results[k] = outJson[k];
                            });
                        } else {
                            results = createFailedResultJson(`Test result unavailable`,
                                `Test results not available for: ${server.name}`);
                        }
                        
                        common.deleteProcess(server);
                        common.saveResults(server, results);
                    });
                }
                common.saveProcess(server, server.name);
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
