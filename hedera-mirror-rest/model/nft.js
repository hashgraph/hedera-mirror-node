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

const BaseModel = require('./baseModel');

class Nft extends BaseModel {
  /**
   * Parses nft table columns into object
   */
  constructor(nft) {
    super();
    this.accountId = nft.account_id;
    this.createdTimestamp = nft.created_timestamp;
    this.deleted = nft.deleted;
    this.metadata = nft.metadata;
    this.modifiedTimestamp = nft.modified_timestamp;
    this.serialNumber = nft.serial_number;
    this.tokenId = nft.token_id;
  }

  static tableAlias = 'nft';
  static tableName = this.tableAlias;

  static ACCOUNT_ID = 'account_id';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static DELETED = 'deleted';
  static METADATA = 'metadata';
  static MODIFIED_TIMESTAMP = 'modified_timestamp';
  static SERIAL_NUMBER = 'serial_number';
  static TOKEN_ID = 'token_id';
}

module.exports = Nft;
