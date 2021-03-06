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

const FullRecordFile = require('../../stream/fullRecordFile');
const {testRecordFiles, copyRecordFileAndSetVersion} = require('./testUtils');

describe('unsupported record file version ', () => {
  const bufferV5 = testRecordFiles.v5[0].buffer;
  // unsupported version numbers 3, 4, and 6 (future version)
  const testSpecs = [
    [5, bufferV5],
    ...[3, 4, 6].map((version) => [version, copyRecordFileAndSetVersion(bufferV5, version)]),
  ];

  testSpecs.forEach((testSpec) => {
    const [version, buffer] = testSpec;
    test(`create object from version ${version}`, () => {
      expect(() => new FullRecordFile(buffer)).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('canCompact always return false', () => {
  const bufferV2 = testRecordFiles.v2[0].buffer;
  // unsupported version numbers, 1, 2, 3, 4, 5, and 6 (future version)
  const testSpecs = [
    [2, bufferV2],
    ...[1, 3, 4, 5, 6].map((version) => [version, copyRecordFileAndSetVersion(bufferV2, version)]),
  ];

  testSpecs.forEach((testSpec) => {
    const [version, buffer] = testSpec;
    test(`version ${version}`, () => {
      expect(FullRecordFile.canCompact(buffer)).toBeFalsy();
    });
  });
});

describe('from v2 buffer', () => {
  describe('check individual field', () => {
    testRecordFiles.v2.forEach((testSpec) => {
      const {buffer, checks} = testSpec;

      checks.forEach((check) => {
        test(`${check.func} - ${JSON.stringify(check.args)}`, () => {
          const fullRecordFile = new FullRecordFile(buffer);
          const fn = fullRecordFile[check.func];
          if (!check.expectErr) {
            const actual = fn.apply(fullRecordFile, check.args);
            expect(actual).toEqual(check.expected);
          } else {
            expect(() => fn.apply(fullRecordFile, check.args)).toThrowErrorMatchingSnapshot();
          }
        });
      });
    });
  });

  test('v2 buffer with extra data', () => {
    const buffer = Buffer.concat([testRecordFiles.v2[0].buffer, Buffer.from([0])]);
    expect(() => new FullRecordFile(buffer)).toThrowErrorMatchingSnapshot();
  });

  test('truncated v2 buffer', () => {
    const v2Buffer = testRecordFiles.v2[0].buffer;
    expect(() => new FullRecordFile(v2Buffer.slice(0, v2Buffer.length - 1))).toThrowErrorMatchingSnapshot();
  });

  test('from non-Buffer obj', () => {
    expect(() => new FullRecordFile({})).toThrowErrorMatchingSnapshot();
  });
});
