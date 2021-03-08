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

const {proto} = require('@hashgraph/proto/lib/proto');
const {logger, protoTransactionIdToString} = require('./utils');

class RecordFile {
  constructor() {
    this._fileHash = null;
    this._metadataHash = null;
    // a map of successful transactions, from its transaction ID to its index in the parsed transactions array
    // if the concrete class doesn't implement a transaction array, the index can be any value
    this._transactionMap = {};
    this._version = null;
  }

  static _readVersion(buffer) {
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
   * @param {string} transactionId
   * @param {boolean} scheduled
   * @returns {boolean}
   */
  containsTransaction(transactionId, scheduled = false) {
    return `${transactionId}-${scheduled}` in this._transactionMap;
  }

  /**
   * Gets the record file's file hash, may return null if not available.
   *
   * * @returns {Buffer}
   */
  getFileHash() {
    return this._fileHash;
  }

  /**
   * Gets the record file's metadata hash, may return null if not available.
   *
   * @returns {Buffer}
   */
  getMetadataHash() {
    return this._metadataHash;
  }

  /**
   * Gets the transaction map.
   * @returns {{}}
   */
  getTransactionMap() {
    return this._transactionMap;
  }

  /**
   * Gets the version.
   *
   * @returns {number}
   */
  getVersion() {
    return this._version;
  }

  /**
   * Converts the parsed record file to the compact format object if possible. Throws error if the transactionId is not
   * in the successful transaction map or the implementation does not support the operation.
   *
   * @param {string} transactionId
   * * @param {boolean} scheduled
   * @returns {{}}
   */
  toCompactObject(transactionId, scheduled = false) {
    throw new Error('Unsupported operation');
  }

  /**
   * Adds a mapping of a transaction's id to its index in the record file if the transaction response status is SUCCESS.
   *
   * @param {Buffer} recordBuffer
   * @param {Number} index
   * @private
   */
  _addTransaction(recordBuffer, index) {
    const {receipt, transactionID, scheduleRef} = proto.TransactionRecord.decode(recordBuffer);
    const transactionIdStr = protoTransactionIdToString(transactionID);
    const scheduled = scheduleRef !== null;
    if (receipt.status !== proto.ResponseCodeEnum.SUCCESS) {
      logger.debug(`Skip non-successful transaction ${transactionIdStr}, ${receipt.status}`);
      return;
    }

    const transactionKey = `${transactionIdStr}-${scheduled}`;
    logger.debug(`Add successful transaction ${transactionKey}`);
    this._transactionMap[transactionKey] = index;
  }
}

module.exports = RecordFile;
