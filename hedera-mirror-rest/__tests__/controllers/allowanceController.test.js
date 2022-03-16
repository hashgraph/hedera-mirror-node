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

const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../../config');
const constants = require('../../constants');
const allowanceCtrl = require('../../controllers/allowanceController');
const utils = require('../../utils');

const ownerIdFilter = 'owner = $1';
const spenderIdFIlter = 'spender = $2';
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
      name: 'spender.id',
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
        conditions: [ownerIdFilter, spenderIdFIlter],
        params: [3, '1000'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(allowanceCtrl.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)).toEqual(
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
    {
      name: 'spender.id gt',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'spender.id gte',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'spender.id lte',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.lt,
            value: '1000',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'spender.id lte',
      input: {
        filters: [
          {
            key: constants.filterKeys.SPENDER_ID,
            operator: utils.opsMap.lte,
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
        allowanceCtrl.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
