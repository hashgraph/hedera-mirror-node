/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

'use strict';

class TransactionTypeModel {
  /**
   * Parses transaction type columns into object
   */
  constructor(dbRow) {
    this.name = dbRow.name;
    this.entity_type = dbRow.entity_type;
    this.proto_id = dbRow.proto_id;
  }

  static tableAlias = 'ttt';
  static tableName = 't_transaction_types';

  static ENTITY_TYPE = `entity_type`;
  static ENTITY_TYPE_FULL_NAME = `${this.tableAlias}.${this.ENTITY_TYPE}`;
  static NAME = `name`;
  static NAME_FULL_NAME = `${this.tableAlias}.${this.NAME}`;
  static PROTO_ID = `proto_id`;
  static PROTO_ID_FULL_NAME = `${this.tableAlias}.${this.PROTO_ID}`;
}

module.exports = TransactionTypeModel;
