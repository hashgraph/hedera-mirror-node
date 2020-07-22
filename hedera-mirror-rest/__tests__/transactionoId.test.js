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

const EntityId = require('../entityId');
const TransactionId = require('../transactionId');
const log4js = require('log4js');

// create a minimal global logger for transactionId to log errors.
global.logger = log4js.getLogger();

describe('TransactionId from invalid transaction ID string', () => {
  const invalidTransactionIdStrs = [
    'bad-string',
    'bad.entity.id-badseconds-badnanos',
    '0.0.1',
    '0.0.1-2-3',
    '0.0.1-1234567891-0',
    '0.0.1-1-999999999'
  ];

  invalidTransactionIdStrs.forEach(invalidTransactionIDStr => {
    expect(() => {
      TransactionId.fromString(invalidTransactionIDStr);
    }).toThrow();
  })
});

describe('TransactionId toString', () => {
  const transactionIdStrs = [
    '0.0.1-1234567891-000000000',
    '32767.65535.4294967295-1234567891-999999999'
  ];

  transactionIdStrs.forEach(transactionIdStr => {
    test(transactionIdStr, () => {
      expect(TransactionId.fromString(transactionIdStr).toString()).toEqual(transactionIdStr);
    })
  })
});

describe('TransactionId getEntityId', () => {
  const testSpecs = [
    {
      transactionIdStr: '0.0.1-1234567891-000000000',
      entityId: EntityId.fromString('0.0.1')
    },
    {
      transactionIdStr: '32767.65535.4294967295-1234567891-999999999',
      entityId: EntityId.fromString('32767.65535.4294967295')
    }
  ];

  testSpecs.forEach(testSpec => {
    test(testSpec.transactionIdStr, () => {
      expect(TransactionId.fromString(testSpec.transactionIdStr).getEntityId()).toEqual(testSpec.entityId);
    })
  })
});

describe('TransactionId getValidStartNs', () => {
  const testSpecs = [
    {
      transactionIdStr: '0.0.1-1234567891-000000000',
      validStartNs: '1234567891000000000'
    },
    {
      transactionIdStr: '0.0.1-9999999999-999999999',
      validStartNs: '9999999999999999999'
    },
  ];

  testSpecs.forEach(testSpec => {
    test(testSpec.transactionIdStr, () => {
      expect(TransactionId.fromString(testSpec.transactionIdStr).getValidStartNs()).toEqual(testSpec.validStartNs);
    })
  });
});
