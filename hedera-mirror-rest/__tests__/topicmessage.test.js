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

const topicmessage = require('../topicmessage.js');

beforeAll(async () => {
  jest.setTimeout(1000);
});

afterAll(() => {});

const invalidTimestamp = 'Invalid parameter: consensusTimestamp';

// Start of tests
describe('topicmessage validateConsensusTimestampParam tests', () => {
  test('Verify validateConsensusTimestampParam returns correct result for -1234567890.000000001', () => {
    verifyInvalidConsensusTimestamp(
      topicmessage.validateConsensusTimestampParam(-1234567890.000000001),
      invalidTimestamp
    );
  });
  test('Verify validateConsensusTimestampParam returns correct result for abc', () => {
    verifyInvalidConsensusTimestamp(topicmessage.validateConsensusTimestampParam('abc'), invalidTimestamp);
  });
  test('Verify validateConsensusTimestampParam returns correct result for 1234567890', () => {
    verifyValidConsensusTimestamp(topicmessage.validateConsensusTimestampParam(1234567890));
  });
  test('Verify validateConsensusTimestampParam returns correct result for 123.0001', () => {
    verifyValidConsensusTimestamp(topicmessage.validateConsensusTimestampParam(123.0001));
  });
});

const verifyValidConsensusTimestamp = val => {
  expect(val).toStrictEqual({code: 200, contents: 'OK', isValid: true});
};

const verifyInvalidConsensusTimestamp = (val, message) => {
  expect(val).toStrictEqual({
    code: 400,
    contents: {_status: {messages: [{message: message}]}},
    isValid: false
  });
};
