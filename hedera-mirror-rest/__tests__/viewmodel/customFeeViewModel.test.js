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

const CustomFee = require('../../model/customFee');
const CustomFeeViewModel = require('../../viewmodel/customFeeViewModel');

describe('CustomFeeViewModel', () => {
  test('fixed fee in HBAR', () => {
    const input = new CustomFee({
      amount: 15,
      collector_account_id: 8901,
      created_timestamp: '10',
      token_id: 10015,
    });
    const expected = {
      amount: 15,
      collector_account_id: '0.0.8901',
      denominating_token_id: null,
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFractionalFee()).toBeFalsy();
  });

  test('fixed fee in token', () => {
    const input = new CustomFee({
      amount: 15,
      collector_account_id: 8901,
      created_timestamp: '10',
      denominating_token_id: 10012,
      token_id: 10015,
    });
    const expected = {
      amount: 15,
      collector_account_id: '0.0.8901',
      denominating_token_id: '0.0.10012',
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFractionalFee()).toBeFalsy();
  });

  test('fractional fee', () => {
    const input = new CustomFee({
      amount: 15,
      amount_denominator: 31,
      collector_account_id: 8901,
      created_timestamp: '10',
      maximum_amount: 101,
      minimum_amount: 37,
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
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFractionalFee()).toBeTruthy();
  });

  test('fractional fee no maximum', () => {
    const input = new CustomFee({
      amount: 15,
      amount_denominator: 31,
      collector_account_id: 8901,
      created_timestamp: '10',
      minimum_amount: 37,
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
    };

    const actual = new CustomFeeViewModel(input);

    expect(actual).toEqual(expected);
    expect(actual.hasFee()).toBeTruthy();
    expect(actual.isFractionalFee()).toBeTruthy();
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
