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

class ContractLog {
  /**
   * Parses contract_log table columns into object
   */
  constructor(contractLog) {
    Object.assign(
      this,
      _.mapKeys(contractLog, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'cl';
  static tableName = 'contract_log';

  static BLOOM = 'bloom';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static DATA = 'data';
  static INDEX = 'index';
  static ROOT_CONTRACT_ID = 'root_contract_id';
  static TOPIC0 = 'topic0';
  static TOPIC1 = 'topic1';
  static TOPIC2 = 'topic2';
  static TOPIC3 = 'topic3';

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

export default ContractLog;
