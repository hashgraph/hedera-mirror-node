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
const accountCtrl = require('../../controllers/accountController');
const utils = require('../../utils');

describe('extractNftsQuery', () => {
  const accountIdFilter = 'account_id = $1';
  const defaultExpected = {
    conditions: [accountIdFilter],
    params: [],
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
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
      expected: {
        ...defaultExpected,
        limit: 20,
        params: [1],
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
        accountId: 2,
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.ASC,
        conditions: [accountIdFilter],
        params: [2],
      },
    },
    {
      name: 'token.id',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '1002',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
        accountId: 3,
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
      expected: {
        ...defaultExpected,
        conditions: [accountIdFilter, 'token_id > $2', 'token_id in ($3,$4)'],
        params: [3, '1000', '1001', '1002'],
      },
    },
    {
      name: 'token and serialnumber',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '1002',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.eq,
            value: '1',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.eq,
            value: '2',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.gt,
            value: '3',
          },
        ],
        accountId: 4,
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
      expected: {
        ...defaultExpected,
        conditions: [accountIdFilter, 'serial_number > $2', 'token_id in ($3,$4)', 'serial_number in ($5,$6)'],
        params: [4, '3', '1001', '1002', '1', '2'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(
        accountCtrl.extractNftsQuery(
          spec.input.filters,
          spec.input.accountId,
          accountCtrl.nftsByAccountIdParamSupportMap
        )
      ).toEqual(spec.expected);
    });
  });
});

describe('extractNftsQuery throws', () => {
  const specs = [
    {
      name: 'bad token id with serialnumber',
      input: {
        filters: [
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.eq,
            value: '1',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gt,
            value: '3',
          },
        ],
        accountId: 4,
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
    },
    {
      name: 'serialnumber with missing tokenId',
      input: {
        filters: [
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.eq,
            value: '1',
          },
        ],
        accountId: 4,
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() =>
        accountCtrl.extractNftsQuery(
          spec.input.filters,
          spec.input.accountId,
          accountCtrl.nftsByAccountIdParamSupportMap
        )
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
