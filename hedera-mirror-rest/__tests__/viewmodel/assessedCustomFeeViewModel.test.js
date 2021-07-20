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

const AssessedCustomFee = require('../../model/assessedCustomFee');
const AssessedCustomFeeViewModel = require('../../viewmodel/assessedCustomFeeViewModel');

describe('AssessedCustomFeeViewModel', () => {
  test('fee charged in hbar', () => {
    const model = new AssessedCustomFee({
      amount: 13,
      collector_account_id: 8901,
      consensus_timestamp: '1',
    });
    const expected = {
      amount: 13,
      collector_account_id: '0.0.8901',
      token_id: null,
    };

    expect(new AssessedCustomFeeViewModel(model)).toEqual(expected);
  });

  test('fee charged in token', () => {
    const model = new AssessedCustomFee({
      amount: 13,
      collector_account_id: 8901,
      consensus_timestamp: '1',
      token_id: 10013,
    });
    const expected = {
      amount: 13,
      collector_account_id: '0.0.8901',
      token_id: '0.0.10013',
    };

    expect(new AssessedCustomFeeViewModel(model)).toEqual(expected);
  });
});
