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

import {filterKeys} from '../constants';

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
    this.netOfTransfers = customFee.net_of_transfers;
    this.royaltyDenominator = customFee.royalty_denominator;
    this.royaltyNumerator = customFee.royalty_numerator;
    this.tokenId = customFee.token_id;
  }

  static tableAlias = 'cf';
  static tableName = 'custom_fee';

  static AMOUNT = `amount`;
  static AMOUNT_DENOMINATOR = `amount_denominator`;
  static COLLECTOR_ACCOUNT_ID = `collector_account_id`;
  static CREATED_TIMESTAMP = `created_timestamp`;
  static DENOMINATING_TOKEN_ID = `denominating_token_id`;
  static MAXIMUM_AMOUNT = `maximum_amount`;
  static MINIMUM_AMOUNT = `minimum_amount`;
  static NET_OF_TRANSFERS = `net_of_transfers`;
  static ROYALTY_DENOMINATOR = `royalty_denominator`;
  static ROYALTY_NUMERATOR = `royalty_numerator`;
  static TOKEN_ID = `token_id`;

  static FILTER_MAP = {
    [filterKeys.TIMESTAMP]: CustomFee.getFullName(CustomFee.CREATED_TIMESTAMP),
  };

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

export default CustomFee;
