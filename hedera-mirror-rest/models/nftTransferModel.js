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

class NftTransferModel {
  /**
   * Parses nft table columns into object
   */
  constructor(dbRow) {
    this.consensus_timestamp = dbRow.consensus_timestamp;
    this.receiver_account_id = dbRow.receiver_account_id;
    this.sender_account_id = dbRow.sender_account_id;
    this.serial_number = dbRow.serial_number;
    this.token_id = dbRow.token_id;
  }

  static tableAlias = 'nft_tr';
  static tableName = 'nft_transfer';

  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static CONSENSUS_TIMESTAMP_FULL_NAME = `${this.tableAlias}.${this.CONSENSUS_TIMESTAMP}`;
  static RECEIVER_ACCOUNT_ID = `receiver_account_id`;
  static RECEIVER_ACCOUNT_ID_FULL_NAME = `${this.tableAlias}.${this.RECEIVER_ACCOUNT_ID}`;
  static SENDER_ACCOUNT_ID = `sender_account_id`;
  static SENDER_ACCOUNT_ID_FULL_NAME = `${this.tableAlias}.${this.SENDER_ACCOUNT_ID}`;
  static SERIAL_NUMBER = `serial_number`;
  static SERIAL_NUMBER_FULL_NAME = `${this.tableAlias}.${this.SERIAL_NUMBER}`;
  static TOKEN_ID = `token_id`;
  static TOKEN_ID_FULL_NAME = `${this.tableAlias}.${this.TOKEN_ID}`;
}

module.exports = NftTransferModel;
