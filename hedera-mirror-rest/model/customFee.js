/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

import {filterKeys} from '../constants';
import FixedFee from './fixedFee.js';
import FractionalFee from './fractionalFee.js';
import RoyaltyFee from './royaltyFee.js';

class CustomFee {
  /**
   * Parses custom_fee table columns into object
   */
  constructor(customFee) {
    this.createdTimestamp = customFee.created_timestamp;
    this.fixedFees = (customFee.fixed_fees ?? []).map((n) => new FixedFee(n));
    this.fractionalFees = (customFee.fractional_fees ?? []).map((n) => new FractionalFee(n));
    this.royaltyFees = (customFee.royalty_fees ?? []).map((n) => new RoyaltyFee(n));
    this.timestampRange = customFee.timestamp_range;
    this.tokenId = customFee.token_id;
  }

  static tableAlias = 'cf';
  static tableName = 'custom_fee';
  static historyTableAlias = 'cfh';
  static historyTableName = 'custom_fee_history';

  static CREATED_TIMESTAMP = `created_timestamp`;
  static FIXED_FEES = `fixed_fees`;
  static FRACTIONAL_FEES = `fractional_fees`;
  static ROYALTY_FEES = `royalty_fees`;
  static TOKEN_ID = `token_id`;
  static TIMESTAMP_RANGE = 'timestamp_range';

  static FILTER_MAP = {
    [filterKeys.TIMESTAMP]: CustomFee.getFullName(CustomFee.TIMESTAMP_RANGE),
  };

  static HISTORY_FILTER_MAP = {
    [filterKeys.TIMESTAMP]: CustomFee.getHistoryFullName(CustomFee.TIMESTAMP_RANGE),
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

  static getHistoryFullName(columnName) {
    return `${this.historyTableAlias}.${columnName}`;
  }
}

export default CustomFee;
