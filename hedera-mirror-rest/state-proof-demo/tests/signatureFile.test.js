/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const {signatureFile} = require('../signatureFile');
const {base64StringToBuffer, readJSONFile} = require('../utils');

const stateProofJson = readJSONFile('stateProofSample.json');

test('signatureFile test', () => {
  // const signatureBase64 = "BEldxQ1jI9TVJj002+/nwgoGV6L6X+DYwBGgH0ondXolmYfa0hD7lZa8WozH+9u6MwMAAAGAW368fmoJsZme3vwzERrctK60l4EJscbU2Z06DjEaSaE1kkngE9PQdMs0roNfE/eniwVNzRbASlEfqZM/SccYiZSBkIzFOarfWDV5aP2F8+xwdJ0Idklw+ezH/Zyz6GEZsDxViQ6pMqVj1r40lcbVCyKbxUK2gKX/OsDOkyobTHtICRbiMKgcUESrfuG5EK1fskVOqLB27dHJX5AmjxGp9CAJfQD85heMxVNkHe73F2xwVSTA33NHw8YU59svk952Q1QyGb7iCGLjJSQDvdChZC/5Ikl7RLYwnCKYJHhVyRWeQ8aH+miKnnPGqkTjpKJNW+WXU6UI6r0r55VlwScqPLonjzgRy0x7b8fhBPPa9M+fq8aCOwacuAymD29uTY6/6NVwSFt8zhc53HPUZmt5vTpUtwOJhFaWk2KJ3buWQDmr7+sCSV7Lj5qCl2sQ3QmuIfDhRpNDsd3Xuvjk9K4f9SAKwAkyFhhbp79cRKC8g4ZIwejiGMTXW3dOnv0CcKTv";
  let sigFilesString = base64StringToBuffer(stateProofJson['signature_files']['0.0.3']);
  const signatureFileDomain = new signatureFile(sigFilesString, '0.0.3');
  expect(signatureFileDomain.hash).toBeDefined();
  expect(signatureFileDomain.signature).toBeDefined();
});
