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

class NftModel {
  /**
   * Parses nft table columns into object
   */
  constructor(dbRow) {
    this.account_id = dbRow.account_id;
    this.created_timestamp = dbRow.created_timestamp;
    this.deleted = dbRow.deleted;
    this.metadata = dbRow.metadata;
    this.modified_timestamp = dbRow.modified_timestamp;
    this.serial_number = dbRow.serial_number;
    this.token_id = dbRow.token_id;
  }

  static tableAlias = 'nft';
  static tableName = this.tableAlias;
  static nftQueryColumns = {
    ACCOUNT_ID: `${this.tableAlias}.account_id`,
    CREATED_TIMESTAMP: `${this.tableAlias}.created_timestamp`,
    DELETED: `${this.tableAlias}.deleted`,
    METADATA: `${this.tableAlias}.metadata`,
    MODIFIED_TIMESTAMP: `${this.tableAlias}.modified_timestamp`,
    SERIAL_NUMBER: `${this.tableAlias}.serial_number`,
    TOKEN_ID: `${this.tableAlias}.token_id`,
  };
}

module.exports = NftModel;
