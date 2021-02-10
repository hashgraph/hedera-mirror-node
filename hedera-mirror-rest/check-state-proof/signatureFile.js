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

const {BYTE_SIZE, INT_SIZE, SHA_384} = require('./constants');
const {readLengthAndBytes, readNBytes, HashObject, StreamObject} = require('./streamObject');

// properties of SHA384WithRsa signature
const SHA_384_WITH_RSA = {
  type: 1,
  maxLength: 384,
};

// version, object stream signature version
const V5_FILE_HASH_OFFSET = BYTE_SIZE + INT_SIZE;

class SignatureObject extends StreamObject {
  /**
   * Reads the body of the signature object
   * @param {Buffer} buffer
   * @returns {Number} The size of the body in bytes
   */
  readBody(buffer) {
    const message = 'Error reading signature object';
    const type = buffer.readInt32BE();
    if (type !== SHA_384_WITH_RSA.type) {
      throw new Error(`${message}, expect type ${SHA_384_WITH_RSA.type} got ${type}`);
    }

    const {length, bytes} = readLengthAndBytes(buffer.slice(INT_SIZE), BYTE_SIZE, SHA_384_WITH_RSA.maxLength, true);
    this.signature = bytes;

    return INT_SIZE + length;
  }
}

class SignatureFile {
  /**
   * Parses signature file buffer, retrieve hash and node id
   * @param {Buffer} buffer
   * @param {String} nodeId
   */
  constructor(buffer, nodeId) {
    this.nodeId = nodeId;

    const version = buffer.readInt8();
    switch (version) {
      case 4:
        this.version = 2;
        this.parseV2SignatureFile(buffer);
        break;
      case 5:
        this.version = 5;
        this.parseV5SignatureFile(buffer);
        break;
      default:
        throw new Error(`Unexpected signature file version '${version}'`);
    }
  }

  parseV2SignatureFile(buffer) {
    // skip type, already checked
    buffer = buffer.slice(BYTE_SIZE);
    this.fileHash = readNBytes(buffer, SHA_384.length);

    buffer = buffer.slice(SHA_384.length);
    const type = buffer.readInt8();
    if (type !== 3) {
      throw new Error(`Unexpected type delimiter '${type}' in signature file`);
    }

    buffer = buffer.slice(BYTE_SIZE);
    const {length, bytes} = readLengthAndBytes(buffer, BYTE_SIZE, SHA_384_WITH_RSA.maxLength, false);
    this.fileHashSignature = bytes;

    buffer = buffer.slice(length);
    if (buffer.length !== 0) {
      throw new Error('Extra data discovered in signature file ');
    }
  }

  parseV5SignatureFile(buffer) {
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

module.exports = {
  SignatureFile,
};
