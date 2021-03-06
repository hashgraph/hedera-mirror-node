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

const RecordStreamObject = require('../../stream/recordStreamObject');

describe('RecordStreamObject', () => {
  const classId = BigInt.asIntN(64, BigInt('0xe370929ba5429d8b'));
  const classVersion = 1;
  const recordLength = 16;
  const recordBytes = new Array(recordLength).fill(0xa);
  const transactionLength = 32;
  const transactionBytes = new Array(transactionLength).fill(0xc);

  const buffer = Buffer.from(
    [].concat(
      [0xe3, 0x70, 0x92, 0x9b, 0xa5, 0x42, 0x9d, 0x8b], // classId
      [0, 0, 0, 1], // classVersion
      [0, 0, 0, recordLength],
      recordBytes,
      [0, 0, 0, transactionLength],
      transactionBytes
    )
  );

  test('getLength', () => {
    const expected = buffer.length;
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.getLength()).toEqual(expected);
  });

  test('classId', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.classId).toEqual(classId);
  });

  test('classVersion', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.classVersion).toEqual(classVersion);
  });

  test('recordBytes', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.record).toEqual(Buffer.from(recordBytes));
  });

  test('transactionBytes', () => {
    const recordStreamObject = new RecordStreamObject(buffer);
    expect(recordStreamObject.transaction).toEqual(Buffer.from(transactionBytes));
  });

  test('truncated buffer', () => {
    expect(() => new RecordStreamObject(buffer.slice(0, buffer.length - 4))).toThrowErrorMatchingSnapshot();
  });
});
