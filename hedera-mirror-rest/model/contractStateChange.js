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

import _ from 'lodash';

class ContractStateChange {
  /**
   * Parses contract_state_change table columns into object
   */
  constructor(contractStateChange) {
    Object.assign(
      this,
      _.mapKeys(contractStateChange, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'csc';
  static tableName = 'contract_state_change';

  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SLOT = 'slot';
  static VALUE_READ = 'value_read';
  static VALUE_WRITTEN = 'value_written';

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

export default ContractStateChange;
