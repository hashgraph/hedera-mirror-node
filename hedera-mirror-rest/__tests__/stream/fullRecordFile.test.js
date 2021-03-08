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
const {testRecordFiles, commonRecordFileTests, copyRecordFileAndSetVersion} = require('./testUtils');

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
  commonRecordFileTests(2, FullRecordFile);

  test('from non-Buffer obj', () => {
    expect(() => new FullRecordFile({})).toThrowErrorMatchingSnapshot();
  });
});
