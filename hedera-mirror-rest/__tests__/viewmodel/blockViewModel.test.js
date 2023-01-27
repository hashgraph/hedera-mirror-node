/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {BlockViewModel} from '../../viewmodel';
import {RecordFile} from '../../model';

describe('BlockViewModel', () => {
  const defaultRecordFile = new RecordFile({
    index: 16,
    count: 3,
    hapi_version_major: 0,
    hapi_version_minor: 22,
    hapi_version_patch: 3,
    name: '2022-04-27T12_09_24.499938763Z.rcd',
    prev_hash: '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    consensus_start: '1676540001234390000',
    consensus_end: '1676540001234490000',
    hash: 'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
    gas_used: 300000,
    logs_bloom: Buffer.from(
      '00000020002000001000000000000000000000000000000000000000000010000000000004000000000000000000000000108000000000000000000080000000000004000000000000000000000000880000000000000000000101000000000000000000000000000000000000008000000000000400000080000000000001000000000000000000000000000000000000000000002000000000100000100000200000040000100000001000000000000000000000000000000001001000004000000000000000000001000000000000000000100000000000100000000000000000000000000000000000000000000000080000100800000000000000120080',
      'hex'
    ),
    size: 4,
  });
  const defaultExpected = {
    count: 3,
    gas_used: 300000,
    hapi_version: '0.22.3',
    hash: '0xfbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
    logs_bloom:
      '0x00000020002000001000000000000000000000000000000000000000000010000000000004000000000000000000000000108000000000000000000080000000000004000000000000000000000000880000000000000000000101000000000000000000000000000000000000008000000000000400000080000000000001000000000000000000000000000000000000000000002000000000100000100000200000040000100000001000000000000000000000000000000001001000004000000000000000000001000000000000000000100000000000100000000000000000000000000000000000000000000000080000100800000000000000120080',
    name: '2022-04-27T12_09_24.499938763Z.rcd',
    number: 16,
    previous_hash: '0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    size: 4,
    timestamp: {
      from: '1676540001.234390000',
      to: '1676540001.234490000',
    },
  };

  test('default', () => {
    expect(new BlockViewModel(defaultRecordFile)).toEqual(defaultExpected);
  });

  test('nullable logs_bloom', () => {
    expect(new BlockViewModel(new RecordFile({logs_bloom: null})).logs_bloom).toStrictEqual(null);
  });

  test('empty logs_bloom', () => {
    expect(new BlockViewModel(new RecordFile({logs_bloom: Buffer.alloc(0)})).logs_bloom).toStrictEqual('0x');
  });

  test('logs_bloom filled with zeros', () => {
    expect(new BlockViewModel(new RecordFile({logs_bloom: Buffer.alloc(256)})).logs_bloom).toStrictEqual(
      `0x${'00'.repeat(256)}`
    );
  });

  test('gas_used = -1', () => {
    expect(new BlockViewModel(new RecordFile({gas_used: -1})).logs_bloom).toStrictEqual(null);
  });

  test('null size', () => {
    expect(new BlockViewModel(new RecordFile({size: null})).size).toEqual(null);
  });
});
