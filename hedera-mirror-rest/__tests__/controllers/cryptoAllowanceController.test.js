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

import {response} from '../../config';
import constants from '../../constants';
import {CryptoAllowanceController} from '../../controllers';
import utils from '../../utils';

const ownerIdFilter = 'owner = $1';
describe('extractCryptoAllowancesQuery', () => {
  const defaultExpected = {
    conditions: [ownerIdFilter],
    params: [1],
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
  };

  const specs = [
    {
      name: 'limit',
      input: {
        filters: [
          {
            key: constants.filterKeys.LIMIT,
            operator: utils.opsMap.eq,
            value: 20,
          },
        ],
        accountId: 1,
      },
      expected: {
        ...defaultExpected,
        limit: 20,
      },
    },
    {
      name: 'order asc',
      input: {
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.ASC,
          },
        ],
        accountId: 1,
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.ASC,
        conditions: [ownerIdFilter],
      },
    },
    {
      name: 'spender.id single',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [ownerIdFilter, 'spender in ($2)'],
        params: [3, '1000'],
      },
    },
    {
      name: 'spender.id multiple',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1002',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [ownerIdFilter, 'spender in ($2,$3,$4)'],
        params: [3, '1000', '1001', '1002'],
      },
    },
    {
      name: 'spender.id all allowed operators',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.gt,
            value: '200',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.gte,
            value: '202',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.lt,
            value: '3000',
          },
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.lte,
            value: '3005',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [
          ownerIdFilter,
          'spender > $2',
          'spender >= $3',
          'spender < $4',
          'spender <= $5',
          'spender in ($6)',
        ],
        params: [3, '200', '202', '3000', '3005', '1000'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(CryptoAllowanceController.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)).toEqual(
        spec.expected
      );
    });
  });
});

describe('validateExtractCryptoAllowancesQuery throw', () => {
  const specs = [
    {
      name: 'spender.id ne',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.ne,
            value: '1000',
          },
        ],
        accountId: 3,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() =>
        CryptoAllowanceController.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
