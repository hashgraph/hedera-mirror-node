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

class ContractAction {
  static tableAlias = 'cact';
  static tableName = 'contract_action';

  static CALL_DEPTH = 'call_depth';
  static CALL_OPERATION_TYPE = 'call_operation_type';
  static CALL_TYPE = 'call_type';
  static CALLER = 'caller';
  static CALLER_TYPE = 'caller_type';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static GAS = 'gas';
  static GAS_USED = 'gas_used';
  static INDEX = 'index';
  static INPUT = 'input';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RECIPIENT_ACCOUNT = 'recipient_account';
  static RECIPIENT_ADDRESS = 'recipient_address';
  static RECIPIENT_CONTRACT = 'recipient_contract';
  static RESULT_DATA = 'result_data';
  static RESULT_DATA_TYPE = 'result_data_type';
  static VALUE = 'value';

  /**
   * Parses contract_action table columns into object
   */
  constructor(contractAction) {
    Object.assign(
      this,
      _.mapKeys(contractAction, (v, k) => _.camelCase(k))
    );
  }

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

export default ContractAction;
