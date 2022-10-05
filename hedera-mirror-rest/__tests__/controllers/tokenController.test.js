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

import {getResponseLimit} from '../../config';
import * as constants from '../../constants';
import * as utils from '../../utils';
import tokenController from '../../controllers/tokenController';

const {default: defaultLimit} = getResponseLimit();
const {
  filterKeys: {TOKEN_ID, ORDER, LIMIT},
} = constants;
const {
  opsMap: {eq, gt, gte, lt, lte},
} = utils;

const ownerAccountId = BigInt(98);

const tokenIdEqFilter = {key: TOKEN_ID, operator: eq, value: 100};
const tokenIdGtFilter = {key: TOKEN_ID, operator: gt, value: 101};
const tokenIdGteFilter = {key: TOKEN_ID, operator: gte, value: 102};
const tokenIdLtFilter = {key: TOKEN_ID, operator: lt, value: 150};
const tokenIdLteFilter = {key: TOKEN_ID, operator: lte, value: 151};

describe('extractTokenRelationshipQuery', () => {
  const defaultExpected = {
    conditions: [],
    ownerAccountId: ownerAccountId,
    order: constants.orderFilterValues.DESC,
    limit: 25,
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
      },
      expected: {
        ...defaultExpected,
        limit: 20,
      },
    },
    {
      name: 'order desc',
      input: {
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.DESC,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.DESC,
        conditions: [],
      },
    },
    {
      name: 'token.id',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '10',
          },
        ],
      },
      expected: {
        ...defaultExpected,
        conditions: ['token_id  =  10'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(tokenController.extractTokensRelationshipQuery(spec.input.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});
