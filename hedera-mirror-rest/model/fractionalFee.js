/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import Fee from './fee';

class FractionalFee extends Fee {
  /**
   * Parses fractional_fee from element in custom_fee.fractional_fees jsonb column
   */
  constructor(fractionalFee) {
    super(fractionalFee);
    this.denominator = fractionalFee.denominator;
    this.maximumAmount = fractionalFee.maximum_amount;
    this.minimumAmount = fractionalFee.minimum_amount;
    this.netOfTransfers = fractionalFee.net_of_transfers;
    this.numerator = fractionalFee.numerator;
  }

  static DENOMINATOR = `denominator`;
  static MAXIMUM_AMOUNT = `maximum_amount`;
  static MINIMUM_AMOUNT = `minimum_amount`;
  static NET_OF_TRANSFERS = `net_of_transfers`;
  static NUMERATOR = `numerator`;
}

export default FractionalFee;
