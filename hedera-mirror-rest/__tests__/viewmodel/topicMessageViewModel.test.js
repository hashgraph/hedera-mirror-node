'use strict';

const {TopicMessageViewModel} = require('../../viewmodel');

describe('topicMessageViewModel tests', () => {
  test('Basic topic message', () => {
    const actual = new TopicMessageViewModel(buildDefaultTopicMessageRow(), '');
    const expected = buildDefaultTopicMessageViewModel();
    expect(actual).toEqual(expected);
  });

  test('Basic topic message with UTF-8', () => {
    const actual = new TopicMessageViewModel(buildDefaultTopicMessageRow(), 'UTF-8');

    const expected = buildDefaultTopicMessageViewModel();
    expected.message = 'message';

    expect(actual).toEqual(expected);
  });

  test('Chunk info with valid start timestamp and payer account id', () => {
    const input = buildDefaultTopicMessageRow();
    input.chunkNum = 1;
    input.chunkTotal = 10;
    input.validStartTimestamp = '1234567890000000000';
    const actual = new TopicMessageViewModel(input, '');

    const expected = buildDefaultTopicMessageViewModel();
    expected.chunk_info = {
      number: 1,
      total: 10,
      initial_transaction_id: '0.0.3-1234567890-000000000',
    };

    expect(actual).toEqual(expected);
  });

  test('Chunk info with saved initial transaction id', () => {
    const input = buildDefaultTopicMessageRow();
    input.chunkNum = 1;
    input.chunkTotal = 10;
    //Account id: 0.0.3, valid start timestamp: 1234567890.000000321, nonce: 1, scheduled: true
    input.initialTransactionId = Buffer.from([
      10, 9, 8, -46, -123, -40, -52, 4, 16, -63, 2, 18, 2, 24, 3, 24, 1, 32, 1,
    ]);
    const actual = new TopicMessageViewModel(input, '');

    const expected = buildDefaultTopicMessageViewModel();
    expected.chunk_info = {
      number: 1,
      total: 10,
      initial_transaction_id: '0.0.3-1234567890-000000321',
      nonce: 1,
      scheduled: true,
    };

    expect(actual).toEqual(expected);
  });
});

const buildDefaultTopicMessageRow = () => {
  return {
    consensusTimestamp: '1234567890000000001',
    topicId: 4,
    message: Buffer.from([0x6d, 0x65, 0x73, 0x73, 0x61, 0x67, 0x65]), //message
    payerAccountId: 3,
    runningHash: Buffer.from([0x68, 0x61, 0x73, 0x68]),
    runningHashVersion: 1,
    sequenceNumber: 1,
  };
};

const buildDefaultTopicMessageViewModel = () => {
  return {
    consensus_timestamp: '1234567890.000000001',
    topic_id: '0.0.4',
    message: 'bWVzc2FnZQ==', //message in base64
    payer_account_id: '0.0.3',
    running_hash: 'aGFzaA==', //hash in base64
    running_hash_version: 1,
    sequence_number: 1,
  };
};
