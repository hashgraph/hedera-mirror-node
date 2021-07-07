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

class AssessedCustomFee {
  /**
   * Parses assessed_custom_fee table columns into object
   */
  constructor(assessedCustomFee) {
    this.amount = assessedCustomFee.amount;
    this.collectorAccountId = assessedCustomFee.collector_account_id;
    this.consensusTimestamp = assessedCustomFee.consensus_timestamp;
    this.tokenId = assessedCustomFee.token_id;
  }

  static tableAlias = 'acf';
  static tableName = 'accessed_custom_fee';

  static AMOUNT = `amount`;
  static AMOUNT_FULL_NAME = `${this.tableAlias}.${this.AMOUNT}`;
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
  static COLLECTOR_ACCOUNT_ID_FULL_NAME = `${this.tableAlias}.${this.COLLECTOR_ACCOUNT_ID}`;
  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static CONSENSUS_TIMESTAMP_FULL_NAME = `${this.tableAlias}.${this.CONSENSUS_TIMESTAMP}`;
  static TOKEN_ID = `token_id`;
  static TOKEN_ID_FULL_NAME = `${this.tableAlias}.${this.TOKEN_ID}`;
}

module.exports = AssessedCustomFee;
