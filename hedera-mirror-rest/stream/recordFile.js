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

const {Status, TransactionRecord} = require('@hashgraph/sdk');

class RecordFile {
  constructor() {
    // a map of successful transactions, from its transaction ID to its index in the parsed transactions array
    // if the concrete class doesn't implement a transaction array, the index can be any value
    this._transactionMap = {};
  }

  static _getVersion(buffer) {
    return buffer.readInt32BE();
  }

  static _support(bufferOrObj) {
    return false;
  }

  static canCompact(buffer) {
    return false;
  }

  /**
   * Checks if a transaction is in the record file's successful transaction map
   *
   * @param {TransactionId} transactionId
   * @returns {boolean}
   */
  containsTransaction(transactionId) {
    return transactionId.toString() in this._transactionMap;
  }

  /**
   * Converts the parsed record file to the compact format object if possible. Throws error if the transactionId is not
   * in the successful transaction map or the implementation does not support the operation.
   *
   * @param {TransactionId} transactionId the transaction ID of interest
   */
  toCompactObject(transactionId) {
    throw new Error('Unsupported operation');
  }

  _addTransaction(recordBuffer, index) {
    const {receipt, transactionId} = TransactionRecord.fromBytes(recordBuffer);
    if (receipt.status !== Status.Success) {
      logger.info(`Skip non-successful transaction ${transactionId.toString()}, ${receipt.status}`);
      return;
    }

    logger.info(`Add successful transaction ${transactionId.toString()}`);
    this._transactionMap[transactionId.toString()] = index;
  }
}

module.exports = RecordFile;
