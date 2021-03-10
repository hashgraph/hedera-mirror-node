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

const {INT_SIZE, LONG_SIZE} = require('../../stream/constants');
const StreamObject = require('../../stream/streamObject');

describe('StreamObject', () => {
  let buffer;
  const classId = BigInt.asIntN(64, BigInt('0xdeadbeaf12345678'));
  const classVersion = 0x12345678;
  const header = Buffer.concat([
    Buffer.from([0x78, 0x56, 0x34, 0x12, 0xaf, 0xbe, 0xad, 0xde]),
    Buffer.from([0x78, 0x56, 0x34, 0x12]),
  ]);

  beforeEach(() => {
    buffer = Buffer.alloc(LONG_SIZE + INT_SIZE);
    buffer.writeBigInt64BE(classId);
    buffer.writeInt32BE(classVersion, LONG_SIZE);
  });

  test('getLength', () => {
    const streamObject = new StreamObject(buffer);
    expect(streamObject.getHeaderLength()).toEqual(LONG_SIZE + INT_SIZE);
  });

  test('getLength', () => {
    const streamObject = new StreamObject(buffer);
    expect(streamObject.getLength()).toEqual(LONG_SIZE + INT_SIZE);
  });

  test('getHeaderLE', () => {
    const streamObject = new StreamObject(buffer);
    expect(streamObject.getHeaderLE()).toEqual(header);
  });

  test('classId', () => {
    const streamObject = new StreamObject(buffer);
    expect(streamObject.classId).toEqual(classId);
  });

  test('classVersion', () => {
    const streamObject = new StreamObject(buffer);
    expect(streamObject.classVersion).toEqual(classVersion);
  });

  test('create from empty buffer', () => {
    expect(() => new StreamObject(Buffer.from([]))).toThrowErrorMatchingSnapshot();
  });

  test('create from truncated buffer', () => {
    expect(() => new StreamObject(Buffer.from([1, 2, 3, 4, 5]))).toThrowErrorMatchingSnapshot();
  });
});
