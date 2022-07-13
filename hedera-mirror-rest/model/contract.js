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

class Contract {
  /**
   * Parses contract table columns into object
   */
  constructor(contract) {
    Object.assign(
      this,
      _.mapKeys(contract, (v, k) => _.camelCase(k))
    );
  }

  static historyTableName = 'contract_history';
  static tableAlias = 'c';
  static tableName = 'contract';

  static AUTO_RENEW_ACCOUNT_ID = 'auto_renew_account_id';
  static AUTO_RENEW_PERIOD = 'auto_renew_period';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static DELETED = 'deleted';
  static EVM_ADDRESS = 'evm_address';
  static EXPIRATION_TIMESTAMP = 'expiration_timestamp';
  static FILE_ID = 'file_id';
  static ID = 'id';
  static INITCODE = 'initcode';
  static KEY = 'key';
  static MAX_AUTOMATIC_TOKEN_ASSOCIATIONS = 'max_automatic_token_associations';
  static MEMO = 'memo';
  static NUM = 'num';
  static OBTAINER_ID = 'obtainer_id';
  static PERMANENT_REMOVAL = 'permanent_removal';
  static PROXY_ACCOUNT_ID = 'proxy_account_id';
  static PUBLIC_KEY = 'public_key';
  static REALM = 'realm';
  static SHARD = 'shard';
  static TIMESTAMP_RANGE = 'timestamp_range';
  static TYPE = 'type';

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

export default Contract;
