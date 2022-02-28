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
      name: 'repeated token range filter',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gt,
            value: '1',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '3',
          },
        ],
        accountId: 4,
        paramSupportMap: accountCtrl.nftsByAccountIdParamSupportMap,
      },
    },
    {
      name: 'repeated serial number range filter',
      input: {
        filters: [
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lt,
            value: '1',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lte,
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

describe('getFilterKeyOpString', () => {
  const specs = [
    {
      name: 'limit key',
      input: {
        filter: {
          key: constants.filterKeys.LIMIT,
          operator: utils.opsMap.eq,
          value: 20,
        },
        merge: false,
      },
      expected: 'limit-=',
    },
    {
      name: 'limit key w merge',
      input: {
        filter: {
          key: constants.filterKeys.LIMIT,
          operator: utils.opsMap.eq,
          value: 20,
        },
        merge: true,
      },
      expected: 'limit-=',
    },
    {
      name: 'order key w asc',
      input: {
        filter: {
          key: constants.filterKeys.ORDER,
          operator: utils.opsMap.eq,
          value: constants.orderFilterValues.ASC,
        },
        merge: false,
      },
      expected: 'order-=',
    },
    {
      name: 'order key w desc merge',
      input: {
        filter: {
          key: constants.filterKeys.ORDER,
          operator: utils.opsMap.eq,
          value: constants.orderFilterValues.DESC,
        },
        merge: true,
      },
      expected: 'order-=',
    },
    {
      name: 'token.id key w gt',
      input: {
        filter: {
          key: constants.filterKeys.TOKEN_ID,
          operator: utils.opsMap.gt,
          value: '1001',
        },
        merge: false,
      },
      expected: 'token.id->',
    },
    {
      name: 'token.id gte',
      input: {
        filter: {
          key: constants.filterKeys.TOKEN_ID,
          operator: utils.opsMap.gte,
          value: '1001',
        },
        merge: false,
      },
      expected: 'token.id->=',
    },
    {
      name: 'token.id key w gte merge',
      input: {
        filter: {
          key: constants.filterKeys.TOKEN_ID,
          operator: utils.opsMap.gte,
          value: '1001',
        },
        merge: true,
      },
      expected: 'token.id->',
    },
    {
      name: 'serialnumber key w lt',
      input: {
        filter: {
          key: constants.filterKeys.SERIAL_NUMBER,
          operator: utils.opsMap.lt,
          value: '1001',
        },
        merge: false,
      },
      expected: 'serialnumber-<',
    },
    {
      name: 'serialnumber key w lte',
      input: {
        filter: {
          key: constants.filterKeys.SERIAL_NUMBER,
          operator: utils.opsMap.lte,
          value: '1001',
        },
        merge: false,
      },
      expected: 'serialnumber-<=',
    },
    {
      name: 'serialnumber key w lte merge',
      input: {
        filter: {
          key: constants.filterKeys.SERIAL_NUMBER,
          operator: utils.opsMap.lte,
          value: '1001',
        },
        merge: true,
      },
      expected: 'serialnumber-<',
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(accountCtrl.getFilterKeyOpString(spec.input.filter, spec.input.merge)).toEqual(spec.expected);
    });
  });
});

describe('validateSingleFilterKeyOccurence not throw', () => {
  const specs = [
    {
      name: 'token.id gt single occurence',
      input: {
        filterMap: {
          'token.id-<': true,
        },
        filter: {
          key: constants.filterKeys.TOKEN_ID,
          operator: utils.opsMap.gt,
          value: 20,
        },
      },
    },
    {
      name: 'token.id gte single occurence',
      input: {
        filterMap: {
          'token.id-<=': true,
        },
        filter: {
          key: constants.filterKeys.TOKEN_ID,
          operator: utils.opsMap.gte,
          value: 20,
        },
      },
    },
    {
      name: 'serialnumber lt single occurence',
      input: {
        filterMap: {
          'serialnumber->': true,
        },
        filter: {
          key: constants.filterKeys.SERIAL_NUMBER,
          operator: utils.opsMap.lt,
          value: 20,
        },
      },
    },
    {
      name: 'serialnumber lte single occurence',
      input: {
        filterMap: {
          'serialnumber->=': true,
        },
        filter: {
          key: constants.filterKeys.SERIAL_NUMBER,
          operator: utils.opsMap.lte,
          value: 20,
        },
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() => accountCtrl.validateSingleFilterKeyOccurence(spec.input.filterMap, spec.input.filter)).not.toThrow();
    });
  });
});

describe('validateSingleFilterKeyOccurence throw', () => {
  const specs = [
    {
      name: 'token.id multiple occurence',
      input: {
        filterMap: {
          'token.id->': true,
        },
        filter: {
          key: constants.filterKeys.TOKEN_ID,
          operator: utils.opsMap.gt,
          value: 20,
        },
      },
    },
    {
      name: 'serialnumber multiple occurence',
      input: {
        filterMap: {
          'serialnumber-<': true,
        },
        filter: {
          key: constants.filterKeys.SERIAL_NUMBER,
          operator: utils.opsMap.lt,
          value: 20,
        },
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() =>
        accountCtrl.validateSingleFilterKeyOccurence(spec.input.filterMap, spec.input.filter)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
