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

const long = require('long');
const {proto} = require('@hashgraph/proto/lib/proto');
const utils = require('../../stream/utils');

describe('utils protoTransactionIdToString', () => {
  const accountID = {
    shardNum: 0,
    realmNum: 0,
    accountNum: 1010,
  };

  const testSpecs = [
    {
      transactionId: proto.TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: 193823,
          nanos: 0,
        },
      }),
      expected: '0.0.1010-193823-0',
    },
    {
      transactionId: proto.TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: 193823,
          nanos: 999999999,
        },
      }),
      expected: '0.0.1010-193823-999999999',
    },
    {
      transactionId: proto.TransactionID.create({
        accountID,
        transactionValidStart: {
          seconds: long.MAX_VALUE,
          nanos: 999999999,
        },
      }),
      expected: `0.0.1010-${long.MAX_VALUE.toString()}-999999999`,
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(`expect output ${testSpec.expected}`, () => {
      expect(utils.protoTransactionIdToString(testSpec.transactionId)).toEqual(testSpec.expected);
    });
  });
});
