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
    this.fixed_fees = customFee.fixedFees?.map((f) => this._parseFixedFee(f)) ?? [];
    this.fractional_fees = customFee.fractionalFees?.map((f) => this._parseFractionalFee(f, customFee.tokenId)) ?? [];
    this.royalty_fees = customFee.royaltyFees?.map((f) => this._parseRoyaltyFee(f)) ?? [];
  }

  _parseFixedFee(fixedFee) {
    return {
      all_collectors_are_exempt: fixedFee.allCollectorsAreExempt ?? false,
      amount: fixedFee.amount,
      collector_account_id: EntityId.parse(fixedFee.collectorAccountId, {isNullable: true}).toString(),
      denominating_token_id: EntityId.parse(fixedFee.denominatingTokenId, {isNullable: true}).toString(),
    };
  }

  _parseFractionalFee(fractionalFee, tokenId) {
    return {
      all_collectors_are_exempt: fractionalFee.allCollectorsAreExempt ?? false,
      amount: {
        numerator: fractionalFee.amount,
        denominator: fractionalFee.amountDenominator,
      },
      collector_account_id: EntityId.parse(fractionalFee.collectorAccountId, {isNullable: true}).toString(),
      denominating_token_id: EntityId.parse(tokenId, {isNullable: true}).toString(),
      maximum: fractionalFee.maximumAmount,
      minimum: fractionalFee.minimumAmount,
      net_of_transfers: fractionalFee.netOfTransfers ?? false,
    };
  }

  _parseRoyaltyFee(royaltyFee) {
    const fallbackFee = royaltyFee.fallbackFee?.amount
      ? {
          amount: royaltyFee.fallbackFee.amount,
          denominating_token_id: EntityId.parse(royaltyFee.fallbackFee.denominatingTokenId, {
            isNullable: true,
          }).toString(),
        }
      : null;

    const viewModel = {
      all_collectors_are_exempt: royaltyFee.fallbackFee?.allCollectorsAreExempt ?? false,
      amount: {
        denominator: royaltyFee.royaltyDenominator,
        numerator: royaltyFee.royaltyNumerator,
      },
      collector_account_id: EntityId.parse(royaltyFee.fallbackFee?.collectorAccountId, {isNullable: true}).toString(),
    };

    return fallbackFee
      ? {
          ...viewModel,
          fallback_fee: fallbackFee,
        }
      : viewModel;
  }
}

export default CustomFeeViewModel;
