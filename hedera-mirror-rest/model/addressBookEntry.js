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

class AddressBookEntry {
  /**
   * Parses address_book_entry table columns into object
   */
  constructor(addressBookEntry) {
    // explicitly assign properties to restict properties and allow for composition in other models
    this.consensusTimestamp = addressBookEntry.consensus_timestamp;
    this.description = addressBookEntry.description;
    this.memo = addressBookEntry.memo;
    this.nodeAccountId = addressBookEntry.node_account_id;
    this.nodeCertHash = addressBookEntry.node_cert_hash;
    this.nodeId = addressBookEntry.node_id;
    this.publicKey = addressBookEntry.public_key;
    this.stake = addressBookEntry.stake;
  }

  static tableAlias = 'abe';
  static tableName = 'address_book_entry';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static MEMO = 'memo';
  static PUBLIC_KEY = 'public_key';
  static NODE_ID = 'node_id';
  static NODE_ACCOUNT_ID = 'node_account_id';
  static NODE_CERT_HASH = 'node_cert_hash';
  static DESCRIPTION = 'description';
  static STAKE = 'stake';

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

export default AddressBookEntry;
