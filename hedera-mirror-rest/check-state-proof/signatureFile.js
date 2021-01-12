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
'uses strict';

class SignatureFile {
  /**
   * parse signature file buffer, retrieve hash and node id
   */
  constructor(signatureFile, nodeid) {
    this.parseSignatureFileBuffer(signatureFile);
    this.nodeId = nodeid;
  }

  // Extract the Hash and signature from the file.
  parseSignatureFileBuffer(signatureFileBuffer) {
    const fileHashSize = 48; // number of bytes
    let index = 0;
    while (index < signatureFileBuffer.length) {
      const typeDelimiter = signatureFileBuffer[index++];

      switch (typeDelimiter) {
        case 4:
          // hash
          this.hash = signatureFileBuffer.subarray(index, index + fileHashSize);
          index += fileHashSize;
          break;
        case 3:
          // signature
          const signatureLength = signatureFileBuffer.readInt32BE(index);
          index += 4;
          this.signature = signatureFileBuffer.subarray(index, index + signatureLength);
          index += signatureLength;
          break;
        default:
          throw new Error(`Unexpected type delimiter '${typeDelimiter}' in signature file at index '${index - 1}'`);
      }
    }
  }
}

module.exports = {
  SignatureFile,
};
