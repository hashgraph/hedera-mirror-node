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

'use strict';

import CustomFee from '../../model/customFee';
import CustomFeeViewModel from '../../viewmodel/customFeeViewModel';

describe('CustomFeeViewModel', () => {
  const fixedFeeTestSpecs = [
    {
      name: 'HBAR',
      expectedDenominatingTokenId: null,
    },
    {
      name: 'token',
      dbDenominatingTokenId: 10012,
      expectedDenominatingTokenId: '0.0.10012',
    },
  ];

  fixedFeeTestSpecs.forEach((testSpec) => {
    test(`fixed fee in ${testSpec.name}`, () => {
      const input = new CustomFee({
        amount: 15,
        collector_account_id: 8901,
        created_timestamp: '10',
        denominating_token_id: testSpec.dbDenominatingTokenId,
        token_id: 10015,
      });
      const expected = {
        amount: 15,
        collector_account_id: '0.0.8901',
        denominating_token_id: testSpec.expectedDenominatingTokenId,
      };

      const actual = new CustomFeeViewModel(input);

      expect(actual).toEqual(expected);
      expect(actual.hasFee()).toBeTruthy();
      expect(actual.isFixedFee()).toBeTruthy();
      expect(actual.isFractionalFee()).toBeFalsy();
      expect(actual.isRoyaltyFee()).toBeFalsy();
    });
  });

  test('fractional fee', () => {
    const input = new CustomFee({
      amount: 15,
      amount_denominator: 31,
      collector_account_id: 8901,
      created_timestamp: '10',
      maximum_amount: 101,
      minimum_amount: 37,
      net_of_transfers: false,
      token_id: 10015,
    });
    const expected = {
      amount: {
        numerator: 15,
        denominator: 31,
      },
      collector_account_id: '0.0.8901',
      denominating_token_id: '0.0.10015',
      maximum: 101,
      minimum: 37,
      net_of_transfers: false,
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFixedFee()).toBeFalsy();
    expect(actual.isFractionalFee()).toBeTruthy();
    expect(actual.isRoyaltyFee()).toBeFalsy();
  });

  test('fractional fee no maximum', () => {
    const input = new CustomFee({
      amount: 15,
      amount_denominator: 31,
      collector_account_id: 8901,
      created_timestamp: '10',
      minimum_amount: 37,
      net_of_transfers: true,
      token_id: 10015,
    });
    const expected = {
      amount: {
        numerator: 15,
        denominator: 31,
      },
      collector_account_id: '0.0.8901',
      denominating_token_id: '0.0.10015',
      minimum: 37,
      net_of_transfers: true,
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFixedFee()).toBeFalsy();
    expect(actual.isFractionalFee()).toBeTruthy();
    expect(actual.isRoyaltyFee()).toBeFalsy();
  });

  test('royalty fee without fallback', () => {
    const input = new CustomFee({
      collector_account_id: 8901,
      created_timestamp: '10',
      royalty_denominator: 31,
      royalty_numerator: 15,
    });
    const expected = {
      amount: {
        numerator: 15,
        denominator: 31,
      },
      collector_account_id: '0.0.8901',
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFixedFee()).toBeFalsy();
    expect(actual.isFractionalFee()).toBeFalsy();
    expect(actual.isRoyaltyFee()).toBeTruthy();
  });

  fixedFeeTestSpecs.forEach((testSpec) => {
    test(`royalty fee with fallback in ${testSpec.name}`, () => {
      const input = new CustomFee({
        amount: 11,
        collector_account_id: 8901,
        created_timestamp: '10',
        denominating_token_id: testSpec.dbDenominatingTokenId,
        royalty_denominator: 31,
        royalty_numerator: 15,
      });
      const expected = {
        amount: {
          numerator: 15,
          denominator: 31,
        },
        collector_account_id: '0.0.8901',
        fallback_fee: {
          amount: 11,
          denominating_token_id: testSpec.expectedDenominatingTokenId,
        },
      };

      const actual = new CustomFeeViewModel(input);

      expect(actual).toEqual(expected);
      expect(actual.hasFee()).toBeTruthy();
      expect(actual.isFixedFee()).toBeFalsy();
      expect(actual.isFractionalFee()).toBeFalsy();
      expect(actual.isRoyaltyFee()).toBeTruthy();
    });
  });

  test('empty fee', () => {
    const input = new CustomFee({
      created_timestamp: '10',
      token_id: 10015,
    });

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual({});
    expect(actual.hasFee()).toBeFalsy();
  });
});
