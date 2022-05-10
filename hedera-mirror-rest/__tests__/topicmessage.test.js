/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
const constants = require('../constants.js');
const EntityId = require('../entityId');
const {assertSqlQueryEqual} = require('./testutils');

describe('topicmessage validateConsensusTimestampParam tests', () => {
  test('Verify validateConsensusTimestampParam throws error for -1234567890.000000001', () => {
    verifyInvalidConsensusTimestamp(-1234567890.000000001);
  });

  test('Verify validateConsensusTimestampParam throws error for abc', () => {
    verifyInvalidConsensusTimestamp('abc');
  });

  test('Verify validateConsensusTimestampParam does not throw error for 1234567890', () => {
    verifyValidConsensusTimestamp(1234567890);
  });

  test('Verify validateConsensusTimestampParam does not throw error for 123.0001', () => {
    verifyValidConsensusTimestamp(123.0001);
  });
});

describe('topicmessage validateGetSequenceMessageParams tests', () => {
  test('Verify validateGetSequenceMessageParams throws error for -123', () => {
    verifyInvalidTopicAndSequenceNum(-123, -123);
  });

  test('Verify validateGetSequenceMessageParams throws error for abc', () => {
    verifyInvalidTopicAndSequenceNum('abc', 'abc');
  });

  test('Verify validateGetSequenceMessageParams throws error for 123.0001', () => {
    verifyInvalidTopicAndSequenceNum(123.0001, 123.0001);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for 0', () => {
    verifyValidTopicAndSequenceNum(0, 1);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for 1234567890', () => {
    verifyValidTopicAndSequenceNum(1234567890, 1234567890);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for 2', () => {
    verifyValidTopicAndSequenceNum(2, 1234567890);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for "0.0.2"', () => {
    verifyValidTopicAndSequenceNum('0.0.2', 1234567890);
  });

  test('Verify validateGetSequenceMessageParams does not throw error for "0.2"', () => {
    verifyValidTopicAndSequenceNum('0.2', 1234567890);
  });
});

describe('topicmessage validateGetTopicMessagesParams tests', () => {
  test('Verify validateGetTopicMessagesParams throws error for -123', () => {
    verifyInvalidTopicMessages(-123);
  });

  test('Verify validateGetTopicMessagesParams throws error for abc', () => {
    verifyInvalidTopicMessages('abc');
  });

  test('Verify validateGetTopicMessagesParams throws error for 123.0001', () => {
    verifyInvalidTopicMessages(123.0001);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for 0', () => {
    verifyValidTopicMessages(0);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for 1234567890', () => {
    verifyValidTopicMessages(1234567890);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for 2', () => {
    verifyValidTopicMessages(2);
  });

  test('Verify validateGetTopicMessagesParams does not throw error for "0.0.2"', () => {
    verifyValidTopicMessages('0.0.2');
  });

  test('Verify validateGetTopicMessagesParams does not throw error for "0.2"', () => {
    verifyValidTopicMessages('0.2');
  });
});

describe('topicmessage extractSqlFromTopicMessagesRequest tests', () => {
  test('extractSqlFromTopicMessagesRequest', () => {
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' > ', value: '2'},
      {key: constants.filterKeys.TIMESTAMP, operator: ' <= ', value: '1234567890.000000006'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = topicmessage.extractSqlFromTopicMessagesRequest(EntityId.parse('7'), filters);

    const expectedQuery = `select *
                         from topic_message
                         where topic_id = $1
                           and sequence_number > $2
                           and consensus_timestamp <= $3
                         order by consensus_timestamp desc
                         limit $4;`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7, '2', '1234567890.000000006', '3']);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
});

const verifyValidConsensusTimestamp = (timestamp) => {
  expect(() => {
    topicmessage.validateConsensusTimestampParam(timestamp);
  }).not.toThrow();
};

const verifyValidTopicAndSequenceNum = (topicid, seqnum) => {
  expect(() => {
    topicmessage.validateGetSequenceMessageParams(topicid, seqnum);
  }).not.toThrow();
};

const verifyValidTopicMessages = (topicid) => {
  expect(() => {
    topicmessage.validateGetTopicMessagesParams(topicid);
  }).not.toThrow();
};

const verifyInvalidConsensusTimestamp = (timestamp) => {
  expect(() => {
    topicmessage.validateConsensusTimestampParam(timestamp);
  }).toThrowErrorMatchingSnapshot();
};

const verifyInvalidTopicAndSequenceNum = (topicid, seqnum) => {
  expect(() => {
    topicmessage.validateGetSequenceMessageParams(topicid, seqnum);
  }).toThrowErrorMatchingSnapshot();
};

const verifyInvalidTopicMessages = (topicid) => {
  expect(() => {
    topicmessage.validateGetTopicMessagesParams(topicid);
  }).toThrowErrorMatchingSnapshot();
};
