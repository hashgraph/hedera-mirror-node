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
const {TransactionRecord} = require('@hashgraph/sdk/lib/generated/TransactionRecord_pb');

class RecordFile {
  /**
   * Parses rcd file storing hash and transactionId map for future verification
   */
  constructor(recordFileBuffer) {
    this.parseRecordFileBuffer(recordFileBuffer);
  }

  parseRecordFileBuffer(recordFileBuffer) {
    const recordFileHash = crypto.createHash('sha384');
    let recordFileContentsHash = recordFileHash;

    // read record file format version
    const recordFormatVersion = this.readIntFromBufferAndUpdateHash(recordFileBuffer, 0, recordFileHash);
    if (recordFormatVersion >= 2) {
      recordFileContentsHash = crypto.createHash('sha384');
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

          this.readBytesFromBufferAndUpdateHash(
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
          const transactionRecordRawBuffer = this.readBytesFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            index + recordRawBytesLength,
            recordFileContentsHash
          );
          index += recordRawBytesLength;

          this.mapSuccessfulTransactions(transactionRecordRawBuffer);

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

  mapSuccessfulTransactions(transactionRecordRawBuffer) {
    const deserializedTransactionRecord = TransactionRecord.deserializeBinary(transactionRecordRawBuffer);
    const transactionReceipt = deserializedTransactionRecord.getReceipt();
    const status = transactionReceipt.getStatus();

    // check if status was SUCCESS, if so add to map
    if (status === 22) {
      const transactionIdFromBody = deserializedTransactionRecord.getTransactionid();
      const accountId = transactionIdFromBody.getAccountid();
      const timestamp = transactionIdFromBody.getTransactionvalidstart();

      // pad nanos if less than 9 digits
      const nanos = `000000000${timestamp.getNanos()}`.slice(-9);
      const parsedTransactionIdString = `${accountId.getShardnum()}_${accountId.getRealmnum()}_${accountId.getAccountnum()}_${timestamp.getSeconds()}_${nanos}`;
      this.transactionIdMap[parsedTransactionIdString] = transactionIdFromBody;
    }
  }
}

module.exports = {
  RecordFile,
};
