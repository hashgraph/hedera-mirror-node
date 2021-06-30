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

  static ACCOUNT_ID = 'account_id';
  static ACCOUNT_ID_FULL_NAME = `${this.tableAlias}.${this.ACCOUNT_ID}`;
  static CREATED_TIMESTAMP = 'created_timestamp';
  static CREATED_TIMESTAMP_FULL_NAME = `${this.tableAlias}.${this.CREATED_TIMESTAMP}`;
  static DELETED = 'deleted';
  static DELETED_FULL_NAME = `${this.tableAlias}.${this.DELETED}`;
  static METADATA = 'metadata';
  static METADATA_FULL_NAME = `${this.tableAlias}.${this.METADATA}`;
  static MODIFIED_TIMESTAMP = 'modified_timestamp';
  static MODIFIED_TIMESTAMP_FULL_NAME = `${this.tableAlias}.${this.MODIFIED_TIMESTAMP}`;
  static SERIAL_NUMBER = 'serial_number';
  static SERIAL_NUMBER_FULL_NAME = `${this.tableAlias}.${this.SERIAL_NUMBER}`;
  static TOKEN_ID = 'token_id';
  static TOKEN_ID_FULL_NAME = `${this.tableAlias}.${this.TOKEN_ID}`;
}

module.exports = NftModel;
