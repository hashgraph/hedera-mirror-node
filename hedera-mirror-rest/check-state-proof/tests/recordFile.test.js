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

const {RecordFile} = require('../../stream/recordFile');
const {base64StringToBuffer} = require('../utils');
const {loadStateProofSamples} = require('./testUtils');

describe('recordFile parsetest', () => {
  loadStateProofSamples().forEach((sample) => {
    test(`parse recordfile in ${sample.filepath}`, () => {
      const buffer = base64StringToBuffer(sample.data.record_file);
      const recordFileDomain = new RecordFile(buffer);

      expect(recordFileDomain.fileHash).toBeDefined();
      expect(recordFileDomain.version).toEqual(sample.version);
      if (sample.version === 5) {
        expect(recordFileDomain.metadataHash).toBeDefined();
      }
      expect(Object.keys(recordFileDomain.transactionIdMap).length).toBeGreaterThan(0);
    });
  });
});
