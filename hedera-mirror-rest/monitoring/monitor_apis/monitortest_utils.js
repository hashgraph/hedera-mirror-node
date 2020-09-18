/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const AbortController = require('abort-controller');
const config = require('../../config');
const long = require('long');
const fetch = require('node-fetch');
const querystring = require('querystring');

const apiPrefix = '/api/v1';

// monitoring class results template
const classResults = {
  testResults: [],
  numPassedTests: 0,
  numFailedTests: 0,
  success: true,
  message: '',
  startTime: 0,
  endTime: 0,
};

// monitoring single test result template
const testResult = {
  at: '', // start time of test in millis since epoch
  result: 'failed', // result of test
  url: '', // last rest-api endpoint call made in test
  message: '', // test message
  failureMessages: [], // failure messages
};

/**
 * Converts consensus timestamp seconds.nanoseconds to nanoseconds
 * @param {String} consensusTimestamp consensus timestamp
 * @return {Long} timestamp in nanoseconds
 */
const consensusTimestampToNanos = (consensusTimestamp) => {
  const parts = consensusTimestamp.split('.');
  const seconds = long.fromString(parts[0]);
  const nanos = parseInt(parts[1], 10);
  return seconds.mul(1000000000).add(nanos);
};

/**
 * Converts shard.realm.accountNumber to accountNumber
 * @param {String} shard.realm.accountNumber
 * @return {Number} accountNumber
 */
const toAccNum = (accId) => Number(accId.split('.')[2]);

/**
 * Converts accountNumber to shard.realm.accountNumber string
 * @param {Number} accountNumber
 * @return {String} shard.realm.accountNumber
 */
const fromAccNum = (accNum) => `${config.shard}.0.${accNum}`;

/**
 * Return a deep clone of a json object
 * @param {Object} obj
 */
const cloneObject = (obj) => {
  return JSON.parse(JSON.stringify(obj));
};

/**
 * Create and return the url for a rest api call
 * If running on a local server http is employed over https
 * @param {Object} server
 * @param {String} path rest-api endpoint path
 * @param {Object} query key-value query params
 * @return {String} rest-api endpoint url
 */
const getUrl = (server, path, query = undefined) => {
  let endpoint = server;
  if (server.includes('localhost') || server.includes('127.0.0.1')) {
    endpoint = server.replace('https', 'http');
  }

  let url = `${endpoint}${apiPrefix}${path}`;
  if (query) {
    const qs = querystring.stringify(query);
    if (qs !== '') {
      url += `?${qs}`;
    }
  }

  return url;
};

/**
 * Make an http request to mirror-node api
 * Host info is prepended to if only path is provided
 * @param {*} url rest-api endpoint
 * @return {Object} JSON object representing api response
 */
const getAPIResponse = (url) => {
  const controller = new AbortController();
  const timeout = setTimeout(
    () => {
      controller.abort();
    },
    60 * 1000 // in ms
  );

  return fetch(url, {signal: controller.signal})
    .then((response) => {
      if (!response.ok) {
        console.log(`Non success response for call to '${url}'`);
        throw Error(response.statusText);
      }

      return response.json();
    })
    .catch((error) => {
      var message = `Fetch error, url : ${url}, error : ${error}`;
      console.log(message);
      throw message;
    })
    .finally(() => {
      clearTimeout(timeout);
    });
};

/**
 * Retrieve a new instance of the monitoring class results object
 */
const getMonitorClassResult = () => {
  var newClassResult = cloneObject(classResults);
  newClassResult.startTime = Date.now();
  return newClassResult;
};

/**
 * Retrieve a new instance of the monitoring single test result object
 */
const getMonitorTestResult = () => {
  var newTestResult = cloneObject(testResult);
  newTestResult.at = Date.now();
  return newTestResult;
};

/**
 * Add provided result to list of class results
 * Also increment passed and failed count based
 * @param {Object} clssRes Class result object
 * @param {Object} res Test result
 * @param {Boolean} passed Test passed flag
 */
const addTestResult = (clssRes, res, passed) => {
  clssRes.testResults.push(res);
  passed ? clssRes.numPassedTests++ : clssRes.numFailedTests++;

  // set class results to failure if any tests failed
  if (!passed) {
    clssRes.success = false;
  }
};

/**
 * Helper function to create a json object for failed test results
 * @param {String} title Title in the jest output
 * @param {String} msg Message in the jest output
 * @return {Object} Constructed failed result object
 */
const createFailedResultJson = (title, msg) => {
  const failedResultJson = getMonitorClassResult();
  failedResultJson.numFailedTests = 1;
  failedResultJson.success = false;
  failedResultJson.message = 'Prerequisite tests failed';
  failedResultJson.testResults = [
    {
      at: (new Date().getTime() / 1000).toFixed(3),
      message: `${title}: ${msg}`,
      result: 'failed',
      assertionResults: [
        {
          ancestorTitles: title,
          failureMessages: [],
          fullName: `${title}: ${msg}`,
          location: null,
          status: 'failed',
          title: msg,
        },
      ],
    },
  ];

  return failedResultJson;
};

/**
 * Helper function to get max limit for a resource. Returns the lesser of the resource specific maxLimit if exists and
 * the global maxLimit, otherwise the global maxLimit.
 * @param {String} resource name of the resource
 * @returns {{maxLimit: number, isGlobal: boolean}}
 */
const getMaxLimit = (resource) => {
  const result = {
    maxLimit: config.maxLimit,
    isGlobal: true,
  };

  const {monitor} = config;
  if (!monitor) {
    return result;
  }

  if (monitor[resource] && monitor[resource].maxLimit) {
    const {maxLimit: resourceMaxLimit} = monitor[resource];
    if (resourceMaxLimit < result.maxLimit) {
      result.maxLimit = resourceMaxLimit;
      result.isGlobal = false;
    }
  }

  return result;
};

module.exports = {
  toAccNum,
  fromAccNum,
  consensusTimestampToNanos,
  getUrl,
  cloneObject,
  getAPIResponse,
  getMonitorClassResult,
  getMonitorTestResult,
  addTestResult,
  createFailedResultJson,
  getMaxLimit,
};
