/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import _ from 'lodash';

class ContractState {
  /**
   * Parses contract_state table columns into object
   */
  constructor(contractState) {
    Object.assign(
      this,
      _.mapKeys(contractState, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'cs';
  static tableName = 'contract_state';

  static CREATED_TIMESTAMP = 'created_timestamp';
  static MODIFIED_TIMESTAMP = 'modified_timestamp';
  static CONTRACT_ID = 'contract_id';
  static SLOT = 'slot';
  static VALUE = 'value';

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

export default ContractState;
