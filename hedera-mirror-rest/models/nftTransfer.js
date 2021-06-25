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

const {EntityId} = require('../entityId');

class NftTransfer {
  /**
   * Parses nft table columns into object
   */
  constructor(consensusTimestamp, receiverAccountId, senderAccountId, serialNumber, tokenId) {
    this.consensus_timestamp = consensusTimestamp;
    this.receiver_account_id = EntityId.fromEncodedId(receiverAccountId).toString();
    this.sender_account_id = EntityId.fromEncodedId(senderAccountId).toString();
    this.serial_number = serialNumber;
    this.token_id = EntityId.fromEncodedId(tokenId).toString();
  }

  static tableAlias = 'nft_tr';
  static tableName = 'nft_transfer';
  static nftTransferColumns = {
    CONSENSUS_TIMESTAMP: `consensus_timestamp`,
    RECEIVER_ACCOUNT_ID: `receiver_account_id`,
    SENDER_ACCOUNT_ID: `sender_account_id`,
    SERIAL_NUMBER: `serial_number`,
    TOKEN_ID: `token_id`,
  };
  static nftTransferFullNameColumns = {
    CONSENSUS_TIMESTAMP: `${this.tableAlias}.consensus_timestamp`,
    RECEIVER_ACCOUNT_ID: `${this.tableAlias}.receiver_account_id`,
    SENDER_ACCOUNT_ID: `${this.tableAlias}.sender_account_id`,
    SERIAL_NUMBER: `${this.tableAlias}.serial_number`,
    TOKEN_ID: `${this.tableAlias}.token_id`,
  };
}

module.exports = NftTransfer;
