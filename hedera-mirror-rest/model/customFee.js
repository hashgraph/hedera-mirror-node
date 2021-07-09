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

const constants = require('../constants');

class CustomFee {
  /**
   * Parses custom_fee table columns into object
   */
  constructor(customFee) {
    this.amount = customFee.amount;
    this.amountDenominator = customFee.amount_denominator;
    this.collectorAccountId = customFee.collector_account_id;
    this.createdTimestamp = customFee.created_timestamp;
    this.denominatingTokenId = customFee.denominating_token_id;
    this.maximumAmount = customFee.maximum_amount;
    this.minimumAmount = customFee.minimum_amount;
    this.tokenId = customFee.token_id;
  }

  static tableAlias = 'cf';
  static tableName = 'custom_fee';

  static AMOUNT = `amount`;
  static AMOUNT_FULL_NAME = this._getFullName(this.AMOUNT);
  static AMOUNT_DENOMINATOR = `amount_denominator`;
  static AMOUNT_DENOMINATOR_FULL_NAME = this._getFullName(this.AMOUNT_DENOMINATOR);
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
  static COLLECTOR_ACCOUNT_ID_FULL_NAME = this._getFullName(this.COLLECTOR_ACCOUNT_ID);
  static CREATED_TIMESTAMP = `created_timestamp`;
  static CREATED_TIMESTAMP_FULL_NAME = this._getFullName(this.CREATED_TIMESTAMP);
  static DENOMINATING_TOKEN_ID = `denominating_token_id`;
  static DENOMINATING_TOKEN_ID_FULL_NAME = this._getFullName(this.DENOMINATING_TOKEN_ID);
  static MAXIMUM_AMOUNT = `maximum_amount`;
  static MAXIMUM_AMOUNT_FULL_NAME = this._getFullName(this.MAXIMUM_AMOUNT);
  static MINIMUM_AMOUNT = `minimum_amount`;
  static MINIMUM_AMOUNT_FULL_NAME = this._getFullName(this.MINIMUM_AMOUNT);
  static TOKEN_ID = `token_id`;
  static TOKEN_ID_FULL_NAME = this._getFullName(this.TOKEN_ID);

  static FILTER_MAP = {
    [constants.filterKeys.TIMESTAMP]: CustomFee.CREATED_TIMESTAMP_FULL_NAME,
  };

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static _getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

module.exports = CustomFee;
