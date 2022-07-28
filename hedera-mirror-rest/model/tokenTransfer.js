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

class TokenTransfer {
  /**
   * Parses token_transfer table columns into object
   */
  constructor(tokenTransfer) {
    this.accountId = tokenTransfer.account_id;
    this.amount = tokenTransfer.amount;
    this.consensusTimestamp = tokenTransfer.consensus_timestamp;
    this.isApproval = tokenTransfer.is_approval;
    this.tokenId = tokenTransfer.token_id;
  }

  static tableAlias = 'tk_tr';
  static tableName = 'token_transfer';

  static ACCOUNT_ID = 'account_id';
  static AMOUNT = 'amount';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static IS_APPROVAL = `is_approval`;
  static TOKEN_ID = 'token_id';

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

export default TokenTransfer;
