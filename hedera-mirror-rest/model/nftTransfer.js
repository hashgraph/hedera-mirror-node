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

const constants = require('../constants');

class NftTransfer {
  /**
   * Parses nft_transfer table columns into object
   */
  constructor(nftTransfer) {
    this.consensusTimestamp = nftTransfer.consensus_timestamp;
    this.receiverAccountId = nftTransfer.receiver_account_id;
    this.senderAccountId = nftTransfer.sender_account_id;
    this.serialNumber = nftTransfer.serial_number;
    this.tokenId = nftTransfer.token_id;
  }

  static tableAlias = 'nft_tr';
  static tableName = 'nft_transfer';

  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static RECEIVER_ACCOUNT_ID = `receiver_account_id`;
  static SENDER_ACCOUNT_ID = `sender_account_id`;
  static SERIAL_NUMBER = `serial_number`;
  static TOKEN_ID = `token_id`;

  static FILTER_MAP = {
    [constants.filterKeys.TIMESTAMP]: NftTransfer.getFullName(NftTransfer.CONSENSUS_TIMESTAMP),
  };

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

module.exports = NftTransfer;
