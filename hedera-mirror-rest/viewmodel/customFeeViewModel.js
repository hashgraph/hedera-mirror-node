/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import EntityId from '../entityId';
import _ from 'lodash';

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
    this.fixed_fees = _.isNil(customFee.fixedFees) ? [] : customFee.fixedFees.map((f) => this._parseFixedFee(f));
    this.fractional_fees = _.isNil(customFee.fractionalFees)
      ? []
      : customFee.fractionalFees?.map((f) => this._parseFractionalFee(f, customFee.tokenId));
    this.royalty_fees = _.isNil(customFee.royaltyFees)
      ? []
      : customFee.royaltyFees?.map((f) => this._parseRoyaltyFee(f));
  }

  _parseFixedFee(fixedFee) {
    return {
      all_collectors_are_exempt: fixedFee.allCollectorsAreExempt ?? false,
      amount: Number(fixedFee.amount),
      collector_account_id: EntityId.parse(fixedFee.collectorAccountId, {isNullable: true}).toString(),
      denominating_token_id: EntityId.parse(fixedFee.denominatingTokenId, {isNullable: true}).toString(),
    };
  }

  _parseFractionalFee(fractionalFee, tokenId) {
    return {
      all_collectors_are_exempt: fractionalFee.allCollectorsAreExempt ?? false,
      amount: {
        numerator: Number(fractionalFee.amount),
        denominator: Number(fractionalFee.amountDenominator),
      },
      collector_account_id: EntityId.parse(fractionalFee.collectorAccountId, {isNullable: true}).toString(),
      denominating_token_id: EntityId.parse(tokenId, {isNullable: true}).toString(),
      maximum: fractionalFee.maximumAmount ? Number(fractionalFee.maximumAmount) : undefined,
      minimum: Number(fractionalFee.minimumAmount),
      net_of_transfers: fractionalFee.netOfTransfers ?? false,
    };
  }

  _parseRoyaltyFee(royaltyFee) {
    const viewModel = {
      all_collectors_are_exempt: royaltyFee.fallbackFee?.allCollectorsAreExempt ?? false,
      amount: {
        denominator: Number(royaltyFee.royaltyDenominator),
        numerator: Number(royaltyFee.royaltyNumerator),
      },
      collector_account_id: EntityId.parse(royaltyFee.fallbackFee?.collectorAccountId, {isNullable: true}).toString(),
    };
    if (royaltyFee.fallbackFee?.amount) {
      viewModel.fallback_fee = {
        amount: Number(royaltyFee.fallbackFee.amount),
        denominating_token_id: EntityId.parse(royaltyFee.fallbackFee.denominatingTokenId, {
          isNullable: true,
        }).toString(),
      };
    }

    return viewModel;
  }
}

export default CustomFeeViewModel;
