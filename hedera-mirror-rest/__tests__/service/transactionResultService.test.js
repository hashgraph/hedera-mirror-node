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

const integrationDbOps = require('../integrationDbOps');
const {InvalidArgumentError} = require('../../errors/invalidArgumentError');
const TransactionResultService = require('../../service/transactionResultService');

jest.setTimeout(40000);

let sqlConnection;

// set timeout for beforeAll to 2 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  sqlConnection = await integrationDbOps.instantiateDatabase();

  // set items that required db connection but weren't available due to integration db setup logic
  await TransactionResultService.loadTransactionResults();
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection();
});

beforeEach(async () => {
  if (!sqlConnection) {
    logger.warn(`sqlConnection undefined, acquire new connection`);
    sqlConnection = await integrationDbOps.instantiateDatabase();
  }

  await integrationDbOps.cleanUp();
});

describe('DB integration test - TransactionResultService.getProtoId', () => {
  test('DB integration test -  transactionTypes.getProtoId - Verify valid transaction type returns value', () => {
    expect(TransactionResultService.getProtoId('SUCCESS')).toBe(22);
    expect(TransactionResultService.getProtoId('success')).toBe(22);
    expect(TransactionResultService.getProtoId('NOT_SUPPORTED')).toBe(13);
    expect(TransactionResultService.getProtoId('not_supported')).toBe(13);
  });
  test('DB integration test -  transactionTypes.getId - Verify invalid transaction type throws error', () => {
    expect(() => TransactionResultService.getProtoId('TEST')).toThrowError(InvalidArgumentError);
    expect(() => TransactionResultService.getProtoId(1)).toThrowError(InvalidArgumentError);
  });
});

describe('DB integration test - TransactionResultService.getName', () => {
  test('DB integration test -  transactionTypes.getName - Verify valid transaction type returns value', () => {
    expect(TransactionResultService.getResult(22)).toBe('SUCCESS');
    expect(TransactionResultService.getResult(13)).toBe('NOT_SUPPORTED');
  });
  test('DB integration test -  transactionTypes.getId - Verify invalid transaction type throws error', () => {
    expect(() => TransactionResultService.getResult('TEST')).toThrowError(InvalidArgumentError);
    expect(() => TransactionResultService.getResult(-1)).toThrowError(InvalidArgumentError);
  });
});
