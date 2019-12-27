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
let pids = {}; // PIDs for the monitoring test processes

/**
 * Initializer for results
 * @return {} None. Updates currentResults
 */
const initResults = () => {
  const restservers = getServerList().servers;

  for (const server of restservers) {
    currentResults[server.name] = {
      ip: server.ip,
      port: server.port,
      results: []
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
  if (server.name != undefined && server.name != null) {
    currentResults[server.name] = {
      ip: server.ip,
      port: server.port,
      results: results
    };
  }
};

/**
 * Getter for a snapshot of results
 * @param {} None
 * @return {Object} Snapshot of results from the latest completed round of tests
 */
const getStatus = () => {
  let results = Object.keys(currentResults).map(net => {
    currentResults[net].name = net;

    return currentResults[net];
  });
  return {
    results: results,
    httpCode: 200
  };
};

/**
 * Getter for a snapshot of results for a server specified in the HTTP request
 * @param {HTTPRequest} req HTTP Request object
 * @param {HTTPResponse} res HTTP Response object
 * @return {Object} Snapshot of results from the latest completed round of tests for
 *      the specified server
 */
const getStatusWithId = net => {
  let ret = {
    numPassedTests: 0,
    numFailedTests: 0,
    success: false,
    testResults: [],
    message: 'Failed',
    httpCode: 400
  };

  // Return 404 (Not found) for illegal name of the serer
  if (net == undefined || net == null) {
    ret.httpCode = 404;
    ret.message = `Not found. Net: ${net}`;
    return ret;
  }

  // Return 404 (Not found) for if the server doesn't appear in our results table
  if (
    !currentResults.hasOwnProperty(net) ||
    currentResults.hasOwnProperty(net) == undefined ||
    !currentResults[net].hasOwnProperty('results')
  ) {
    ret.httpCode = 404;
    ret.message = `Test results unavailable for Net: ${net}`;
    return ret;
  }

  // Return the results saved in the currentResults object
  ret = currentResults[net];
  ret.httpCode = currentResults[net].results.success ? 200 : 409;
  return ret;
};

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
    return config;
  } catch (err) {
    return {};
  }
};

/**
 * Get the process pid for a given server
 * @param {Object} server the server for which the pid is requested
 * @return {Number} PID of the test process running for the given server
 */
const getProcess = server => {
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
  const processObject = {
    id: server.name,
    encountered: count
  };

  pids[key] = processObject;
};

/**
 * Deletes the process pid for a given server (when the test process returns)
 * @param {Object} server the server for which the pid needs to be deleted
 * @return {} None
 */
const deleteProcess = server => {
  const key = `${server.ip}_${server.port}`;
  delete pids[key];
};

module.exports = {
  initResults: initResults,
  saveResults: saveResults,
  getStatus: getStatus,
  getStatusWithId: getStatusWithId,
  getServerList: getServerList,
  getProcess: getProcess,
  saveProcess: saveProcess,
  deleteProcess: deleteProcess
};
