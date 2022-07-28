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

class CryptoTransfer {
  /**
   * Parses crypto_transfer table columns into object
   */
  constructor(cryptoTransfer) {
    this.amount = cryptoTransfer.amount;
    this.consensusTimestamp = cryptoTransfer.consensus_timestamp;
    this.entityId = cryptoTransfer.entity_id;
    this.isApproval = cryptoTransfer.is_approval;
  }

  static tableAlias = 'ctr';
  static tableName = 'crypto_transfer';

  static AMOUNT = 'amount';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static IS_APPROVAL = 'is_approval';

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

export default CryptoTransfer;
