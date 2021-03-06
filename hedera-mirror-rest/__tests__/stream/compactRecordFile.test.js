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

const CompactRecordFile = require('../../stream/compactRecordFile');
const {testRecordFiles, copyRecordFileAndSetVersion} = require('./testUtils');

describe('unsupported record file version ', () => {
  const bufferV2 = testRecordFiles.v2[0].buffer;
  // unsupported version numbers, 1, 2, 3, 4, and 6 (future version)
  const testSpecs = [
    [2, bufferV2],
    ...[1, 3, 4, 6].map((version) => [version, copyRecordFileAndSetVersion(bufferV2, version)]),
  ];

  testSpecs.forEach((testSpec) => {
    const [version, buffer] = testSpec;
    test(`create object from version ${version}`, () => {
      expect(() => new CompactRecordFile(buffer)).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('canCompact', () => {
  test('true', () => {
    expect(CompactRecordFile.canCompact(testRecordFiles.v5[0].buffer)).toBeTruthy();
  });

  describe('false', () => {
    const bufferV2 = testRecordFiles.v2[0].buffer;
    // unsupported version numbers, 1, 2, 3, 4, and 6 (future version)
    const testSpecs = [
      [2, bufferV2],
      ...[1, 3, 4, 6].map((version) => [version, copyRecordFileAndSetVersion(bufferV2, version)]),
    ];

    testSpecs.forEach((testSpec) => {
      const [version, buffer] = testSpec;
      test(`version ${version}`, () => {
        expect(CompactRecordFile.canCompact(buffer)).toBeFalsy();
      });
    });
  });
});

describe('from v5 buffer or compact object', () => {
  describe('check individual field', () => {
    testRecordFiles.v5.forEach((testSpec) => {
      const {buffer, obj, checks} = testSpec;
      const bufferOrObj = buffer || obj;
      const name = `from v5 ${buffer ? 'buffer' : 'compact object'}`;

      checks.forEach((check) => {
        test(`${name} - ${check.func} - ${JSON.stringify(check.args)}`, () => {
          const compactRecordFile = new CompactRecordFile(bufferOrObj);
          const fn = compactRecordFile[check.func];
          if (!check.expectErr) {
            const actual = fn.apply(compactRecordFile, check.args);
            expect(actual).toEqual(check.expected);
          } else {
            expect(() => fn.apply(compactRecordFile, check.args)).toThrowErrorMatchingSnapshot();
          }
        });
      });
    });
  });

  test('v5 buffer with extra data', () => {
    const buffer = Buffer.concat([testRecordFiles.v5[0].buffer, Buffer.from([0])]);
    expect(() => new CompactRecordFile(buffer)).toThrowErrorMatchingSnapshot();
  });

  test('truncated v5 buffer', () => {
    const v5Buffer = testRecordFiles.v5[0].buffer;
    expect(() => new CompactRecordFile(v5Buffer.slice(0, v5Buffer.length - 1))).toThrowErrorMatchingSnapshot();
  });

  test('end running hash mismatch', () => {
    // make a shallow copy, change the last byte of the end running hash object
    const v5Obj = {...testRecordFiles.v5[1].obj};
    const badEndRunningHashObject = Buffer.from(v5Obj.endRunningHashObject);
    const lastIndex = badEndRunningHashObject.length - 1;
    badEndRunningHashObject[lastIndex] = badEndRunningHashObject[lastIndex] ^ 0xff;
    v5Obj.endRunningHashObject = badEndRunningHashObject;

    expect(() => new CompactRecordFile(v5Obj)).toThrowErrorMatchingSnapshot();
  });
});
