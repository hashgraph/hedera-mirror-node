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

class TokenRelationship {
  /**
   * Parses token_transfer table columns into object
   */
  constructor(tokenRelationship) {
    this.automatic_association = tokenRelationship.automatic_association;
    this.balance = tokenRelationship.balance;
    this.created_timestamp = tokenRelationship.created_timestamp;
    this.freeze_status = tokenRelationship.freeze_status;
    this.kyc_status = tokenRelationship.kyc_status;
    this.symbol = tokenRelationship.symbol;
    this.token_id = tokenRelationship.token_id;
  }

  static tableAlias = 'tk_tr';
  static tableName = 'token_account';

  static AUTOMATIC_ASSOCIATION = 'associated';
  static BALANCE = 'balance';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static FREEZE_STATUS = `freeze_status`;
  static KYC_STATUS = 'kyc_status';
  static SYMBOL = 'symbol';
  static TOKEN_ID = 'token_id';

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

export default TokenRelationship;
