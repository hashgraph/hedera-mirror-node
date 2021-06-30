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

class TokenModel {
  /**
   * Parses token table columns into object
   */
  constructor(dbRow) {
    this.admin_key = dbRow.admin_key;
    this.auto_renew_account = dbRow.auto_renew_account;
    this.auto_renew_period = dbRow.auto_renew_period;
    this.created_timestamp = dbRow.created_timestamp;
    this.decimals = dbRow.decimals;
    this.expiry_timestamp = dbRow.expiry_timestamp;
    this.freeze_default = dbRow.freeze_default;
    this.freeze_key = dbRow.freeze_key;
    this.initial_supply = dbRow.initial_supply;
    this.kyc_key = dbRow.kyc_key;
    this.max_supply = dbRow.max_supply;
    this.modified_timestamp = dbRow.modified_timestamp;
    this.name = dbRow.name;
    this.supply_key = dbRow.supply_key;
    this.supply_type = dbRow.supply_type;
    this.symbol = dbRow.symbol;
    this.token_id = dbRow.token_id;
    this.total_supply = dbRow.total_supply;
    this.treasury_account_id = dbRow.treasury_account_id;
    this.type = dbRow.type;
    this.wipe_key = dbRow.wipe_key;
  }

  static tableAlias = 'token';
  static tableName = this.tableAlias;

  static TOKEN_ID = `token_id`;
}

module.exports = TokenModel;
