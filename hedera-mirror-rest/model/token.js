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

class Token {
  /**
   * Parses token table columns into object
   */
  constructor(dbRow) {
    this.adminKey = dbRow.admin_key;
    this.autoRenewAccount = dbRow.auto_renew_account;
    this.autoRenewPeriod = dbRow.auto_renew_period;
    this.createdTimestamp = dbRow.created_timestamp;
    this.decimals = dbRow.decimals;
    this.expiryTimestamp = dbRow.expiry_timestamp;
    this.freezeDefault = dbRow.freeze_default;
    this.freezeKey = dbRow.freeze_key;
    this.initialSupply = dbRow.initial_supply;
    this.kycKey = dbRow.kyc_key;
    this.maxSupply = dbRow.max_supply;
    this.modifiedTimestamp = dbRow.modified_timestamp;
    this.name = dbRow.name;
    this.supplyKey = dbRow.supply_key;
    this.supplyType = dbRow.supply_type;
    this.symbol = dbRow.symbol;
    this.tokenId = dbRow.token_id;
    this.totalSupply = dbRow.total_supply;
    this.treasuryAccountId = dbRow.treasury_account_id;
    this.type = dbRow.type;
    this.wipeKey = dbRow.wipe_key;
  }

  static tableAlias = 'token';
  static tableName = this.tableAlias;

  static TOKEN_ID = `token_id`;
}

module.exports = Token;
