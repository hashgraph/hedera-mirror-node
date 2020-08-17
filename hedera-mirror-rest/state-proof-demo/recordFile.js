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

// recordFile object. Read buffer, parse file, potentially stores all transactionConsensusNs as keys, hash
// file_hash, consensus_start, consensus_end
// parse rcd file looking for transaction with matching consensus time stamp from transaction id

// external libraries
const _ = require('lodash');
var crypto = require('crypto');
const {TransactionRecord} = require('@hashgraph/sdk');
const {Transaction} = require('@hashgraph/sdk/lib/Transaction');

class recordFile {
  constructor(recordFileBuffer, transactionId) {
    this.hash = 'hash';
    this.consensusStart = 'consensus_start';
    this.consensusEnd = 'consensus_end';
    this.readRecordFile(recordFileBuffer, transactionId);
  }

  readRecordFile(recordFileBuffer) {
    let recordFileHash = crypto.createHash('sha384');
    let recordFileContentsHash = crypto.createHash('sha384');

    // read record file format version
    // const recordFormatVersion = recordFileBuffer.readInt32BE();
    const recordFormatVersion = this.readIntFromBufferAndUpdateHash(recordFileBuffer, 0, recordFileHash);
    if (recordFormatVersion < 0 || recordFormatVersion > 3) {
      throw new Error(`Unexpected record file format version '${recordFormatVersion}'`);
    }
    recordFileHash.update(Buffer.from([recordFormatVersion]));

    // version
    // recordFileHash.update(recordFileBuffer.readInt32BE(4));
    this.readIntFromBufferAndUpdateHash(recordFileBuffer, 4, recordFileHash);

    const fileHashSize = 48; // number of bytes
    let index = 8;

    this.transactionIdMap = {};
    while (index < recordFileBuffer.length - 1) {
      if (index < 0) {
        // reached end
        break;
      }

      const typeDelimiter = recordFileBuffer[index];
      index++;

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
          index = index + fileHashSize;
          break;
        case 2:
          // RECORD_TYPE_RECORD
          recordFileContentsHash.update(Buffer.from([typeDelimiter]));

          // transaction raw bytes
          let buf = recordFileBuffer.subarray(index, index + 4);
          recordFileContentsHash.update(buf);
          const transactionRawBytesLength = buf.readInt32BE(0);
          // const transactionRawBytesLength = this.readIntFromBufferAndUpdateHash(recordFileBuffer, index, index + 4, recordFileContentsHash);
          index = index + 4;

          const transactionRawBuffer = this.readBytesFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            index + transactionRawBytesLength,
            recordFileContentsHash
          );
          index = index + transactionRawBytesLength;

          // record raw bytes
          buf = recordFileBuffer.subarray(index, index + 4);
          recordFileContentsHash.update(buf);
          const recordRawBytesLength = buf.readInt32BE(0);
          // const recordRawBytesLength = this.readIntFromBufferAndUpdateHash(recordFileBuffer, index, index + 4, recordFileContentsHash);
          index = index + 4;

          // recordRawBuffer
          this.readBytesFromBufferAndUpdateHash(
            recordFileBuffer,
            index,
            index + recordRawBytesLength,
            recordFileContentsHash
          );
          index = index + recordRawBytesLength;

          const transaction = Transaction.fromBytes(transactionRawBuffer);
          const transactionIdBody = transaction.id;

          this.transactionIdMap[transactionIdBody.toString()] = transactionIdBody;

          break;
        default:
          throw new Error(`Unexpected type delimiter '${typeDelimiter}' in record file at index '${index - 1}'`);
      }
    }

    if (recordFormatVersion === 2) {
      recordFileHash.update(recordFileContentsHash.digest());
    }

    // set recordFile hash
    // recordFileHash.digest("hex")
    this.hash = recordFileHash.digest();
  }

  containsTransaction(transactionId) {
    return _.isUndefined(this.transactionIdMap[transactionId]);
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
}

module.exports = {
  recordFile,
};
