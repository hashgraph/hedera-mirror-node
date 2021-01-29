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

const config = require('./config');

const currentResults = {}; // Results of current tests are stored here
const pids = {}; // PIDs for the monitoring test processes

/**
 * Initializer for results
 * @return {} None. Updates currentResults
 */
const initResults = () => {
  const {servers} = config;

  for (const server of servers) {
    currentResults[server.name] = {
      ...server,
      results: [],
    };
  }
};

/**
 * Saves the results from the current test run
 * @param {Object} server The server under test
 * @param {Object} results The results of the current test run
 * @return {} None. Updates currentResults
 */
const saveResults = (server, results) => {
  if (server.name) {
    currentResults[server.name] = {
      ...server,
      results,
    };
  }
};

/**
 * Gets the current results of the server
 *
 * @param {String} name server name
 * @return {Object} results object
 */
const getServerCurrentResults = (name) => {
  return currentResults[name].results;
};

/**
 * Getter for a snapshot of results
 * @param {} None
 * @return {Object} Snapshot of results from the latest completed round of tests
 */
const getStatus = () => {
  const results = Object.values(currentResults);
  const httpErrorCodes = results
    .map((result) => result.httpCode)
    .filter((httpCode) => httpCode < 200 || httpCode > 299);
  const httpCode = httpErrorCodes.length === 0 ? 200 : 409;
  return {
    results,
    httpCode,
  };
};

/**
 * Getter for a snapshot of results for a server specified in the HTTP request
 * @param {String} name server name
 * @return {Object} Snapshot of results from the latest completed round of tests for
 *      the specified server
 */
const getStatusByName = (name) => {
  let ret = {
    numPassedTests: 0,
    numFailedTests: 0,
    success: false,
    testResults: [],
    message: 'Failed',
    httpCode: 400,
  };

  // Return 404 (Not found) for illegal name of the serer
  if (name === undefined || name === null) {
    ret.httpCode = 404;
    ret.message = `Not found. Net: ${name}`;
    return ret;
  }

  // Return 404 (Not found) for if the server doesn't appear in our results table
  if (!currentResults[name] || !currentResults[name].results) {
    ret.httpCode = 404;
    ret.message = `Test results unavailable for server: ${name}`;
    return ret;
  }

  // Return the results saved in the currentResults object
  ret = currentResults[name];
  ret.httpCode = currentResults[name].results.success ? 200 : 409;
  return ret;
};

/**
 * Get the process pid for a given server
 * @param {Object} server the server for which the pid is requested
 * @return {Number} PID of the test process running for the given server
 */
const getProcess = (server) => {
  const key = `${server.ip}_${server.port}`;
  return pids[key];
};

/**
 * Stores the process pid for a given server
 * @param {Object} server the server for which the pid needs to be stored
 * @return {} None
 */
const saveProcess = (server, count) => {
  const key = `${server.ip}_${server.port}`;
  pids[key] = {
    id: server.name,
    encountered: count,
  };
};

/**
 * Deletes the process pid for a given server (when the test process returns)
 * @param {Object} server the server for which the pid needs to be deleted
 * @return {} None
 */
const deleteProcess = (server) => {
  const key = `${server.ip}_${server.port}`;
  delete pids[key];
};

module.exports = {
  initResults,
  saveResults,
  getServerCurrentResults,
  getStatus,
  getStatusByName,
  getProcess,
  saveProcess,
  deleteProcess,
};
