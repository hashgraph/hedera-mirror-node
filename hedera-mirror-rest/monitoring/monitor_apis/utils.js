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

'use strict';

const AbortController = require('abort-controller');
const httpErrors = require('http-errors');
const _ = require('lodash');
const fetch = require('node-fetch');
const querystring = require('querystring');
const config = require('./config');

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
      return httpErrors(response.status, response.statusText);
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

class ServerTestResult {
  constructor() {
    this.result = {
      testResults: [],
      numPassedTests: 0,
      numFailedTests: 0,
      success: true,
      message: '',
      startTime: Date.now(),
      endTime: 0,
    };
  }

  addTestResult(testResult) {
    this.result.testResults.push(testResult);
    if (testResult.result === 'passed') {
      this.result.numPassedTests += 1;
    } else {
      this.result.numFailedTests += 1;
      this.result.success = false;
    }
  }

  finish() {
    this.result.endTime = Date.now();
  }
}

/**
 * Creates a function to run specific tests with the provided server address, classs result, and resource
 *
 * @param {String} server server address in the format of http://ip:port
 * @param {ServerTestResult} testClassResult test class result object
 * @param {String} resource name of the resource to test
 * @return {function(...[*]=)}
 */
const testRunner = (server, testClassResult, resource) => {
  return async (testFunc) => {
    const start = Date.now();
    const result = await testFunc(server);
    if (result.skipped) {
      return;
    }

    const testResult = {
      at: start,
      result: result.passed ? 'passed' : 'failed',
      url: result.url,
      message: result.passed ? result.message : '',
      failureMessages: !result.passed ? [result.message] : [],
      resource,
    };

    testClassResult.addTestResult(testResult);
  };
};

/**
 * Helper function to create a json object for failed test results
 * @param {String} title Title in the jest output
 * @param {String} msg Message in the jest output
 * @return {Object} Constructed failed result object
 */
const createFailedResultJson = (title, msg) => {
  const failedResultJson = new ServerTestResult().result;
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

const checkAPIResponseError = (resp, option) => {
  const {expectHttpError, status} = option;
  const isRespError = resp instanceof Error;
  const isRespHttpError = resp instanceof httpErrors.HttpError;

  if (expectHttpError) {
    if (isRespHttpError && (!status || resp.status === status)) {
      return {passed: true};
    }

    const actual = isRespError ? JSON.stringify(resp) : '2xx';
    return {
      passed: false,
      message: `expect http error ${status || 'any'}, got ${actual}`,
    };
  }

  if (isRespError) {
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

const checkConsensusTimestampOrder = (elements, option) => {
  const {asc} = option;
  let previous = asc ? '0' : 'A';
  for (const element of elements) {
    const timestamp = element.consensus_timestamp;
    if (asc && timestamp <= previous) {
      return {passed: false, message: 'consensus timestamps are not in ascending order'};
    }
    if (!asc && timestamp >= previous) {
      return {passed: false, message: 'consensus timestamps are not in descending order'};
    }
    previous = timestamp;
  }

  return {passed: true};
};

/**
 * Checks resource freshness
 *
 * @param server the server address in the format of http://ip:port
 * @param path resource path
 * @param resource resource name
 * @param timestamp function to extract timestamp from response
 * @param jsonRespKey json response key to extract data from json response
 * @return {Promise<>}
 */
const checkResourceFreshness = async (server, path, resource, timestamp, jsonRespKey) => {
  const {freshnessThreshold} = config[resource];
  if (freshnessThreshold === 0) {
    return {skipped: true};
  }

  const url = getUrl(server, path, {limit: 1, order: 'desc'});
  const resp = await getAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: `${resource}: response object is undefined`});
  if (Array.isArray(resp)) {
    checkRunner.withCheckSpec(checkRespArrayLength, {
      limit: 1,
      message: (elements) => `${resource}: response data length of ${elements.length} was expected to be 1`,
    });
  }
  const result = checkRunner
    .withCheckSpec(checkRespDataFreshness, {
      timestamp,
      threshold: freshnessThreshold,
      message: (delta) => `${resource} was stale, ${delta} seconds old`,
    })
    .run(resp);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully retrieved ${resource} from with ${freshnessThreshold} seconds ago`,
  };
};

class CheckRunner {
  constructor() {
    this.checkSpecs = [];
  }

  withCheckSpec(check, option = {}) {
    this.checkSpecs.push({check, option});
    return this;
  }

  resetCheckSpec(check, option = {}) {
    this.checkSpecs.forEach((checkSpec) => {
      if (checkSpec.check === check) {
        checkSpec.option = option;
      }
    });
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
  createFailedResultJson,
  testRunner,
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkAccountNumber,
  checkMandatoryParams,
  checkConsensusTimestampOrder,
  checkResourceFreshness,
  CheckRunner,
  ServerTestResult,
};
