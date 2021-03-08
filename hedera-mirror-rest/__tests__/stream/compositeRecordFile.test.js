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

const {CompositeRecordFile} = require('../../stream');
const {testRecordFiles, commonRecordFileTests, copyRecordFileAndSetVersion} = require('./testUtils');

describe('unsupported record file version', () => {
  const bufferV2 = testRecordFiles.v2[0].buffer;
  // unsupported version numbers 3, 4, and 6 (future version)
  const testSpecs = [...[3, 4, 6].map((version) => [version, copyRecordFileAndSetVersion(bufferV2, version)])];

  testSpecs.forEach((testSpec) => {
    const [version, buffer] = testSpec;
    test(`create object from version ${version}`, () => {
      expect(() => new CompactRecordFile(buffer)).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('canCompact', () => {
  const bufferV2 = testRecordFiles.v2[0].buffer;
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, true],
    [6, false],
  ].map(([version, expected]) => {
    return {
      version,
      buffer: copyRecordFileAndSetVersion(bufferV2, version),
      expected,
    };
  });

  testSpecs.forEach((testSpec) => {
    const {version, buffer, expected} = testSpec;
    test(`version ${version} - ${expected ? 'can compact' : 'cannot compact'}`, () => {
      expect(CompositeRecordFile.canCompact(buffer)).toEqual(expected);
    });
  });
});

describe('from record file buffer or compact object', () => {
  commonRecordFileTests(2, CompositeRecordFile, false);
  commonRecordFileTests(5, CompositeRecordFile, true);
});
