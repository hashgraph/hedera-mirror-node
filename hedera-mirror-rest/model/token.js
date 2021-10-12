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
  constructor(token) {
    this.createdTimestamp = BigInt(token.created_timestamp);
    this.deleted = token.deleted;
    this.decimals = BigInt(token.decimals);
    this.feeScheduleKey = token.fee_schedule_key;
    this.feeScheduleKeyEd25519Hex = token.fee_schedule_key_ed25519_hex;
    this.freezeDefault = token.freeze_default;
    this.freezeKey = token.freeze_key;
    this.freezeKeyEd25519Hex = token.freeze_key_ed25519_hex;
    this.initialSupply = BigInt(token.initial_supply);
    this.kycKey = token.kyc_key;
    this.kycKeyEd25519Hex = token.kyc_key_ed25519_hex;
    this.maxSupply = BigInt(token.max_supply);
    this.memo = token.memo;
    this.modifiedTimestamp = BigInt(token.modified_timestamp);
    this.name = token.name;
    this.pauseKey = token.pause_key;
    this.pauseStatus = token.pause_status;
    this.supplyKey = token.supply_key;
    this.supplyKeyEd25519Hex = token.supply_key_ed25519_hex;
    this.supplyType = token.supply_type;
    this.symbol = token.symbol;
    this.tokenId = token.token_id;
    this.totalSupply = BigInt(token.total_supply);
    this.treasuryAccountId = token.treasury_account_id;
    this.type = token.type;
    this.wipeKey = token.wipe_key;
    this.wipeKeyEd25519Hex = token.wipe_key_ed25519_hex;
  }

  static tableAlias = 'token';
  static tableName = this.tableAlias;

  static TOKEN_ID = `token_id`;

  static TYPE = {
    FUNGIBLE_COMMON: 'FUNGIBLE_COMMON',
    NON_FUNGIBLE_UNIQUE: 'NON_FUNGIBLE_UNIQUE',
  };
}

module.exports = Token;
