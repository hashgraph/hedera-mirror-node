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

const config = require('./config');
const {
  checkAPIResponseError,
  checkRespObjDefined,
  checkRespArrayLength,
  checkMandatoryParams,
  checkConsensusTimestampOrder,
  getAPIResponse,
  getUrl,
  testRunner,
  CheckRunner,
} = require('./utils');

const resource = 'topic';
const resourceLimit = config[resource].limit;
const {topicId} = config[resource];
const jsonRespKey = 'messages';
const mandatoryParams = [
  'consensus_timestamp',
  'topic_id',
  'message',
  'running_hash',
  'running_hash_version',
  'sequence_number',
];

const topicMessagesPath = (id, sequence = undefined) => {
  const path = `/topics/${id}/messages`;
  if (sequence === undefined) {
    return path;
  }

  return `${path}/${sequence}`;
};

const checkSequenceNumberOrder = (messages, option) => {
  const errorMessage = (asc, previous, current) => {
    const order = asc ? 'ascending' : 'descending';
    return `topic message sequence is not in ${order} order / contiguous: previous - ${previous}, current - ${current}`;
  };

  const {asc} = option;
  let previous = 0;
  for (const message of messages) {
    const sequence = message.sequence_number;
    if (previous !== 0) {
      if ((asc && sequence !== previous + 1) || (!asc && sequence >= previous + 1)) {
        return {passed: false, message: errorMessage(asc, previous, sequence)};
      }
    }

    previous = sequence;
  }

  return {passed: true};
};

/**
 * Verifies /topics/:topicId/messages
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessages = async (server) => {
  let url = getUrl(server, topicMessagesPath(topicId), {limit: resourceLimit});
  let messages = await getAPIResponse(url, jsonRespKey);

  const checkRunner = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `messages.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .withCheckSpec(checkConsensusTimestampOrder, {asc: true})
    .withCheckSpec(checkSequenceNumberOrder, {asc: true});
  let result = checkRunner.run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, topicMessagesPath(topicId), {limit: resourceLimit, order: 'desc'});
  messages = await getAPIResponse(url, jsonRespKey);

  result = checkRunner
    .resetCheckSpec(checkConsensusTimestampOrder, {asc: false})
    .resetCheckSpec(checkSequenceNumberOrder, {asc: false})
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics for ${topicId} and performed order check`,
  };
};

/**
 * Verifies /topics/0.0.2/messages returns 400
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesForNonTopicEntityId = async (server) => {
  const url = getUrl(server, topicMessagesPath('0.0.2'), {limit: resourceLimit});
  const resp = await getAPIResponse(url);

  const result = new CheckRunner().withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 400}).run(resp);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: 'Successfully called topics on non-topic entity id 0.0.2 and got expected 400',
  };
};

/**
 * Verifies /topics/:topicId/messages with non-existing topic ID returns 404
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesForNonExistingTopicId = async (server) => {
  const nonExistingTopicId = `${config.shard + 1}.0.1930`;
  const url = getUrl(server, topicMessagesPath(nonExistingTopicId), {limit: resourceLimit});
  const resp = await getAPIResponse(url);

  const result = new CheckRunner().withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 404}).run(resp);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics on non-existing topic ID ${nonExistingTopicId} and got expected 404`,
  };
};

/**
 * Verifies /topics/:topicId/messages/:sequencenumber
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesByTopicIDAndSequenceNumber = async (server) => {
  let url = getUrl(server, topicMessagesPath(topicId, 1));
  let message = await getAPIResponse(url);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, topicMessagesPath(topicId, 0));
  message = await getAPIResponse(url);

  result = new CheckRunner().withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 400}).run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, topicMessagesPath(topicId, -1));
  message = await getAPIResponse(url);

  result = new CheckRunner().withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 400}).run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics for ${topicId} and message sequence number 1`,
  };
};

/**
 * Verifies /topics/messages/:consensusTimestamp
 *
 * @param {String} server API host endpoint
 * @return {{url: String, passed: boolean, message: String}}
 */
const getTopicMessagesByConsensusTimestamp = async (server) => {
  let url = getUrl(server, topicMessagesPath(topicId), {limit: resourceLimit});
  const messages = await getAPIResponse(url, jsonRespKey);

  let result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'messages is undefined'})
    .withCheckSpec(checkRespArrayLength, {
      limit: resourceLimit,
      message: (elements, limit) => `messages.length of ${elements.length} is less than limit ${limit}`,
    })
    .withCheckSpec(checkMandatoryParams, {
      params: mandatoryParams,
      message: 'topic message object is missing some mandatory fields',
    })
    .run(messages);
  if (!result.passed) {
    return {url, ...result};
  }

  const consensusTimestamp = messages[0].consensus_timestamp;
  url = getUrl(server, `/topics/messages/${consensusTimestamp}`);
  let message = await getAPIResponse(url);

  result = new CheckRunner()
    .withCheckSpec(checkAPIResponseError)
    .withCheckSpec(checkRespObjDefined, {message: 'message is undefined'})
    .withCheckSpec((element) => {
      const actual = element.consensus_timestamp;
      if (actual !== consensusTimestamp) {
        return {
          passed: false,
          message: `expect message.consensus_timestamp to be ${consensusTimestamp}, got ${actual}`,
        };
      }
      return {passed: true};
    })
    .run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  url = getUrl(server, '/topics/messages/0');
  message = await getAPIResponse(url);

  result = new CheckRunner().withCheckSpec(checkAPIResponseError, {expectHttpError: true, status: 404}).run(message);
  if (!result.passed) {
    return {url, ...result};
  }

  return {
    url,
    passed: true,
    message: `Successfully called topics with consensus timestamp`,
  };
};

/**
 * Run all tests in an asynchronous fashion waiting for all tests to complete
 *
 * @param {String} server API host endpoint
 * @param {Object} classResults shared class results object capturing tests for given endpoint
 */
const runTests = async (server, classResults) => {
  const tests = [];
  const runTest = testRunner(server, classResults);
  tests.push(runTest(getTopicMessages));
  tests.push(runTest(getTopicMessagesForNonTopicEntityId));
  tests.push(runTest(getTopicMessagesForNonExistingTopicId));
  tests.push(runTest(getTopicMessagesByTopicIDAndSequenceNumber));
  tests.push(runTest(getTopicMessagesByConsensusTimestamp));

  return Promise.all(tests);
};

module.exports = {
  runTests,
};
