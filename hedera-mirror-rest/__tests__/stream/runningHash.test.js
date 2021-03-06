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

const SHA_384 = require('../../stream/hashObject').SHA_384;
const {calculateRunningHash} = require('../../stream/runningHash');

test('calculateRunningHash', () => {
  const header = Buffer.from([0xde, 0xad, 0xbe, 0xef]);
  const runningHashObject = {
    header,
    hash: Buffer.from(new Array(48).fill(0xab)),
  };
  const nextHashObject = {
    header,
    hash: Buffer.from(new Array(48).fill(0xef)),
  };
  const expected = Buffer.from([
    0x98,
    0x88,
    0x17,
    0xb7,
    0xa1,
    0x83,
    0xb1,
    0x63,
    0xbc,
    0x49,
    0x06,
    0x1e,
    0xef,
    0xca,
    0x16,
    0x3e,
    0xb3,
    0xc5,
    0x46,
    0x57,
    0xd7,
    0xd0,
    0x7f,
    0xba,
    0xf9,
    0xc9,
    0xfb,
    0x4a,
    0x69,
    0xf5,
    0xf4,
    0x1c,
    0x8b,
    0x3a,
    0x3c,
    0x1d,
    0xf8,
    0x9d,
    0xc6,
    0x40,
    0x23,
    0xd1,
    0xc8,
    0xf1,
    0xb5,
    0x72,
    0xe4,
    0x30,
  ]);

  expect(calculateRunningHash(runningHashObject, nextHashObject, SHA_384.name)).toEqual(expected);
});
