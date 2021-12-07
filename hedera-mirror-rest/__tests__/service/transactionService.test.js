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
const TransactionId = require('../../transactionId');
const {TransactionService} = require('../../service');

// add logger configuration support
require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

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

describe('TransactionService.getTransactionContractDetailsFromTimestamp tests', () => {
  test('TransactionService.getTransactionContractDetailsFromTimestamp - No match', async () => {
    await expect(TransactionService.getTransactionContractDetailsFromTimestamp(1)).resolves.toBeNull();
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

  test('TransactionService.getTransactionContractDetailsFromTimestamp - Row match', async () => {
    await integrationDomainOps.loadTransactions(inputTransaction);

    await expect(TransactionService.getTransactionContractDetailsFromTimestamp(2)).resolves.toMatchObject(
      expectedTransaction
    );
  });
});

describe('TransactionService.getTransactionContractDetailsFromTransactionId tests', () => {
  test('TransactionService.getTransactionContractDetailsFromTransactionId - No match', async () => {
    await expect(
      TransactionService.getTransactionContractDetailsFromTransactionId(
        TransactionId.fromString('0.0.1010-1234567890-123456789')
      )
    ).resolves.toBeNull();
  });

  const inputTransaction = [
    {
      consensus_timestamp: 2,
      payerAccountId: '5',
      valid_start_timestamp: 1,
    },
  ];

  const expectedTransaction = {
    consensusTimestamp: '2',
    payerAccountId: '5',
  };

  test('TransactionService.getTransactionContractDetailsFromTransactionId - Row match', async () => {
    await integrationDomainOps.loadTransactions(inputTransaction);

    await expect(
      TransactionService.getTransactionContractDetailsFromTransactionId(
        TransactionId.fromString('0.0.5-0000000000-000000001')
      )
    ).resolves.toMatchObject(expectedTransaction);
  });
});
