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

// external libraries
const _ = require('lodash');
const crypto = require('crypto');
const {Transaction} = require('@hashgraph/sdk/lib/generated/Transaction_pb');
const {TransactionBody} = require('@hashgraph/sdk/lib/generated/TransactionBody_pb');

class RecordFile {
  /**
   * Parses rcd file storing hash and transactionId map for future verification
   */
  constructor(recordFileBuffer) {
    this.parseRecordFileBuffer(recordFileBuffer);
  }

  parseRecordFileBuffer(recordFileBuffer) {
    const recordFileHash = crypto.createHash('sha384');
    const recordFileContentsHash = crypto.createHash('sha384');

    // read record file format version
    const recordFormatVersion = this.readIntFromBufferAndUpdateHash(recordFileBuffer, 0, recordFileHash);
    if (recordFormatVersion < 0 || recordFormatVersion > 3) {
      throw new Error(`Unexpected record file format version '${recordFormatVersion}'`);
    }

    // version
    this.readIntFromBufferAndUpdateHash(recordFileBuffer, 4, recordFileHash);

    const fileHashSize = 48; // number of bytes
    let index = 8;

    this.transactionIdMap = {};
    while (index < recordFileBuffer.length) {
      const typeDelimiter = recordFileBuffer[index++];

      switch (typeDelimiter) {
        case 1:
          // RECORD_TYPE_PREV_HASH
          recordFileHash.update(Buffer.from([typeDelimiter]));
          this.prevHash = this.readBytesFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            index + fileHashSize,
            recordFileHash
          );
          index += fileHashSize;
          break;
        case 2:
          // RECORD_TYPE_RECORD
          recordFileContentsHash.update(Buffer.from([typeDelimiter]));

          // transaction raw bytes
          const transactionRawBytesLength = this.readIntFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            recordFileContentsHash
          );
          index += 4;

          const transactionRawBuffer = this.readBytesFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            index + transactionRawBytesLength,
            recordFileContentsHash
          );
          index += transactionRawBytesLength;

          // record raw bytes
          const recordRawBytesLength = this.readIntFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            recordFileContentsHash
          );
          index += 4;

          // recordRawBuffer
          this.readBytesFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            index + recordRawBytesLength,
            recordFileContentsHash
          );
          index += recordRawBytesLength;

          this.updateTransactionIdMap(transactionRawBuffer);

          break;
        default:
          throw new Error(`Unexpected type delimiter '${typeDelimiter}' in record file at index '${index - 1}'`);
      }
    }

    if (recordFormatVersion === 2) {
      recordFileHash.update(recordFileContentsHash.digest());
    }

    // set recordFile hash
    this.hash = recordFileHash.digest('hex');
  }

  containsTransaction(transactionId) {
    return !_.isUndefined(this.transactionIdMap[transactionId]);
  }

  readIntFromBufferAndUpdateHash(buffer, start, cryptoHash) {
    const buf = buffer.subarray(start, start + 4);
    cryptoHash.update(buf);
    return buf.readInt32BE(0);
  }

  readByteFromBufferAndUpdateHash(buffer, index, cryptoHash) {
    const buf = buffer[index];
    cryptoHash.update(Buffer.from([buf]));
    return buf;
  }

  readBytesFromBufferAndUpdateHash(buffer, start, end, cryptoHash) {
    const buf = buffer.subarray(start, end);
    cryptoHash.update(buf);
    return buf;
  }

  updateTransactionIdMap(transactionRawBuffer) {
    // retrieve successful transactions and store transactionId in map for future use
    const deserializedTransaction = Transaction.deserializeBinary(transactionRawBuffer);
    const transactionBody = TransactionBody.deserializeBinary(deserializedTransaction.getBodybytes_asU8());
    const transactionIdFromBody = transactionBody.getTransactionid();
    const accountId = transactionIdFromBody.getAccountid();
    const timestamp = transactionIdFromBody.getTransactionvalidstart();

    const parsedTransactionIdString = `${accountId.getShardnum()}_${accountId.getRealmnum()}_${accountId.getAccountnum()}_${timestamp.getSeconds()}_${timestamp.getNanos()}`;

    // to:do add logic to pull success status from TransactionRecord as there may be duplicate transactionId's
    this.transactionIdMap[parsedTransactionIdString] = transactionIdFromBody;
  }
}

module.exports = {
  RecordFile,
};
