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

const _ = require('lodash');

class FileData {
  /**
   * Parses file_data table columns into object
   */
  constructor(fileData) {
    Object.assign(
      this,
      _.mapKeys(fileData, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'f';
  static tableName = 'file_data';

  static FILE_DATA = 'file_data';
  static FILE_DATA_FULL_NAME = this.getFullName(FileData.FILE_DATA);
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONSENSUS_TIMESTAMP_FULL_NAME = this.getFullName(FileData.CONSENSUS_TIMESTAMP);
  static ENTITY_ID = 'entity_id';
  static ENTITY_ID_FULL_NAME = this.getFullName(FileData.ENTITY_ID);
  static TRANSACTION_TYPE = 'transaction_type';
  static TRANSACTION_TYPE_FULL_NAME = this.getFullName(FileData.TRANSACTION_TYPE);

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

module.exports = FileData;
