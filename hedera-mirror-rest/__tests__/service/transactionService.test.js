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

const _ = require('lodash');

const TransactionId = require('../../transactionId');
const {TransactionService} = require('../../service');
const {TransactionResult, TransactionType} = require('../../model');

// add logger configuration support
require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

const contractCallType = TransactionType.getProtoId('CONTRACTCALL');
const contractCreateType = TransactionType.getProtoId('CONTRACTCREATEINSTANCE');
const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
const successTransactionResult = TransactionResult.getProtoId('SUCCESS');

jest.setTimeout(40000);

let dbConfig;

// set timeout for beforeAll to 4 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  dbConfig = await integrationDbOps.instantiateDatabase();
  await integrationDomainOps.setUp({}, dbConfig.sqlConnection);
  global.pool = dbConfig.sqlConnection;
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection(dbConfig);
});

beforeEach(async () => {
  if (!dbConfig.sqlConnection) {
    logger.warn(`sqlConnection undefined, acquire new connection`);
    dbConfig.sqlConnection = integrationDbOps.getConnection(dbConfig.dbSessionConfig);
  }

  await integrationDbOps.cleanUp(dbConfig.sqlConnection);
});

describe('TransactionService.getTransactionDetailsFromTimestamp tests', () => {
  test('No match', async () => {
    await expect(TransactionService.getTransactionDetailsFromTimestamp('1')).resolves.toBeNull();
  });

  const inputTransaction = [
    {
      consensus_timestamp: 2,
      payerAccountId: '5',
      valid_start_timestamp: 1,
    },
  ];

  const expectedTransaction = {
    payerAccountId: '5',
  };

  test('Row match', async () => {
    await integrationDomainOps.loadTransactions(inputTransaction);

    await expect(TransactionService.getTransactionDetailsFromTimestamp('2')).resolves.toMatchObject(
      expectedTransaction
    );
  });
});

describe('TransactionService.getTransactionDetailsFromTransactionIdAndNonce tests', () => {
  const duplicateValidStartNs = 10;
  const inputTransactions = [
    {consensus_timestamp: 2, payerAccountId: '5'}, // crypto transfer, success
    {consensus_timestamp: 6, payerAccountId: '5', type: contractCreateType}, // success
    {
      consensus_timestamp: 8,
      payerAccountId: '5',
      type: contractCallType,
      result: duplicateTransactionResult, // duplicate of the previous tx, though this is of different tx type
      valid_start_timestamp: 5,
    },
    {
      consensus_timestamp: 11,
      payerAccountId: '5',
      type: contractCallType,
      valid_start_timestamp: duplicateValidStartNs, // success
    },
    {
      consensus_timestamp: 13,
      payerAccountId: '5',
      type: contractCallType,
      nonce: 1,
      valid_start_timestamp: duplicateValidStartNs, // success, child
    },
    {
      consensus_timestamp: 15,
      payerAccountId: '5',
      type: contractCallType,
      result: duplicateTransactionResult,
      valid_start_timestamp: duplicateValidStartNs, // same valid start so duplicate tx id with the 4th tx
    },
  ];

  // pick the fields of interests, otherwise expect will fail since the Transaction object has other fields
  const pickTransactionFields = (transactions) => {
    return transactions.map((t) => _.pick(t, ['consensusTimestamp', 'payerAccountId']));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadTransactions(inputTransactions);
  });

  test('No match', async () => {
    await expect(
      TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
        TransactionId.fromString('0.0.1010-1234567890-123456789')
      )
    ).resolves.toHaveLength(0);
  });

  test('Single row match', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString('0.0.5-0-1')
    );
    expect(pickTransactionFields(actual)).toEqual([{consensusTimestamp: '2', payerAccountId: '5'}]);
  });

  test('Single row match nonce=1', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      1
    );
    expect(pickTransactionFields(actual)).toEqual([{consensusTimestamp: '13', payerAccountId: '5'}]);
  });

  test('Multiple rows match with nonce', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString('0.0.5-0-5'),
      0
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: '6', payerAccountId: '5'},
      {consensusTimestamp: '8', payerAccountId: '5'},
    ]);
  });

  test('Multiple rows match without nonce', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`)
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: '11', payerAccountId: '5'},
      {consensusTimestamp: '13', payerAccountId: '5'},
      {consensusTimestamp: '15', payerAccountId: '5'},
    ]);
  });

  test('Tow rows match without nonce exclude duplicate transaction', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      undefined,
      duplicateTransactionResult
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: '11', payerAccountId: '5'},
      {consensusTimestamp: '13', payerAccountId: '5'},
    ]);
  });

  test('Single row match with nonce exclude duplicate transaction', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      0,
      duplicateTransactionResult
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([{consensusTimestamp: '11', payerAccountId: '5'}]);
  });

  test('No match without nonce exclude all possible transaction results', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      undefined,
      [duplicateTransactionResult, successTransactionResult]
    );
    expect(actual).toHaveLength(0);
  });
});
