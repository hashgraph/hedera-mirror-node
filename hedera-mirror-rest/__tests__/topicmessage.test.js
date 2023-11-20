/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

import * as constants from '../constants';
import EntityId from '../entityId';
import {assertSqlQueryEqual} from './testutils';
import topicmessage from '../topicmessage';
import config from '../config.js';
import {InvalidArgumentError} from '../errors/index.js';
import sinon from 'sinon';

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
  test('extractSqlFromTopicMessagesRequest', async () => {
    config.query.v2.topicMessageLookups = false;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' > ', value: '2'},
      {key: constants.filterKeys.TIMESTAMP, operator: ' <= ', value: '1234567890.000000006'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlFromTopicMessagesRequest(
      EntityId.parse('7'),
      filters
    );

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

describe('topicmessage extractSqlFromTopicMessagesLookup tests for V2', () => {
  test('extractSqlFromTopicMessagesLookup for single eq sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' = ', value: '2'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select 
                         lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                         from topic_message_lookup
                         where topic_id = $1
                           and sequence_number_range && '[2,5]'::int8range
                         order by sequence_number_range desc
                         limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for range >= sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' >= ', value: '2'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select
                               lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                           from topic_message_lookup
                           where topic_id = $1
                             and sequence_number_range && '[2,5]'::int8range
                           order by sequence_number_range desc
                               limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for range >= and > sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' >= ', value: '2'},
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' > ', value: '20'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select
                               lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                           from topic_message_lookup
                           where topic_id = $1
                             and sequence_number_range && '[21,24]'::int8range
                           order by sequence_number_range desc
                               limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for range <= and < sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' <= ', value: '200'},
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' < ', value: '150'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select
                               lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                           from topic_message_lookup
                           where topic_id = $1
                             and sequence_number_range && '[146,149]'::int8range
                           order by sequence_number_range desc
                               limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for range <= sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' <= ', value: '200'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select
                               lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                           from topic_message_lookup
                           where topic_id = $1
                             and sequence_number_range && '[197,200]'::int8range
                           order by sequence_number_range desc
                               limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for range >= and < sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' >= ', value: '2'},
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' < ', value: '200'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select 
                         lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                         from topic_message_lookup
                         where topic_id = $1
                           and sequence_number_range && '[2,199]'::int8range
                         order by sequence_number_range desc
                         limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for range > and <= sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' > ', value: '2'},
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' <= ', value: '200'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlForTopicMessagesLookup(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select 
                         lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                         from topic_message_lookup
                         where topic_id = $1
                           and sequence_number_range && '[3,200]'::int8range
                         order by sequence_number_range desc
                         limit 3`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7]);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesLookup for multiple eq operator for sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' = ', value: '2'},
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' = ', value: '4'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    expect(() => {
      topicmessage.extractSqlForTopicMessagesLookup(EntityId.parse('7'), filters);
    }).toThrowError(InvalidArgumentError);
  });
  test('extractSqlFromTopicMessagesLookup for multiple ne operator for sequence_number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' != ', value: '2'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    expect(() => {
      topicmessage.extractSqlForTopicMessagesLookup(EntityId.parse('7'), filters);
    }).toThrowError(InvalidArgumentError);
  });
});

describe('topicmessage extractSqlFromTopicMessagesRequest tests for V2', () => {
  test('extractSqlFromTopicMessagesRequest V2 with seq number parameter', async () => {
    config.query.v2.topicMessageLookups = true;
    const expectedRangeStartConsensusNs1 = '1234567890000000006';
    const expectedRangeEndConsensusNs1 = '1234567891000000006';
    const expectedRangeStartConsensusNs2 = '1234577890000000006';
    const expectedRangeEndConsensusNs2 = '1234577891000000006';
    const validQueryResult = {
      rows: [
        {timestamp_start: expectedRangeStartConsensusNs1, timestamp_end: expectedRangeEndConsensusNs1},
        {timestamp_start: expectedRangeStartConsensusNs2, timestamp_end: expectedRangeEndConsensusNs2},
      ],
    };
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    const filters = [
      {key: constants.filterKeys.SEQUENCE_NUMBER, operator: ' > ', value: '2'},
      {key: constants.filterKeys.TIMESTAMP, operator: ' <= ', value: '1234567890.000000006'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlFromTopicMessagesRequest(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select * from topic_message 
    where topic_id = $1 
    and consensus_timestamp <= $2 
    and ((consensus_timestamp >= 1234567890000000006 and consensus_timestamp < 1234567891000000006)
     or (consensus_timestamp >= 1234577890000000006 and consensus_timestamp < 1234577891000000006)) 
     order by consensus_timestamp desc limit $3;`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7, '1234567890.000000006', '3']);
    expect(order).toStrictEqual(constants.orderFilterValues.DESC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesRequest V2 without seq number parameter order asc', async () => {
    config.query.v2.topicMessageLookups = true;
    const expectedRangeStartConsensusNs1 = '1234567890000000006';
    const expectedRangeEndConsensusNs1 = '1234567891000000006';
    const expectedRangeStartConsensusNs2 = '1234577890000000006';
    const expectedRangeEndConsensusNs2 = '1234577891000000006';
    const validQueryResult = {
      rows: [
        {timestamp_start: expectedRangeStartConsensusNs1, timestamp_end: expectedRangeEndConsensusNs1},
        {timestamp_start: expectedRangeStartConsensusNs2, timestamp_end: expectedRangeEndConsensusNs2},
      ],
    };
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    const filters = [
      {key: constants.filterKeys.TIMESTAMP, operator: ' <= ', value: '1234567890.000000006'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.ASC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlFromTopicMessagesRequest(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select * from topic_message 
    where topic_id = $1 
    and consensus_timestamp <= $2 
    and sequence_number = (select MIN(lower(sequence_number_range))
                           from topic_message_lookup where topic_id = $1) 
     order by consensus_timestamp asc limit $3;`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7, '1234567890.000000006', '3']);
    expect(order).toStrictEqual(constants.orderFilterValues.ASC);
    expect(limit).toStrictEqual(3);
  });
  test('extractSqlFromTopicMessagesRequest V2 without seq number parameter order desc', async () => {
    config.query.v2.topicMessageLookups = true;
    const expectedRangeStartConsensusNs1 = '1234567890000000006';
    const expectedRangeEndConsensusNs1 = '1234567891000000006';
    const expectedRangeStartConsensusNs2 = '1234577890000000006';
    const expectedRangeEndConsensusNs2 = '1234577891000000006';
    const validQueryResult = {
      rows: [
        {timestamp_start: expectedRangeStartConsensusNs1, timestamp_end: expectedRangeEndConsensusNs1},
        {timestamp_start: expectedRangeStartConsensusNs2, timestamp_end: expectedRangeEndConsensusNs2},
      ],
    };
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    const filters = [
      {key: constants.filterKeys.TIMESTAMP, operator: ' <= ', value: '1234567890.000000006'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const {query, params, order, limit} = await topicmessage.extractSqlFromTopicMessagesRequest(
      EntityId.parse('7'),
      filters
    );

    const expectedQuery = `select * from topic_message 
    where topic_id = $1 
    and consensus_timestamp <= $2 
    and sequence_number = (select MAX(upper(sequence_number_range))
                           from topic_message_lookup where topic_id = $1) 
     order by consensus_timestamp desc limit $3;`;
    assertSqlQueryEqual(query, expectedQuery);
    expect(params).toStrictEqual([7, '1234567890.000000006', '3']);
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
