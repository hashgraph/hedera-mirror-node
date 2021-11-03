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

const log4js = require('log4js');
const EntityId = require('../entityId');
const TransactionId = require('../transactionId');

// create a minimal global logger for transactionId to log errors.
global.logger = log4js.getLogger();

describe('TransactionId from invalid transaction ID string', () => {
  const invalidTransactionIdStrs = [
    'bad-string',
    'bad.entity.id-badseconds-badnanos',
    '0.0.1',
    '0.0.1-1234567891-0000000000',
    '0.0.1-1234567891-000000000 ',
    ' 0.0.1-1234567891-000000000',
    '0.0.1- 1234567891-000000000',
    '0.0.1-1234567891- 000000000',
    '0.0.1-9223372036854775808-0',
    '0.0.1-9223372036854775809-0',
    '0.0.1--9--0',
  ];

  invalidTransactionIdStrs.forEach((invalidTransactionIdStr) => {
    test(`invalid transaction ID - ${invalidTransactionIdStr}`, () => {
      expect(() => {
        TransactionId.fromString(invalidTransactionIdStr);
      }).toThrow();
    });
  });
});

describe('TransactionId toString', () => {
  const testSpecs = [
    {
      input: '0.0.1-1234567891-000000000',
      expected: '0.0.1-1234567891-0',
    },
    {
      input: '0.0.1-1234567891-0',
    },
    {
      input: '0.0.1-0-0',
    },
    {
      input: '0.0.1-01-0',
      expected: '0.0.1-1-0',
    },
    {
      input: '32767.65535.4294967295-9223372036854775807-999999999',
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.input, () => {
      const {input} = testSpec;
      const expected = testSpec.expected ? testSpec.expected : input;
      expect(TransactionId.fromString(input).toString()).toEqual(expected);
    });
  });
});

describe('TransactionId getEntityId', () => {
  const testSpecs = [
    {
      transactionIdStr: '0.0.1-1234567891-000000000',
      entityId: EntityId.parse('0.0.1'),
    },
    {
      transactionIdStr: '32767.65535.4294967295-9223372036854775807-999999999',
      entityId: EntityId.parse('32767.65535.4294967295'),
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.transactionIdStr, () => {
      expect(TransactionId.fromString(testSpec.transactionIdStr).getEntityId()).toEqual(testSpec.entityId);
    });
  });
});

describe('TransactionId getValidStartNs', () => {
  const testSpecs = [
    {
      transactionIdStr: '0.0.1-1234567891-0',
      validStartNs: '1234567891000000000',
    },
    {
      transactionIdStr: '0.0.1-9223372036854775807-1',
      validStartNs: '9223372036854775807000000001',
    },
    {
      transactionIdStr: '0.0.1-9223372036854775807-999999999',
      validStartNs: '9223372036854775807999999999',
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.transactionIdStr, () => {
      expect(TransactionId.fromString(testSpec.transactionIdStr).getValidStartNs()).toEqual(testSpec.validStartNs);
    });
  });
});
