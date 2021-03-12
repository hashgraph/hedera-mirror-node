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

const {BYTE_SIZE} = require('./constants');
const StreamObject = require('./streamObject');
const {readLengthAndBytes} = require('./utils');

class RecordStreamObject extends StreamObject {
  static MAX_RECORD_LENGTH = 64 * 1024;
  static MAX_TRANSACTION_LENGTH = 64 * 1024;

  /**
   * Reads the body of the record stream object
   * @param {Buffer} buffer
   * @returns {Number} The size of the body in bytes
   */
  _readBody(buffer) {
    const record = readLengthAndBytes(buffer, BYTE_SIZE, RecordStreamObject.MAX_RECORD_LENGTH, false);
    const transaction = readLengthAndBytes(
      buffer.slice(record.length),
      BYTE_SIZE,
      RecordStreamObject.MAX_TRANSACTION_LENGTH,
      false
    );
    this.record = record.bytes;
    this.transaction = transaction.bytes;

    return record.length + transaction.length;
  }
}

module.exports = RecordStreamObject;
