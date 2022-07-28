/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import {BYTE_SIZE, INT_SIZE} from './constants';
import HashObject from './hashObject';
import SignatureObject from './signatureObject';
import {readLengthAndBytes, readNBytes} from './utils';

// version, object stream signature version
const V5_FILE_HASH_OFFSET = BYTE_SIZE + INT_SIZE;

class SignatureFile {
  /**
   * Parses signature file buffer, retrieves hashes and the corresponding signatures.
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    const version = buffer.readInt8();
    switch (version) {
      case 4:
        this.version = 2;
        this._parseV2SignatureFile(buffer);
        break;
      case 5:
        this.version = 5;
        this._parseV5SignatureFile(buffer);
        break;
      default:
        throw new Error(`Unexpected signature file version '${version}'`);
    }
  }

  _parseV2SignatureFile(buffer) {
    // skip type, already checked
    buffer = buffer.slice(BYTE_SIZE);
    this.fileHash = readNBytes(buffer, HashObject.SHA_384.length);

    buffer = buffer.slice(HashObject.SHA_384.length);
    const type = buffer.readInt8();
    if (type !== 3) {
      throw new Error(`Unexpected type delimiter '${type}' in signature file`);
    }

    buffer = buffer.slice(BYTE_SIZE);
    const {length, bytes} = readLengthAndBytes(buffer, BYTE_SIZE, SignatureObject.SHA_384_WITH_RSA.maxLength, false);
    this.fileHashSignature = bytes;

    buffer = buffer.slice(length);
    if (buffer.length !== 0) {
      throw new Error('Extra data discovered in signature file ');
    }
  }

  _parseV5SignatureFile(buffer) {
    buffer = buffer.slice(V5_FILE_HASH_OFFSET);
    const fileHashObject = new HashObject(buffer);
    const fileHashSignatureObject = new SignatureObject(buffer.slice(fileHashObject.getLength()));

    buffer = buffer.slice(fileHashObject.getLength() + fileHashSignatureObject.getLength());
    const metadataHashObject = new HashObject(buffer);
    const metadataHashSignatureObject = new SignatureObject(buffer.slice(metadataHashObject.getLength()));

    if (buffer.length !== metadataHashObject.getLength() + metadataHashSignatureObject.getLength()) {
      throw new Error('Extra data discovered in signature file');
    }

    this.fileHash = fileHashObject.hash;
    this.fileHashSignature = fileHashSignatureObject.signature;
    this.metadataHash = metadataHashObject.hash;
    this.metadataHashSignature = metadataHashSignatureObject.signature;
  }
}

export default SignatureFile;
