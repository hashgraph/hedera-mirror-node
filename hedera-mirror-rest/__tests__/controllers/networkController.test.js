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

import constants from '../../constants';
import {NetworkController} from '../../controllers';
import {FileData} from '../../model';
import networkCtrl from '../../controllers/networkController';
import utils from '../../utils';

describe('extractNetworkNodesQuery', () => {
  const defaultExpected = {
    conditions: [],
    params: [],
    order: constants.orderFilterValues.ASC,
    limit: 10,
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

describe('validateExtractNetworkNodesQuery throw', () => {
  const specs = [
    {
      name: 'file.id ne',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.ne,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id gt',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id gte',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id lte',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.lt,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'file.id lte',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.lte,
            value: '1000',
          },
        ],
      },
    },
    {
      name: 'multi file.id eq',
      input: {
        filters: [
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.eq,
            value: '101',
          },
          {
            key: constants.filterKeys.FILE_ID,
            operator: utils.opsMap.eq,
            value: '102',
          },
        ],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() => NetworkController.extractNetworkNodesQuery(spec.input.filters)).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('extractFileDataQuery', () => {
  const defaultExpected = {
    filterQuery: {
      whereQuery: [],
    },
    order: constants.orderFilterValues.ASC,
  };

  const specs = [
    {
      name: 'no timestamp',
      input: {
        filters: [],
      },
      expected: {
        ...defaultExpected,
      },
    },
    {
      name: 'le timestamp',
      input: {
        filters: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.lt,
            value: 2,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        filterQuery: {
          whereQuery: [
            {
              query: `${FileData.CONSENSUS_TIMESTAMP}  < `,
              param: 2,
            },
          ],
        },
      },
    },
    {
      name: 'lte timestamp',
      input: {
        filters: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.lte,
            value: 2,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        filterQuery: {
          whereQuery: [
            {
              query: `${FileData.CONSENSUS_TIMESTAMP}  <= `,
              param: 2,
            },
          ],
        },
      },
    },
    {
      name: 'gt timestamp',
      input: {
        filters: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.gt,
            value: 2,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        filterQuery: {
          whereQuery: [
            {
              query: `${FileData.CONSENSUS_TIMESTAMP}  > `,
              param: 2,
            },
          ],
        },
      },
    },
    {
      name: 'gte timestamp',
      input: {
        filters: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.gte,
            value: 2,
          },
        ],
      },
      expected: {
        ...defaultExpected,
        filterQuery: {
          whereQuery: [
            {
              query: `${FileData.CONSENSUS_TIMESTAMP}  >= `,
              param: 2,
            },
          ],
        },
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(networkCtrl.extractFileDataQuery(spec.input.filters)).toEqual(spec.expected);
    });
  });
});
