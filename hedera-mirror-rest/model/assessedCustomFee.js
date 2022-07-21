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

class AssessedCustomFee {
  /**
   * Parses assessed_custom_fee table columns into object
   */
  constructor(assessedCustomFee) {
    this.amount = assessedCustomFee.amount;
    this.collectorAccountId = assessedCustomFee.collector_account_id;
    this.consensusTimestamp = assessedCustomFee.consensus_timestamp;
    this.effectivePayerAccountIds = assessedCustomFee.effective_payer_account_ids;
    this.tokenId = assessedCustomFee.token_id;
  }

  static tableAlias = 'acf';
  static tableName = 'assessed_custom_fee';

  static AMOUNT = `amount`;
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static EFFECTIVE_PAYER_ACCOUNT_IDS = `effective_payer_account_ids`;
  static TOKEN_ID = `token_id`;

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

export default AssessedCustomFee;
