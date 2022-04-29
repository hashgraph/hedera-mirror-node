/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

'use strict';

const BlockViewModel = require('../../viewmodel/blockViewModel');
const RecordFile = require('../../model/recordFile');

describe('BlockViewModel', () => {
  const defaultRecordFile = new RecordFile({
    index: 16,
    count: 3,
    hapi_version_major: '0',
    hapi_version_minor: '22',
    hapi_version_patch: '3',
    name: '2022-04-27T12_09_24.499938763Z.rcd',
    prev_hash: '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    consensus_start: 1676540001234390000,
    consensus_end: 1676540001234490000,
    hash: 'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
    bytes: '10101100',
  });
  const defaultExpected = {
    count: 3,
    hapi_version: '0.22.3',
    hash: '0xfbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
    name: '2022-04-27T12_09_24.499938763Z.rcd',
    number: 16,
    previous_hash: '0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    size: 8,
    timestamp: {
      from: '1676540001.234390000',
      to: '1676540001.234490000',
    },
    gas_used: 50000000,
    logs_bloom: '0x549358c4c2e573e02410ef7b5a5ffa5f36dd7398',
  };

  test('default', () => {
    expect(new BlockViewModel(defaultRecordFile)).toEqual(defaultExpected);
  });
});
