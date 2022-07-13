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

import AssessedCustomFee from '../../model/assessedCustomFee';
import AssessedCustomFeeViewModel from '../../viewmodel/assessedCustomFeeViewModel';

describe('AssessedCustomFeeViewModel', () => {
  const effectivePayersTestSpecs = [
    {
      name: 'empty effective payers',
      payersModel: [],
      expectedPayers: [],
    },
    {
      name: 'null empty effective payers',
      expectedPayers: [],
    },
    {
      name: 'non-empty effective payers',
      payersModel: [9000, 9001, 9002],
      expectedPayers: ['0.0.9000', '0.0.9001', '0.0.9002'],
    },
  ];

  effectivePayersTestSpecs.forEach((testSpec) => {
    test(`fee charged in hbar with ${testSpec.name}`, () => {
      const model = new AssessedCustomFee({
        amount: 13,
        collector_account_id: 8901,
        consensus_timestamp: '1',
        effective_payer_account_ids: testSpec.payersModel,
      });
      const expected = {
        amount: 13,
        collector_account_id: '0.0.8901',
        effective_payer_account_ids: testSpec.expectedPayers,
        token_id: null,
      };

      expect(new AssessedCustomFeeViewModel(model)).toEqual(expected);
    });

    test(`fee charged in token with ${testSpec.name}`, () => {
      const model = new AssessedCustomFee({
        amount: 13,
        collector_account_id: 8901,
        consensus_timestamp: '1',
        effective_payer_account_ids: testSpec.payersModel,
        token_id: 10013,
      });
      const expected = {
        amount: 13,
        collector_account_id: '0.0.8901',
        effective_payer_account_ids: testSpec.expectedPayers,
        token_id: '0.0.10013',
      };

      expect(new AssessedCustomFeeViewModel(model)).toEqual(expected);
    });
  });
});
