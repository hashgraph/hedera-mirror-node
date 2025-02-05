/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

import FixedFee from './fixedFee';
import FractionalFee from './fractionalFee';
import RoyaltyFee from './royaltyFee';

class CustomFee {
  /**
   * Parses custom_fee table columns into object
   */
  constructor(customFee) {
    this.createdTimestamp = customFee.created_timestamp;
    this.fixedFees = (customFee.fixed_fees ?? []).map((n) => new FixedFee(n));
    this.fractionalFees = (customFee.fractional_fees ?? []).map((n) => new FractionalFee(n));
    this.royaltyFees = (customFee.royalty_fees ?? []).map((n) => new RoyaltyFee(n));
    this.tokenId = customFee.token_id;
  }

  static tableName = `custom_fee`;

  static ENTITY_ID = `entity_id`;
  static FIXED_FEES = `fixed_fees`;
  static FRACTIONAL_FEES = `fractional_fees`;
  static ROYALTY_FEES = `royalty_fees`;
  static TIMESTAMP_RANGE = `timestamp_range`;
}

export default CustomFee;
