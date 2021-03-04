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

// external libraries
const crypto = require('crypto');

const {BYTE_SIZE, INT_SIZE} = require('./constants');
const HashObject = require('./hashObject');
const RecordFile = require('./recordFile');
const RecordStreamObject = require('./recordStreamObject');
const {readLengthAndBytes} = require('./utils');

const {MAX_TRANSACTION_LENGTH, MAX_RECORD_LENGTH} = RecordStreamObject;
const {SHA_384} = HashObject;

// version, hapiVersion, previous hash marker, SHA-384 hash
const PRE_V5_HEADER_LENGTH = INT_SIZE + INT_SIZE + BYTE_SIZE + SHA_384.length;

class FullRecordFile extends RecordFile {
  /**
   * Parses rcd file storing hash and transactionId map for future verification
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    super();

    if (FullRecordFile._support(buffer)) {
      this._parsePreV5RecordFile(buffer);
    }

    throw new Error(`Unsupported record file version ${this.version}`);
  }

  static _support(bufferOrObj) {
    if (!Buffer.isBuffer(bufferOrObj)) {
      return false;
    }

    const version = RecordFile._getVersion(bufferOrObj);
    return version === 1 || version === 2;
  }

  _parsePreV5RecordFile(buffer) {
    this.version = RecordFile._getVersion(buffer);
    this._calculatePreV5FileHash(buffer.readInt32BE(), buffer);

    buffer = buffer.slice(PRE_V5_HEADER_LENGTH);
    while (buffer.length !== 0) {
      const marker = buffer.readInt8();
      if (marker !== 2) {
        throw new Error(`Unsupported marker ${marker}, expect 2`);
      }

      buffer = buffer.slice(BYTE_SIZE);
      const transaction = readLengthAndBytes(buffer, BYTE_SIZE, MAX_TRANSACTION_LENGTH, false);
      const record = readLengthAndBytes(buffer.slice(transaction.length), BYTE_SIZE, MAX_RECORD_LENGTH, false);
      this._addTransaction(record.bytes);

      buffer = buffer.slice(transaction.length + record.length);
    }
  }

  _calculatePreV5FileHash(version, buffer) {
    const fileDigest = crypto.createHash(SHA_384.name);

    if (version === 1) {
      fileDigest.update(buffer);
    } else {
      // version 2
      const contentHash = crypto.createHash(SHA_384.name).update(buffer.slice(PRE_V5_HEADER_LENGTH)).digest();
      fileDigest.update(buffer.slice(0, PRE_V5_HEADER_LENGTH)).update(contentHash);
    }

    this.fileHash = fileDigest.digest(SHA_384.encoding);
  }
}

module.exports = FullRecordFile;
