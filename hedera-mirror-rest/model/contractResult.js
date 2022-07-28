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

class ContractResult {
  /**
   * Parses contract_result table columns into object
   */
  constructor(contractResult) {
    Object.assign(
      this,
      _.mapKeys(contractResult, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'cr';
  static tableName = 'contract_result';

  static AMOUNT = 'amount';
  static BLOOM = 'bloom';
  static CALL_RESULT = 'call_result';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static CREATED_CONTRACT_IDS = 'created_contract_ids';
  static ERROR_MESSAGE = 'error_message';
  static FUNCTION_PARAMETERS = 'function_parameters';
  static FUNCTION_RESULT = 'function_result';
  static GAS_LIMIT = 'gas_limit';
  static GAS_USED = 'gas_used';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static SENDER_ID = 'sender_id';

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

export default ContractResult;
