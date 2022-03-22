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

'use strict';

const _ = require('lodash');

class AddressBookServiceEndpoint {
  /**
   * Parses crypto_allowance table columns into object
   */
  constructor(cryptoAllowance) {
    Object.assign(
      this,
      _.mapKeys(cryptoAllowance, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'abse';
  static tableName = 'address_book_service_endpoint';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static IP_ADDRESS_V4 = 'ip_address_v4';
  static NODE_ID = 'node_id';
  static PORT = 'port';

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

module.exports = AddressBookServiceEndpoint;
