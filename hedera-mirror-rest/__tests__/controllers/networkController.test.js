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
const networkCtrl = require('../../controllers/networkController');
const utils = require('../../utils');

describe('extractNetworkNodesQuery', () => {
  const defaultExpected = {
    conditions: [],
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
      },
      expected: {
        ...defaultExpected,
        limit: 20,
        params: ['102'],
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
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.ASC,
        conditions: [],
        params: ['102'],
      },
    },
    {
      name: 'node.id',
      input: {
        filters: [
          {
            key: constants.filterKeys.NODE_ID,
            operator: utils.opsMap.eq,
            value: '10',
          },
        ],
      },
      expected: {
        ...defaultExpected,
        conditions: ['abe.node_id in ($2)'],
        params: ['102', '10'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(networkCtrl.extractNetworkNodesQuery(spec.input.filters)).toEqual(spec.expected);
    });
  });
});
