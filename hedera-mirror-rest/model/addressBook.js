/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

class AddressBook {
  /**
   * Parses address_book table columns into object
   */
  constructor(addressBook) {
    // explicitly assign properties to restict properties and allow for composition in other models
    this.endConsensusTimestamp = addressBook.end_consensus_timestamp;
    this.fileData = addressBook.file_data;
    this.fileId = addressBook.file_id;
    this.nodeCount = addressBook.node_count;
    this.startConsensusTimestamp = addressBook.start_consensus_timestamp;
  }

  static tableAlias = 'adb';
  static tableName = 'address_book';

  static END_CONSENSUS_TIMESTAMP = 'end_consensus_timestamp';
  static FILE_DATA = 'file_data';
  static NODE_COUNT = 'node_count';
  static FILE_ID = 'file_id';
  static START_CONSENSUS_TIMESTAMP = 'start_consensus_timestamp';

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

export default AddressBook;
