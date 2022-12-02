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

import * as constants from '../../constants';
import {NetworkController} from '../../controllers';
import {FileData} from '../../model';
import networkCtrl from '../../controllers/networkController';
import * as utils from '../../utils';

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

describe('convertToCurrencyFormat', () => {
  const specs = [
    {
      tinycoins: '1234567890000',
      currencyFormat: 'BOTH',
      expected: '12345.67890000',
    },
    {
      tinycoins: '1234567890000',
      currencyFormat: null,
      expected: '12345.67890000',
    },
    {
      tinycoins: '0',
      currencyFormat: 'BOTH',
      expected: '0.00000000',
    },
    {
      tinycoins: '42',
      currencyFormat: 'BOTH',
      expected: '0.00000042',
    },
    {
      tinycoins: '987654321098765432',
      currencyFormat: 'BOTH',
      expected: '9876543210.98765432',
    },
    {
      tinycoins: '5000000000000000000',
      currencyFormat: null,
      expected: '50000000000.00000000',
    },
    {
      tinycoins: '1234567890000',
      currencyFormat: 'HBARS',
      expected: '12345',
    },
    {
      tinycoins: '0',
      currencyFormat: 'HBARS',
      expected: '0',
    },
    {
      tinycoins: '42',
      currencyFormat: 'HBARS',
      expected: '0',
    },
    {
      tinycoins: '987654321098765432',
      currencyFormat: 'HBARS',
      expected: '9876543210',
    },
    {
      tinycoins: '5000000000000000000',
      currencyFormat: 'HBARS',
      expected: '50000000000',
    },
    {
      tinycoins: '1234567890123',
      currencyFormat: 'TINYBARS',
      expected: '1234567890123',
    },
    {
      tinycoins: '0',
      currencyFormat: 'TINYBARS',
      expected: '0',
    },
    {
      tinycoins: '42',
      currencyFormat: 'TINYBARS',
      expected: '42',
    },
    {
      tinycoins: '987654321098765432',
      currencyFormat: 'TINYBARS',
      expected: '987654321098765432',
    },
    {
      tinycoins: '5000000000000000000',
      currencyFormat: 'TINYBARS',
      expected: '5000000000000000000',
    },
    {
      tinycoins: '',
      currencyFormat: undefined,
      expected: '0.00000000',
    },
    {
      tinycoins: undefined,
      currencyFormat: undefined,
      expected: '0.00000000',
    },
    {
      tinycoins: undefined,
      currencyFormat: 'HBARS',
      expected: '0',
    },
    {
      tinycoins: undefined,
      currencyFormat: 'TINYBARS',
      expected: '0',
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.currencyFormat}-${spec.tinycoins}`, () => {
      expect(networkCtrl.convertToCurrencyFormat(spec.tinycoins, spec.currencyFormat)).toEqual(spec.expected);
    });
  });
});
