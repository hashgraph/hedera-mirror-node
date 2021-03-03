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

const {SignatureFile} = require('../../stream/signatureFile');
const {base64StringToBuffer} = require('../utils');
const {loadStateProofSamples} = require('./testUtils');

describe('signatureFile test', () => {
  loadStateProofSamples().forEach((sample) => {
    test(`parse signature file in ${sample.filepath}`, () => {
      const buffer = base64StringToBuffer(sample.data.signature_files['0.0.3']);
      const signatureFileDomain = new SignatureFile(buffer, '0.0.3');

      expect(signatureFileDomain.fileHash).toBeDefined();
      expect(signatureFileDomain.fileHashSignature).toBeDefined();
      expect(signatureFileDomain.version).toEqual(sample.version);

      if (sample.version === 5) {
        expect(signatureFileDomain.metadataHash).toBeDefined();
        expect(signatureFileDomain.metadataHashSignature).toBeDefined();
      }
    });
  });
});
