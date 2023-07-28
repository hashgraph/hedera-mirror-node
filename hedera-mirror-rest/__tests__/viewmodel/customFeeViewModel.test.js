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

import {CustomFee} from '../../model';
import {CustomFeeViewModel} from '../../viewmodel';

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
        fixed_fees: [
          {
            all_collectors_are_exempt: true,
            amount: '15',
            collector_account_id: 8901,
            denominating_token_id: testSpec.dbDenominatingTokenId,
          },
        ],
      });

      const expected = {
        fixed_fees: [
          {
            all_collectors_are_exempt: true,
            amount: 15,
            collector_account_id: '0.0.8901',
            denominating_token_id: testSpec.expectedDenominatingTokenId,
          },
        ],
        fractional_fees: [],
        royalty_fees: [],
      };

      const actual = new CustomFeeViewModel(input);
      expect(actual).toEqual(expected);
    });
  });

  test('fractional fee', () => {
    const input = new CustomFee({
      fractional_fees: [
        {
          all_collectors_are_exempt: true,
          amount: 15,
          amount_denominator: 31,
          collector_account_id: 8901,
          maximum_amount: 101,
          minimum_amount: 37,
          net_of_transfers: false,
        },
      ],
      token_id: 10015,
    });

    const expected = {
      fixed_fees: [],
      fractional_fees: [
        {
          all_collectors_are_exempt: true,
          amount: {
            denominator: 31,
            numerator: 15,
          },
          collector_account_id: '0.0.8901',
          denominating_token_id: '0.0.10015',
          maximum: 101,
          minimum: 37,
          net_of_transfers: false,
        },
      ],
      royalty_fees: [],
    };

    const actual = new CustomFeeViewModel(input);
    expect(actual).toEqual(expected);
  });

  test('fractional fee no maximum', () => {
    const input = new CustomFee({
      fractional_fees: [
        {
          all_collectors_are_exempt: false,
          amount: 15,
          amount_denominator: 31,
          collector_account_id: 8901,
          minimum_amount: 37,
          net_of_transfers: true,
        },
      ],
      token_id: 10015,
    });

    const expected = {
      fixed_fees: [],
      fractional_fees: [
        {
          all_collectors_are_exempt: false,
          amount: {
            denominator: 31,
            numerator: 15,
          },
          collector_account_id: '0.0.8901',
          denominating_token_id: '0.0.10015',
          minimum: 37,
          net_of_transfers: true,
        },
      ],
      royalty_fees: [],
    };

    const actual = new CustomFeeViewModel(input);
    expect(actual).toEqual(expected);
  });

  test('royalty fee without fallback', () => {
    const input = new CustomFee({
      royalty_fees: [
        {
          fallback_fee: {
            all_collectors_are_exempt: true,
            collector_account_id: 8901,
          },
          royalty_denominator: 31,
          royalty_numerator: 15,
        },
      ],
    });

    const expected = {
      fixed_fees: [],
      fractional_fees: [],
      royalty_fees: [
        {
          all_collectors_are_exempt: true,
          amount: {
            denominator: 31,
            numerator: 15,
          },
          collector_account_id: '0.0.8901',
        },
      ],
    };

    const actual = new CustomFeeViewModel(input);
    expect(actual).toEqual(expected);
  });

  fixedFeeTestSpecs.forEach((testSpec) => {
    test(`royalty fee with fallback in ${testSpec.name}`, () => {
      const input = new CustomFee({
        royalty_fees: [
          {
            fallback_fee: {
              amount: 11,
              all_collectors_are_exempt: false,
              collector_account_id: 8901,
              denominating_token_id: testSpec.dbDenominatingTokenId,
            },
            royalty_denominator: 31,
            royalty_numerator: 15,
          },
        ],
      });

      const expected = {
        fixed_fees: [],
        fractional_fees: [],
        royalty_fees: [
          {
            all_collectors_are_exempt: false,
            amount: {
              denominator: 31,
              numerator: 15,
            },
            collector_account_id: '0.0.8901',
            fallback_fee: {
              amount: 11,
              denominating_token_id: testSpec.expectedDenominatingTokenId,
            },
          },
        ],
      };

      const actual = new CustomFeeViewModel(input);
      expect(actual).toEqual(expected);
    });
  });

  test('empty fee', () => {
    const input = new CustomFee({});

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual({
      fixed_fees: [],
      fractional_fees: [],
      royalty_fees: [],
    });
  });
});
