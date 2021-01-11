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

const {SignatureFile} = require('../signatureFile');
const {base64StringToBuffer, readJSONFile} = require('../utils');

const stateProofJson = readJSONFile('stateProofSample.json');

test('signatureFile test', () => {
  const sigFilesString = base64StringToBuffer(stateProofJson['signature_files']['0.0.3']);
  const signatureFileDomain = new SignatureFile(sigFilesString, '0.0.3');
  expect(signatureFileDomain.hash).toBeDefined();
  expect(signatureFileDomain.signature).toBeDefined();
});
