/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';

class TokenAllowance {
  /**
   * Parses token_allowance table columns into object
   */
  constructor(tokenAllowance) {
    Object.assign(
      this,
      _.mapKeys(tokenAllowance, (v, k) => _.camelCase(k))
    );
  }

  static historyTableName = 'token_allowance_history';
  static tableAlias = 'ta';
  static tableName = 'token_allowance';

  static AMOUNT = 'amount';
  static OWNER = 'owner';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SPENDER = 'spender';
  static TIMESTAMP_RANGE = 'timestamp_range';
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

export default TokenAllowance;
