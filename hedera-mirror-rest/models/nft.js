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
const utils = require('../utils');

class Nft {
  /**
   * Parses nft table columns into object
   */
  constructor(accountId, createdTimestamp, deleted, metadata, modifiedTimestamp, serialNumber, tokenId) {
    this.account_id = EntityId.fromEncodedId(accountId).toString();
    this.created_timestamp = createdTimestamp;
    this.deleted = deleted;
    this.metadata = utils.encodeKey(metadata); // base64 encode
    this.modified_timestamp = modifiedTimestamp;
    this.serial_number = serialNumber;
    this.token_id = EntityId.fromEncodedId(tokenId).toString();
  }

  static tableAlias = 'nft';
  static tableName = this.tableAlias;
  static nftQueryColumns = {
    ACCOUNT_ID: `${this.tableAlias}.account_id`,
    CREATED_TIMESTAMP: `${this.tableAlias}nft.created_timestamp`,
    DELETED: `${this.tableAlias}.deleted`,
    METADATA: `${this.tableAlias}.metadata`,
    MODIFIED_TIMESTAMP: `${this.tableAlias}.modified_timestamp`,
    SERIAL_NUMBER: `${this.tableAlias}.serial_number`,
    TOKEN_ID: `${this.tableAlias}.token_id`,
  };
}

module.exports = Nft;
