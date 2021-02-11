/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

'use strict';

const schedules = require('../schedules');

describe('schedule formatScheduleRow tests', () => {
  const defaultInput = {
    key: [3, 3, 3],
    consensus_timestamp: '1234567890000000001',
    creator_account_id: '100',
    executed_timestamp: '1234567890000000002',
    memo: 'Created per council decision dated 1/21/21',
    payer_account_id: '101',
    schedule_id: '102',
    signatures: [
      {
        consensus_timestamp: '1234567890000000001',
        public_key_prefix: [0xa1, 0xb1, 0xc1],
        signature: [0xa2, 0xb2, 0xc2],
      },
      {
        consensus_timestamp: '1234567890000000010',
        public_key_prefix: [0xd1, 0xe1, 0xf1],
        signature: [0xd2, 0xe2, 0xf2],
      },
    ],
    transaction_body: Buffer.from([0x29, 0xde, 0xad, 0xbe, 0xef]),
  };
  const defaultExpected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
    consensus_timestamp: '1234567890.000000001',
    creator_account_id: '0.0.100',
    executed_timestamp: '1234567890.000000002',
    memo: 'Created per council decision dated 1/21/21',
    payer_account_id: '0.0.101',
    schedule_id: '0.0.102',
    signatures: [
      {
        consensus_timestamp: '1234567890.000000001',
        public_key_prefix: 'a1b1c1',
        signature: 'a2b2c2',
      },
      {
        consensus_timestamp: '1234567890.000000010',
        public_key_prefix: 'd1e1f1',
        signature: 'd2e2f2',
      },
    ],
    transaction_body: 'Kd6tvu8=',
  };

  const testSpecs = [
    {
      description: 'input with all fields present',
      input: {...defaultInput},
      expected: {...defaultExpected},
    },
    {
      description: 'input with "executed_timestamp" nullified',
      input: {
        ...defaultInput,
        executed_timestamp: null,
      },
      expected: {
        ...defaultExpected,
        executed_timestamp: null,
      },
    },
  ];

  testSpecs.forEach((testSpec) => {
    test(testSpec.description, () => {
      expect(schedules.formatScheduleRow(testSpec.input)).toStrictEqual(testSpec.expected);
    });
  });
});
