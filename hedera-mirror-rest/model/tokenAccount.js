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

class TokenAccount {
  static TOKEN_ID = `token_id`;
  static ACCOUNT_ID = `account_id`;

  /**
   * Parses token_account table columns into object
   */
  constructor(tokenRelationship) {
    this.automaticAssociation = tokenRelationship.automatic_association;
    this.balance = tokenRelationship.balance;
    this.createdTimestamp = tokenRelationship.created_timestamp;
    this.freezeStatus = tokenRelationship.freeze_status;
    this.kycStatus = tokenRelationship.kyc_status;
    this.tokenId = tokenRelationship.token_id;
  }

  static tableAlias = 'ta';
  static tableName = 'token_account';

  static AUTOMATIC_ASSOCIATION = 'automatic_association';
  static ASSOCIATED = 'associated';
  static BALANCE = 'balance';
  static CREATED_TIMESTAMP = 'created_timestamp';
  static FREEZE_STATUS = `freeze_status`;
  static KYC_STATUS = 'kyc_status';

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

export default TokenAccount;
