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

class Contract {
  static tableAlias = 'c';
  static tableName = 'contract';

  static FILE_ID = 'file_id';
  static ID = 'id';
  static INITCODE = 'initcode';
  static RUNTIME_BYTECODE = 'runtime_bytecode';

  /**
   * Parses contract table columns into object
   */
  constructor(contract) {
    Object.assign(
      this,
      _.mapKeys(contract, (v, k) => _.camelCase(k))
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

export default Contract;
