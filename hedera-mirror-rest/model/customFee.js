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

import FixedFee from './fixedFee.js';
import FractionalFee from './fractionalFee.js';
import RoyaltyFee from './royaltyFee.js';

class CustomFee {
  /**
   * Parses custom_fee table columns into object
   */
  constructor(customFee) {
    this.fixedFees = (customFee.fixed_fees ?? []).map((n) => new FixedFee(n));
    this.fractionalFees = (customFee.fractional_fees ?? []).map((n) => new FractionalFee(n));
    this.royaltyFees = (customFee.royalty_fees ?? []).map((n) => new RoyaltyFee(n));
    this.timestampRange = customFee.timestamp_range;
    this.tokenId = customFee.token_id;
  }

  static tableName = 'custom_fee';

  static FIXED_FEES = `fixed_fees`;
  static FRACTIONAL_FEES = `fractional_fees`;
  static ROYALTY_FEES = `royalty_fees`;
  static TOKEN_ID = `token_id`;
  static TIMESTAMP_RANGE = 'timestamp_range';
}

export default CustomFee;
