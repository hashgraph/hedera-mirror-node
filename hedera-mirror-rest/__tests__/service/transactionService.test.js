/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';

import TransactionId from '../../transactionId';
import {TransactionService} from '../../service';
import {TransactionResult, TransactionType} from '../../model';

import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';

setupIntegrationTest();

const contractCallType = TransactionType.getProtoId('CONTRACTCALL');
const contractCreateType = TransactionType.getProtoId('CONTRACTCREATEINSTANCE');
const ethereumTxType = TransactionType.getProtoId('ETHEREUMTRANSACTION');
const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
const successTransactionResult = TransactionResult.getProtoId('SUCCESS');
const wrongNonceTransactionResult = TransactionResult.getProtoId('WRONG_NONCE');

describe('TransactionService.getTransactionDetailsFromTransactionId tests', () => {
  const duplicateValidStartNs = 10;
  const inputTransactions = [
    {consensus_timestamp: 2, payerAccountId: 5}, // crypto transfer, success
    {consensus_timestamp: 6, payerAccountId: 5, type: contractCreateType}, // success
    {
      consensus_timestamp: 8,
      payerAccountId: 5,
      type: contractCallType,
      result: duplicateTransactionResult, // duplicate of the previous tx, though this is of different tx type
      valid_start_timestamp: 5,
    },
    {
      consensus_timestamp: 11,
      payerAccountId: 5,
      type: contractCallType,
      valid_start_timestamp: duplicateValidStartNs, // success
    },
    {
      consensus_timestamp: 13,
      payerAccountId: 5,
      type: contractCallType,
      nonce: 1,
      valid_start_timestamp: duplicateValidStartNs, // success, child
    },
    {
      consensus_timestamp: 15,
      payerAccountId: 5,
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
      TransactionService.getTransactionDetailsFromTransactionId(
        TransactionId.fromString('0.0.1010-1234567890-123456789')
      )
    ).resolves.toHaveLength(0);
  });

  test('Single row match', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString('0.0.5-0-1')
    );
    expect(pickTransactionFields(actual)).toEqual([{consensusTimestamp: 2}]);
  });

  test('Single row match nonce=1', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      1
    );
    expect(pickTransactionFields(actual)).toEqual([{consensusTimestamp: 13}]);
  });

  test('Multiple rows match with nonce', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString('0.0.5-0-5'),
      0
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([{consensusTimestamp: 6}, {consensusTimestamp: 8}]);
  });

  test('Multiple rows match without nonce', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`)
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([
      {consensusTimestamp: 11},
      {consensusTimestamp: 13},
      {consensusTimestamp: 15},
    ]);
  });

  test('Tow rows match without nonce exclude duplicate transaction', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      undefined,
      duplicateTransactionResult
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([{consensusTimestamp: 11}, {consensusTimestamp: 13}]);
  });

  test('Single row match with nonce exclude duplicate transaction', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      0,
      duplicateTransactionResult
    );
    expect(pickTransactionFields(actual)).toIncludeSameMembers([{consensusTimestamp: 11}]);
  });

  test('No match without nonce exclude all possible transaction results', async () => {
    const actual = await TransactionService.getTransactionDetailsFromTransactionId(
      TransactionId.fromString(`0.0.5-0-${duplicateValidStartNs}`),
      undefined,
      [duplicateTransactionResult, successTransactionResult]
    );
    expect(actual).toHaveLength(0);
  });
});

describe('TransactionService.getEthTransactionByHash tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';
  const ethereumTxHashBuffer = Buffer.from(ethereumTxHash, 'hex');

  const inputTransactions = [
    {
      consensus_timestamp: 1,
      payerAccountId: 10,
      type: ethereumTxType,
      result: successTransactionResult,
      valid_start_timestamp: 1,
    },
    {
      consensus_timestamp: 2,
      payerAccountId: 10,
      type: ethereumTxType,
      result: duplicateTransactionResult,
      valid_start_timestamp: 2,
    },
    {
      consensus_timestamp: 3,
      payerAccountId: 10,
      type: ethereumTxType,
      result: wrongNonceTransactionResult,
      valid_start_timestamp: 3,
    },
    {
      consensus_timestamp: 4,
      payerAccountId: 10,
      type: ethereumTxType,
      result: successTransactionResult,
      valid_start_timestamp: 4,
    },
    {
      consensus_timestamp: 5,
      payerAccountId: 10,
      type: contractCreateType,
    },
  ];

  const inputEthTransaction = [
    {
      consensus_timestamp: 1,
      hash: ethereumTxHash,
      payer_account_id: 10,
    },
    {
      consensus_timestamp: 2,
      hash: ethereumTxHash,
      payer_account_id: 10,
    },
    {
      consensus_timestamp: 3,
      hash: ethereumTxHash,
      payer_account_id: 10,
    },
    {
      consensus_timestamp: 4,
      hash: ethereumTxHash,
      payer_account_id: 10,
    },
  ];

  const expectedTransaction = {
    consensusTimestamp: 1,
    hash: ethereumTxHash,
  };

  // pick the fields of interests, otherwise expect will fail since the Transaction object has other fields
  const pickTransactionFields = (transactions) => {
    return transactions
      .map((tx) => _.pick(tx, ['consensusTimestamp', 'hash']))
      .map((tx) => ({...tx, hash: Buffer.from(tx.hash).toString('hex')}));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadTransactions(inputTransactions);
    await integrationDomainOps.loadEthereumTransactions(inputEthTransaction);
  });

  test('No match', async () => {
    await expect(
      TransactionService.getEthTransactionByHash(
        Buffer.from('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6393', 'hex')
      )
    ).resolves.toHaveLength(0);
  });

  test('Match all transactions by same hash', async () => {
    const ethTransactions = await TransactionService.getEthTransactionByHash(ethereumTxHashBuffer);
    expect(pickTransactionFields(ethTransactions)).toIncludeSameMembers([
      expectedTransaction,
      {consensusTimestamp: 2, hash: ethereumTxHash},
      {consensusTimestamp: 3, hash: ethereumTxHash},
      {consensusTimestamp: 4, hash: ethereumTxHash},
    ]);
  });

  test('Match all transactions with no duplicates and wrong nonces', async () => {
    const ethTransactions = await TransactionService.getEthTransactionByHash(ethereumTxHashBuffer, [
      duplicateTransactionResult,
      wrongNonceTransactionResult,
    ]);
    expect(pickTransactionFields(ethTransactions)).toIncludeSameMembers([
      expectedTransaction,
      {consensusTimestamp: 4, hash: ethereumTxHash},
    ]);
  });

  test('Match the oldest tx with no duplicates and wrong nonces', async () => {
    const ethTransactions = await TransactionService.getEthTransactionByHash(
      ethereumTxHashBuffer,
      [duplicateTransactionResult, wrongNonceTransactionResult],
      1
    );
    expect(pickTransactionFields(ethTransactions)).toIncludeSameMembers([expectedTransaction]);
  });
});

describe('TransactionService.getEthTransactionByTimestamp tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';

  const inputTransactions = [
    {
      consensus_timestamp: 1,
      payerAccountId: 10,
      type: ethereumTxType,
      result: successTransactionResult,
      valid_start_timestamp: 1,
    },
    {
      consensus_timestamp: 2,
      payerAccountId: 10,
      type: ethereumTxType,
      result: duplicateTransactionResult,
      valid_start_timestamp: 2,
    },
  ];

  const inputEthTransactions = [
    {
      consensus_timestamp: 1,
      hash: ethereumTxHash,
      payer_account_id: 10,
    },
    {
      consensus_timestamp: 2,
      hash: ethereumTxHash,
      payer_account_id: 10,
    },
  ];

  const expectedTransaction = {
    consensusTimestamp: 2,
    hash: ethereumTxHash,
  };

  const pickTransactionFields = (transactions) => {
    return transactions
      .map((tx) => _.pick(tx, ['consensusTimestamp', 'hash']))
      .map((tx) => ({...tx, hash: Buffer.from(tx.hash).toString('hex')}));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadTransactions(inputTransactions);
  });

  test('No match', async () => {
    await expect(TransactionService.getEthTransactionByTimestamp('1')).resolves.toHaveLength(0);
  });

  test('Finds a matching eth transaction', async () => {
    await integrationDomainOps.loadEthereumTransactions(inputEthTransactions);

    const ethTransactions = await TransactionService.getEthTransactionByTimestamp('2');

    expect(pickTransactionFields(ethTransactions)).toIncludeSameMembers([expectedTransaction]);
  });
});
