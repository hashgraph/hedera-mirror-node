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

class TransactionResult {
  /**
   * Parses transaction results columns into object
   */
  constructor(result) {
    this.protoId = result.proto_id;
    this.result = result.result;
  }

  static tableAlias = 'ttr';
  static tableName = 't_transaction_results';

  static PROTO_ID = `proto_id`;
  static PROTO_ID_FULL_NAME = `${this.tableAlias}.${this.PROTO_ID}`;
  static RESULT = `result`;
  static RESULT_FULL_NAME = `${this.tableAlias}.${this.RESULT}`;
}

module.exports = TransactionResult;
