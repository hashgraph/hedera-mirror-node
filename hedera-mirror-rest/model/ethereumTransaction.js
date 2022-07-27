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

class EthereumTransaction {
  /**
   * Parses ethereum_transaction table columns into object
   */
  constructor(ethereumTransaction) {
    Object.assign(
      this,
      _.mapKeys(ethereumTransaction, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'etht';
  static tableName = 'ethereum_transaction';

  static ACCESS_LIST = 'access_list';
  static CALL_DATA_ID = 'call_data_id';
  static CALL_DATA = 'call_data';
  static CHAIN_ID = 'chain_id';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static DATA = 'data';
  static FROM_ADDRESS = 'from_address';
  static GAS_LIMIT = 'gas_limit';
  static GAS_PRICE = 'gas_price';
  static HASH = 'hash';
  static MAX_FEE_PER_GAS = 'max_fee_per_gas';
  static MAX_GAS_ALLOWANCE = 'max_gas_allowance';
  static MAX_PRIORITY_FEE_PER_GAS = 'max_priority_fee_per_gas';
  static NONCE = 'nonce';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RECOVERY_ID = 'recovery_id';
  static SIGNATURE_R = 'signature_r';
  static SIGNATURE_S = 'signature_s';
  static SIGNATURE_V = 'signature_v';
  static TO_ADDRESS = 'to_address';
  static TYPE = 'type';
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

export default EthereumTransaction;
