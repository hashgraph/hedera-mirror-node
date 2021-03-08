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
const testUtils = require('./testUtils');

describe('unsupported record file version', () => {
  testUtils.testRecordFileUnsupportedVersion([1, 2, 3, 4, 6], CompactRecordFile);
});

describe('canCompact', () => {
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, true],
    [6, false],
  ];

  testUtils.testRecordFileCanCompact(testSpecs, CompactRecordFile);
});

describe('from v5 buffer or compact object', () => {
  testUtils.testRecordFileFromBufferOrObj(5, CompactRecordFile, true, true);
});
