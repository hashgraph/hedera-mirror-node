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
const {TransactionRecord} = require('@hashgraph/sdk/lib/generated/TransactionRecord_pb');

const {BYTE_SIZE, INT_SIZE} = require('./constants');
const HashObject = require('./hashObject');
const RecordStreamObject = require('./recordStreamObject');
const {readLengthAndBytes} = require('./utils');

const {MAX_TRANSACTION_LENGTH, MAX_RECORD_LENGTH} = RecordStreamObject;
const {SHA_384} = HashObject;

// version, hapiVersion, previous hash marker, SHA-384 hash
const PRE_V5_HEADER_LENGTH = INT_SIZE + INT_SIZE + BYTE_SIZE + SHA_384.length;

// version, hapi version major/minor/patch, object stream version
const V5_START_HASH_OFFSET = INT_SIZE + (INT_SIZE + INT_SIZE + INT_SIZE) + INT_SIZE;

class RecordFile {
  /**
   * Parses rcd file storing hash and transactionId map for future verification
   * @param {Buffer} buffer
   */
  constructor(buffer) {
    this.transactionIdMap = {};

    this.version = buffer.readInt32BE();
    switch (this.version) {
      case 1:
      case 2:
        this.parsePreV5RecordFile(buffer);
        break;
      case 5:
        this.parseV5RecordFile(buffer);
        break;
      default:
        throw new Error(`Unsupported record file version ${this.version}`);
    }
  }

  parsePreV5RecordFile(buffer) {
    this.calculatePreV5FileHash(buffer.readInt32BE(), buffer);

    buffer = buffer.slice(PRE_V5_HEADER_LENGTH);
    while (buffer.length !== 0) {
      const marker = buffer.readInt8();
      if (marker !== 2) {
        throw new Error(`Unsupported marker ${marker}, expect 2`);
      }

      buffer = buffer.slice(BYTE_SIZE);
      const transaction = readLengthAndBytes(buffer, BYTE_SIZE, MAX_TRANSACTION_LENGTH, false);
      const record = readLengthAndBytes(buffer.slice(transaction.length), BYTE_SIZE, MAX_RECORD_LENGTH, false);
      this.mapSuccessfulTransactions(record.bytes);

      buffer = buffer.slice(transaction.length + record.length);
    }
  }

  parseV5RecordFile(buffer) {
    this.fileHash = crypto.createHash(SHA_384.name).update(buffer).digest(SHA_384.encoding);
    const metadataDigest = crypto.createHash(SHA_384.name).update(buffer.slice(0, V5_START_HASH_OFFSET));

    // skip the bytes before the start hash object to read a list of stream objects organized as follows:
    //
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |  Start Object Running Hash  |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    Record Stream Object     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    ...                      |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |    Record Stream Object     |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |   End Object Running Hash   |
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    //
    // Note the start object running hash and the end object running hash are of the same type HashObject and
    // they have the same classId.
    buffer = buffer.slice(V5_START_HASH_OFFSET);
    const startHashObject = new HashObject(buffer);
    metadataDigest.update(buffer.slice(0, startHashObject.getLength()));

    buffer = buffer.slice(startHashObject.getLength());
    while (buffer.readBigInt64BE() !== startHashObject.classId) {
      // record stream objects are between the start hash object and the end hash object
      const recordStreamObject = new RecordStreamObject(buffer);
      this.mapSuccessfulTransactions(recordStreamObject.record);
      buffer = buffer.slice(recordStreamObject.getLength());
    }

    const endHashObject = new HashObject(buffer);
    if (buffer.length !== endHashObject.getLength()) {
      throw new Error('Extra data discovered in record file');
    }

    this.metadataHash = metadataDigest.update(buffer).digest(SHA_384.encoding);
  }

  calculatePreV5FileHash(version, buffer) {
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

  containsTransaction(transactionId) {
    return transactionId in this.transactionIdMap;
  }

  mapSuccessfulTransactions(transactionRecordRawBuffer) {
    const transactionRecord = TransactionRecord.deserializeBinary(transactionRecordRawBuffer);
    const transactionReceipt = transactionRecord.getReceipt();
    const status = transactionReceipt.getStatus();

    // check if status was SUCCESS, if so add to map
    if (status === 22) {
      const transactionId = transactionRecord.getTransactionid();
      const accountId = transactionId.getAccountid();
      const timestamp = transactionId.getTransactionvalidstart();

      // pad nanos if less than 9 digits
      const nanos = `000000000${timestamp.getNanos()}`.slice(-9);
      const parsedTransactionIdString = `${accountId.getShardnum()}_${accountId.getRealmnum()}_${accountId.getAccountnum()}_${timestamp.getSeconds()}_${nanos}`;
      this.transactionIdMap[parsedTransactionIdString] = transactionId;
    }
  }
}

module.exports = RecordFile;
