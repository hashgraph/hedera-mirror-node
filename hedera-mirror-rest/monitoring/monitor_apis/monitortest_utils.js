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
const _ = require('lodash');
const fetch = require('node-fetch');
const querystring = require('querystring');

const apiPrefix = '/api/v1';

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
 * @param {String} key JSON key of the object to return
 * @return {Object} JSON object representing api response or error
 */
const getAPIResponse = async (url, key = undefined) => {
  const controller = new AbortController();
  const timeout = setTimeout(
    () => {
      controller.abort();
    },
    60 * 1000 // in ms
  );

  try {
    const response = await fetch(url, {signal: controller.signal});
    if (!response.ok) {
      console.log(`Non success response for call to '${url}'`);
      return Error(response.statusText);
    }

    const json = await response.json();
    return key ? json[key] : json;
  } catch (error) {
    const message = `Fetch error, url : ${url}, error : ${error}`;
    console.log(message);
    return Error(message);
  } finally {
    clearTimeout(timeout);
  }
};

/**
 * Retrieve a new instance of the monitoring class results object
 */
const getMonitorClassResult = () => {
  return {
    testResults: [],
    numPassedTests: 0,
    numFailedTests: 0,
    success: true,
    message: '',
    startTime: Date.now(),
    endTime: 0,
  };
};

const testRunner = (server, testClassResult) => {
  return async (testFunc) => {
    const start = Date.now();
    const result = await testFunc(server);

    const testResult = {
      at: start,
      result: result.passed ? 'passed' : 'failed',
      url: result.url,
      message: result.passed ? result.message : '',
      failureMessages: !result.passed ? [result.message] : [],
    };

    testClassResult.testResults.push(testResult);
    if (result.passed) {
      testClassResult.numPassedTests++;
    } else {
      testClassResult.numFailedTests++;
      testClassResult.success = false;
    }
  };
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

const checkAPIResponseError = (resp) => {
  if (resp instanceof Error) {
    return {
      passed: false,
      message: resp.message,
    };
  }
  return {passed: true};
};

const checkRespObjDefined = (resp, option) => {
  const {message} = option;
  if (resp === undefined) {
    return {
      passed: false,
      message,
    };
  }
  return {passed: true};
};

const checkRespArrayLength = (elements, option) => {
  const {limit, message} = option;
  if (elements.length !== limit) {
    return {
      passed: false,
      message: message(elements, limit),
    };
  }
  return {passed: true};
};

const checkAccountNumber = (elements, option) => {
  const {accountNumber, message} = option;
  const element = Array.isArray(elements) ? elements[0] : elements;
  if (element.account !== fromAccNum(accountNumber)) {
    return {
      passed: false,
      message,
    };
  }
  return {passed: true};
};

const checkMandatoryParams = (elements, option) => {
  const element = Array.isArray(elements) ? elements[0] : elements;
  const {params, message} = option;
  for (let index = 0; index < params.length; index += 1) {
    if (!_.has(element, params[index])) {
      return {
        passed: false,
        message: `${message}: ${params[index]}`,
      };
    }
  }

  return {passed: true};
};

const checkRespDataFreshness = (resp, option) => {
  const {timestamp, threshold, message} = option;
  const ts = timestamp(Array.isArray(resp) ? resp[0] : resp);
  const secs = ts.split('.')[0];
  const currSecs = Math.floor(new Date().getTime() / 1000);
  const delta = currSecs - secs;
  if (delta > threshold) {
    return {
      passed: false,
      message: message(delta),
    };
  }

  return {passed: true};
};

class CheckRunner {
  constructor() {
    this.checkSpecs = [];
  }

  withCheckSpec(check, option = {}) {
    this.checkSpecs.push({check, option});
    return this;
  }

  run(data) {
    for (const checkSpec of this.checkSpecs) {
      const {check, option} = checkSpec;
      const result = check(data, option);
      if (!result.passed) {
        return result;
      }
    }

    return {passed: true};
  }
}

module.exports = {
  toAccNum,
  fromAccNum,
  getUrl,
  getAPIResponse,
  getMonitorClassResult,
  createFailedResultJson,
  getMaxLimit,
  testRunner,
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkAccountNumber,
  checkMandatoryParams,
  checkRespDataFreshness,
  CheckRunner,
};
