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
const TransactionTypeService = require('../../service/transactionTypeService');

jest.setTimeout(40000);

let sqlConnection;

// set timeout for beforeAll to 2 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  sqlConnection = await integrationDbOps.instantiateDatabase();

  // set items that required db connection but weren't available due to integration db setup logic
  await TransactionTypeService.loadTransactionTypes();
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

describe('DB integration test - TransactionTypeService.getProtoId', () => {
  test('DB integration test -  transactionTypes.getProtoId - Verify valid transaction type returns value', () => {
    expect(TransactionTypeService.getProtoId('CRYPTOTRANSFER')).toBe(14);
    expect(TransactionTypeService.getProtoId('cryptotransfer')).toBe(14);
    expect(TransactionTypeService.getProtoId('TOKENWIPE')).toBe(39);
    expect(TransactionTypeService.getProtoId('tokenWipe')).toBe(39);
  });
  test('DB integration test -  transactionTypes.getId - Verify invalid transaction type throws error', () => {
    expect(() => TransactionTypeService.getProtoId('TEST')).toThrowError(InvalidArgumentError);
    expect(() => TransactionTypeService.getProtoId(1)).toThrowError(InvalidArgumentError);
  });
});

describe('DB integration test - TransactionTypeService.getName', () => {
  test('DB integration test -  transactionTypes.getName - Verify valid transaction type returns value', () => {
    expect(TransactionTypeService.getName(14)).toBe('CRYPTOTRANSFER');
    expect(TransactionTypeService.getName(39)).toBe('TOKENWIPE');
  });
  test('DB integration test -  transactionTypes.getId - Verify invalid transaction type throws error', () => {
    expect(() => TransactionTypeService.getName('TEST')).toThrowError(InvalidArgumentError);
    expect(() => TransactionTypeService.getName(1)).toThrowError(InvalidArgumentError);
  });
});
