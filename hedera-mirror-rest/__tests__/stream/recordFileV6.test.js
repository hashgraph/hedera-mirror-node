/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import RecordFileV6 from '../../stream/recordFileV6';
import testUtils from './testUtils';

describe('unsupported record file version', () => {
  testUtils.testRecordFileUnsupportedVersion([1, 2, 3, 4, 5], RecordFileV6);
});

describe('canCompact', () => {
  const testSpecs = [
    [1, false],
    [2, false],
    [3, false],
    [4, false],
    [5, false],
    [6, true],
  ];

  testUtils.testRecordFileCanCompact(testSpecs, RecordFileV6);
});

describe('from v6 buffer or compact object', () => {
  testUtils.testRecordFileFromBufferOrObj(6, RecordFileV6, true, true);
});
