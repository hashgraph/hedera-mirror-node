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

const accountIdFilter = 'account_id = $1';
const tokenIdFilter = 'token_id = $2';

describe('extractNftsQuery', () => {
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
            value: '1000',
          },
        ],
        accountId: 3,
      },
      expected: {
        ...defaultExpected,
        conditions: [accountIdFilter, tokenIdFilter],
        params: [3, '1000'],
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
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.eq,
            value: '1',
          },
        ],
        accountId: 4,
      },
      expected: {
        ...defaultExpected,
        conditions: [accountIdFilter, tokenIdFilter, 'serial_number = $3'],
        params: [4, '1001', '1'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(accountCtrl.extractNftsQuery(spec.input.filters, spec.input.accountId)).toEqual(spec.expected);
    });
  });
});

describe('extractNftMultiUnionQuery', () => {
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
      },
      expected: {
        lower: {
          ...defaultExpected,
          limit: 20,
          params: [1],
        },
        inner: null,
        upper: null,
        order: constants.orderFilterValues.DESC,
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
        accountId: 2,
      },
      expected: {
        lower: {
          ...defaultExpected,
          order: constants.orderFilterValues.ASC,
          params: [2],
        },
        inner: null,
        upper: null,
        order: constants.orderFilterValues.ASC,
        limit: defaultLimit,
      },
    },
    {
      name: 'token.id single',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1001',
          },
        ],
        accountId: 3,
      },
      expected: {
        lower: {
          ...defaultExpected,
          conditions: [accountIdFilter, tokenIdFilter],
          params: [3, '1001'],
        },
        inner: {
          ...defaultExpected,
          conditions: ['account_id = $4', 'token_id > $5'],
          params: [3, '1001'],
        },
        upper: null,
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(accountCtrl.extractNftMultiUnionQuery(spec.input.filters, spec.input.accountId)).toEqual(spec.expected);
    });
  });
});

describe('extractNftMultiUnionQuery range bounds', () => {
  const defaultExpected = {
    conditions: [accountIdFilter],
    params: [],
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
  };

  const specs = [
    {
      name: 'token inner only',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lt,
            value: '777',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gt,
            value: '111',
          },
        ],
        accountId: 3,
      },
      expected: {
        lower: null,
        inner: {
          ...defaultExpected,
          conditions: [accountIdFilter, 'token_id > $2', 'token_id < $3'],
          params: [3, '111', '777'],
        },
        upper: null,
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: 'token and serialnumber lower and inner',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.gt,
            value: '1',
          },
        ],
        accountId: 3,
      },
      expected: {
        lower: {
          ...defaultExpected,
          conditions: [accountIdFilter, tokenIdFilter, 'serial_number > $3'],
          params: [3, '1000', '1'],
        },
        inner: {
          ...defaultExpected,
          conditions: ['account_id = $5', 'token_id > $6'],
          params: [3, '1000'],
        },
        upper: null,
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: 'token and serialnumber inner and upper',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lte,
            value: '1000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lt,
            value: '10',
          },
        ],
        accountId: 3,
      },
      expected: {
        lower: null,
        inner: {
          ...defaultExpected,
          conditions: [accountIdFilter, 'token_id < $2'],
          params: [3, '1000'],
        },
        upper: {
          ...defaultExpected,
          conditions: ['account_id = $4', 'token_id = $5', 'serial_number < $6'],
          params: [3, '1000', '10'],
        },
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: 'token and serialnumber lower, inner and upper',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.gt,
            value: '10',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lte,
            value: '2000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lt,
            value: '20',
          },
        ],
        accountId: 4,
      },
      expected: {
        lower: {
          ...defaultExpected,
          conditions: [accountIdFilter, tokenIdFilter, 'serial_number > $3'],
          params: [4, '1000', '10'],
        },
        inner: {
          ...defaultExpected,
          conditions: ['account_id = $5', 'token_id > $6', 'token_id < $7'],
          params: [4, '1000', '2000'],
        },
        upper: {
          ...defaultExpected,
          conditions: ['account_id = $9', 'token_id = $10', 'serial_number < $11'],
          params: [4, '2000', '20'],
        },
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name} bound`, () => {
      expect(accountCtrl.extractNftMultiUnionQuery(spec.input.filters, spec.input.accountId)).toEqual(spec.expected);
    });
  });
});

describe('extractNftMultiUnionQuery throws', () => {
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
          {
            key: constants.filterKeys.LIMIT,
            operator: utils.opsMap.eq,
            value: 30,
          },
        ],
        accountId: 1,
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
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.DESC,
          },
        ],
        accountId: 2,
      },
    },
    {
      name: 'token.id ne',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.ne,
            value: '1001',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'token.id eq repeated',
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
        ],
        accountId: 3,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(() =>
        accountCtrl.extractNftMultiUnionQuery(spec.input.filters, spec.input.accountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('extractNftMultiUnionQuery range bound throws', () => {
  const specs = [
    {
      name: 'token.id gt(e) repeated',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gt,
            value: '1001',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1002',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'token.id lt(e) repeated',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lt,
            value: '1001',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lte,
            value: '1002',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'serialnumber gt with no token.id gt(e)',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lte,
            value: '1000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.gt,
            value: '1',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'serialnumber lt with no token.id lt(e)',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.eq,
            value: '1000',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lt,
            value: '1',
          },
        ],
        accountId: 3,
      },
    },
    {
      name: 'token and serialnumber repeated range',
      input: {
        filters: [
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.gt,
            value: '10',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lte,
            value: '2000',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lt,
            value: '20',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.gte,
            value: '1001',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.gt,
            value: '11',
          },
          {
            key: constants.filterKeys.TOKEN_ID,
            operator: utils.opsMap.lte,
            value: '2001',
          },
          {
            key: constants.filterKeys.SERIAL_NUMBER,
            operator: utils.opsMap.lt,
            value: '21',
          },
        ],
        accountId: 4,
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name} bound`, () => {
      expect(() =>
        accountCtrl.extractNftMultiUnionQuery(spec.input.filters, spec.input.accountId)
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
      expect(accountCtrl.extractCryptoAllowancesQuery(spec.input.filters, spec.input.accountId)).toEqual(spec.expected);
    });
  });
});
