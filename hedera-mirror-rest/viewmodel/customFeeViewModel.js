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

const EntityId = require('../entityId');

/**
 * Custom fee view model
 */
class CustomFeeViewModel {
  /**
   * Constructs custom fee view model
   *
   * @param {CustomFee} customFee
   */
  constructor(customFee) {
    if (!customFee.amount) {
      return;
    }

    if (customFee.amountDenominator) {
      // fractional fee
      this.amount = {
        numerator: customFee.amount,
        denominator: customFee.amountDenominator,
      };

      this.denominating_token_id = EntityId.fromEncodedId(
        customFee.denominatingTokenId || customFee.tokenId
      ).toString();
      this.maximum = customFee.maximumAmount || undefined;
      this.minimum = customFee.minimumAmount;
    } else {
      // fixed fee
      this.amount = customFee.amount;
      this.denominating_token_id = EntityId.fromEncodedId(customFee.denominatingTokenId, true).toString();
    }

    this.collector_account_id = EntityId.fromEncodedId(customFee.collectorAccountId, true).toString();
  }

  hasFee() {
    return !!this.amount;
  }

  isFractionalFee() {
    return !!this.amount.numerator;
  }
}

module.exports = CustomFeeViewModel;
