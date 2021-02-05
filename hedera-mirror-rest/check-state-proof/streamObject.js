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

const constants = require('./constants');

/**
 * Reads the length field, an optional checksum, and the byte array from buffer
 * @param {Buffer} buffer - The buffer to read data from
 * @param {Number} minLength - The minimum allowed length
 * @param {Number} maxLength - The maxinum allowed length
 * @param {boolean} hasChecksum - If there is a checksum field
 * @return {Object} The length read and the bytes
 */
const readLengthAndBytes = (buffer, minLength, maxLength, hasChecksum) => {
  const message = 'Error reading length and bytes';
  const length = buffer.readInt32BE();
  let offset = 4;
  if (minLength === maxLength) {
    if (length !== minLength) {
      throw new Error(`${message}, expect length ${minLength} got ${length}`);
    }
  } else if (length < minLength || length > maxLength) {
    throw new Error(`${message}, expect length ${minLength} within [${minLength}, ${maxLength}]`);
  }

  if (hasChecksum) {
    const checksum = buffer.readInt32BE(offset);
    const expected = constants.SIMPLE_SUM - length;
    offset += 4;
    if (checksum !== expected) {
      throw new Error(`${message}, expect checksum ${checksum} to be ${expected}`);
    }
  }

  return {
    length: offset + length,
    bytes: readNBytes(buffer.slice(offset), length),
  };
};

/**
 * Reads a byte array from buffer
 * @param {Buffer} buffer
 * @param {Number} length
 * @return {Buffer}
 */
const readNBytes = (buffer, length) => {
  if (buffer.length < length) {
    throw new Error(`Error reading byte array, expect ${length}-byte data got ${buffer.length}-byte`);
  }

  return buffer.slice(0, length);
};

class StreamObject {
  /**
   * Reads stream object from buffer
   * @param {Buffer} buffer - The buffer to read the stream object from
   */
  constructor(buffer) {
    this.classId = buffer.readBigInt64BE();
    this.classVersion = buffer.readInt32BE(8);
  }

  getLength() {
    return 12;
  }
}

class HashObject extends StreamObject {
  /**
   * Reads hash object from buffer
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    super(buffer);
    this.read(buffer.slice(super.getLength()));
  }

  read(buffer) {
    // always SHA-384
    const hashLength = constants.SHA_384_LENGTH;
    this.digestType = buffer.readInt32BE();
    const {length, bytes} = readLengthAndBytes(buffer.slice(4), hashLength, hashLength, false);
    this.dataLength = 4 + length;
    this.hash = bytes;
  }

  getLength() {
    return super.getLength() + this.dataLength;
  }
}

class RecordStreamObject extends StreamObject {
  /**
   * Reads record stream object from buffer
   * @param buffer
   */
  constructor(buffer) {
    super(buffer);
    this.read(buffer.slice(super.getLength()));
  }

  read(buffer) {
    const record = readLengthAndBytes(buffer, 1, constants.MAX_RECORD_LENGTH, false);
    const transaction = readLengthAndBytes(buffer.slice(record.length), 1, constants.MAX_TRANSACTION_LENGTH, false);
    this.record = record.bytes;
    this.transaction = transaction.bytes;
    this.dataLength = record.length + transaction.length;
  }

  getLength() {
    return super.getLength() + this.dataLength;
  }
}

class SignatureObject extends StreamObject {
  /**
   * Reads signature object from buffer
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    super(buffer);
    this.read(buffer.slice(super.getLength()));
  }

  read(buffer) {
    const message = 'Error reading signature object';
    const type = buffer.readInt32BE();
    if (type !== constants.SHA_384_WITH_RSA.type) {
      throw new Error(`${message}, expect type ${constants.SHA_384_WITH_RSA.type} got ${type}`);
    }

    const {length, bytes} = readLengthAndBytes(buffer.slice(4), 1, constants.SHA_384_WITH_RSA.maxLength, true);
    this.dataLength = 4 + length;
    this.signature = bytes;
  }

  getLength() {
    return super.getLength() + this.dataLength;
  }
}

module.exports = {
  readLengthAndBytes,
  readNBytes,
  StreamObject,
  HashObject,
  RecordStreamObject,
  SignatureObject,
};
