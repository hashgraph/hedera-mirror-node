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
'uses strict';

// parse signature file, retrieve hash and node id

// external libraries
class signatureFile {
  constructor(signatureFileString, nodeid) {
    this.setHashAndSignature(signatureFileString);
    this.nodeId = nodeid;
  }

  // 1. Extract the Hash of the content of corresponding RecordStream file. This Hash is the signed Content of this signature
  //  2. Extract signature from the file.
  setHashAndSignature(signatureFileBuffer) {
    // see Utility.extractHashAndSigFromFile
    // const signatureFileBuffer = Buffer.from(signatureFileString.toString('hex'))
    const fileHashSize = 48; // number of bytes
    let index = 0;
    // console.log(`* setHashAndSignature, signatureFileBuffer.length: ${signatureFileBuffer.length}, parse buffer: ${signatureFileBuffer.toString('hex')}`);
    while (index < signatureFileBuffer.length - 1) {
      if (index < 0) {
        // reached end
        break;
      }

      const typeDelimiter = signatureFileBuffer[index++];
      // console.log(`** setHashAndSignature, index: ${index}, typeDelimiter: ${typeDelimiter}`);

      switch (typeDelimiter) {
        case 4:
          // hash
          this.hash = signatureFileBuffer.subarray(index, index + fileHashSize);
          index = index + fileHashSize;
          // console.log(`** setHashAndSignature, read hash index: ${index}, hash: ${this.hash}`);
          break;
        case 3:
          // signature
          const signatureLength = signatureFileBuffer.readInt32BE(index);
          index = index + 4;
          // console.log(`** setHashAndSignature, read signature index: ${index}, signatureLength: ${signatureLength}`);
          this.signature = signatureFileBuffer.subarray(index, index + signatureLength);
          index = index + signatureLength;
          // console.log(`** setHashAndSignature, read signature index: ${index}, signature: ${this.signature}`);
          break;
        default:
          throw new Error(`Unexpected type delimiter '${typeDelimiter}' in signature file at index '${index - 1}'`);
      }
    }

    // console.log(`*** setHashAndSignature, parse complete index: ${index}, hash: ${this.hash}, signature: ${this.signature}`);
  }
}

module.exports = {
  signatureFile,
};
