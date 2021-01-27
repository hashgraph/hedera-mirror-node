/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const common = require('./common.js');
const monitorTests = require('./monitor_tests.js');
const utils = require('./utils.js');

const retryCountMax = 3; // # of times a single process can retry

/**
 * Main function to run the tests and save results
 * @param {Array} servers array of servers to test
 * @return {} None
 */
const runEverything = async (servers) => {
  try {
    const restservers = undefined === servers ? common.getServerList().servers : servers;

    if (restservers.length === 0) {
      return;
    }

    for (const server of restservers) {
      const processObj = common.getProcess(server);

      if (processObj === undefined) {
        // execute test and store name
        monitorTests.runTests(server).then((outJson) => {
          let results;
          if (outJson.testResults) {
            results = outJson;
            const total = results.testResults.length;
            logger.info(`Completed tests run for ${server.name} with ${results.numPassedTests}/${total} tests passed`);
          } else {
            results = utils.createFailedResultJson(
              `Test result unavailable`,
              `Test results not available for: ${server.name}`
            );
            logger.warn(`Incomplete tests for ${server.name}`);
          }

          common.deleteProcess(server);
          common.saveResults(server, results);
        });

        common.saveProcess(server, 1);
      } else {
        const results = utils.createFailedResultJson(
          `Test result unavailable`,
          `Previous tests are still running for: ${server.name}`
        );

        // escape race condition, kill stored process and allow next process to attempt test
        if (processObj.encountered >= retryCountMax) {
          logger.warn(
            `Previous tests for ${server.name} persisted for ${processObj.encountered} rounds. Clearing saved process.`
          );
          common.deleteProcess(server);
        } else {
          logger.info(
            `Previous tests for ${server.name} still running after ${processObj.encountered} rounds. Incrementing count.`
          );
          common.saveProcess(server, processObj.encountered + 1);
        }

        common.saveResults(server, results);
        logger.warn(`Incomplete tests for ${server.name}`);
      }
    }
  } catch (err) {
    logger.error(`Error in runEverything: `, err);
  }
};

module.exports = {
  runEverything,
};
