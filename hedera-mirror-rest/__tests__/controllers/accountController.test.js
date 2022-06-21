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
const utils = require('../../utils');

const {
  filterKeys: {SERIAL_NUMBER, SPENDER_ID, TOKEN_ID, ORDER, LIMIT},
} = constants;
const {
  opsMap: {eq, gt, gte, lt, lte, ne},
} = utils;

const Bound = require('../../controllers/bound');
const {AccountController} = require('../../controllers');

const ownerAccountId = BigInt(1);

const serialEqFilter = {key: SERIAL_NUMBER, operator: eq, value: 5};
const serialGtFilter = {key: SERIAL_NUMBER, operator: gt, value: 6};
const serialGteFilter = {key: SERIAL_NUMBER, operator: gte, value: 7};
const serialLtFilter = {key: SERIAL_NUMBER, operator: lt, value: 15};
const serialLteFilter = {key: SERIAL_NUMBER, operator: lte, value: 16};

const spenderEqFilter = {key: SPENDER_ID, operator: eq, value: 1000};
const spenderGtFilter = {key: SPENDER_ID, operator: gt, value: 2000};
const spenderGteFilter = {key: SPENDER_ID, operator: gte, value: 3000};
const spenderLtFilter = {key: SPENDER_ID, operator: lt, value: 4000};
const spenderLteFilter = {key: SPENDER_ID, operator: lte, value: 5000};
const spenderEqInFilter = {key: SPENDER_ID, operator: eq, value: 1050};
const spenderEqInFilter2 = {key: SPENDER_ID, operator: eq, value: 1100};

const tokenIdEqFilter = {key: TOKEN_ID, operator: eq, value: 100};
const tokenIdGtFilter = {key: TOKEN_ID, operator: gt, value: 101};
const tokenIdGteFilter = {key: TOKEN_ID, operator: gte, value: 102};
const tokenIdLtFilter = {key: TOKEN_ID, operator: lt, value: 150};
const tokenIdLteFilter = {key: TOKEN_ID, operator: lte, value: 151};

describe('extractNftMultiUnionQuery', () => {
  const defaultExpected = {
    bounds: {[TOKEN_ID]: new Bound(), [SERIAL_NUMBER]: new Bound()},
    lower: [],
    inner: [],
    upper: [],
    ownerAccountId,
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
    spenderIdFilters: [],
    spenderIdInFilters: [],
  };

  const specs = [
    {
      name: 'empty',
      filters: [],
      expected: defaultExpected,
    },
    {
      name: 'order asc',
      filters: [{key: ORDER, operator: eq, value: 'asc'}],
      expected: {...defaultExpected, order: 'asc'},
    },
    {
      name: 'limit',
      filters: [{key: LIMIT, operator: eq, value: 60}],
      expected: {...defaultExpected, limit: 60},
    },
    {
      name: 'token eq',
      filters: [tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdEqFilter],
      },
    },
    {
      name: 'token gt',
      filters: [tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdGtFilter],
      },
    },
    {
      name: 'token lt',
      filters: [tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({upper: tokenIdLtFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdLtFilter],
      },
    },
    {
      name: 'token gte',
      filters: [tokenIdGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdGteFilter],
      },
    },
    {
      name: 'token lte',
      filters: [tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({upper: tokenIdLteFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdLteFilter],
      },
    },
    {
      name: 'token lt and gt',
      filters: [tokenIdLtFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter, upper: tokenIdLtFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdGtFilter, tokenIdLtFilter],
      },
    },
    {
      name: 'token eq and serial eq',
      filters: [serialEqFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
          [SERIAL_NUMBER]: Bound.create({equal: serialEqFilter}),
        },
        lower: [tokenIdEqFilter, serialEqFilter],
      },
    },
    {
      name: 'token gt and serial eq',
      filters: [serialEqFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({equal: serialEqFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter}),
        },
        lower: [tokenIdGtFilter, serialEqFilter],
      },
    },
    {
      name: 'token lt and serial eq',
      filters: [serialEqFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({equal: serialEqFilter}),
          [TOKEN_ID]: Bound.create({upper: tokenIdLtFilter}),
        },
        lower: [tokenIdLtFilter, serialEqFilter],
      },
    },
    {
      name: 'token gt and lt, serial eq',
      filters: [serialEqFilter, tokenIdGtFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({equal: serialEqFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter, upper: tokenIdLtFilter}),
        },
        lower: [tokenIdGtFilter, tokenIdLtFilter, serialEqFilter],
      },
    },
    {
      name: 'token gte and lte, serial eq',
      filters: [serialEqFilter, tokenIdGteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({equal: serialEqFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter, upper: tokenIdLteFilter}),
        },
        lower: [tokenIdGteFilter, tokenIdLteFilter, serialEqFilter],
      },
    },
    {
      name: 'token gte and serial gte',
      filters: [serialGteFilter, tokenIdGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({lower: serialGteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter}),
        },
        lower: [{...tokenIdGteFilter, operator: eq}, serialGteFilter],
        inner: [{...tokenIdGtFilter, value: tokenIdGtFilter.value + 1}],
      },
    },
    {
      name: 'token gt and serial gte',
      filters: [serialGteFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({lower: serialGteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter}),
        },
        lower: [{...tokenIdGtFilter, operator: eq}, serialGteFilter],
        inner: [tokenIdGtFilter],
      },
    },
    {
      name: 'token lte and serial lte',
      filters: [serialLteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({upper: serialLteFilter}),
          [TOKEN_ID]: Bound.create({upper: tokenIdLteFilter}),
        },
        inner: [{...tokenIdLteFilter, operator: lt}],
        upper: [{...tokenIdLteFilter, operator: eq}, serialLteFilter],
      },
    },
    {
      name: 'token lt and serial lte',
      filters: [serialLteFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({upper: serialLteFilter}),
          [TOKEN_ID]: Bound.create({upper: tokenIdLtFilter}),
        },
        inner: [tokenIdLtFilter],
        upper: [{...tokenIdLtFilter, operator: eq}, serialLteFilter],
      },
    },
    {
      name: 'token gt and lt, serial gte and lte',
      filters: [serialGteFilter, serialLteFilter, tokenIdGtFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({lower: serialGteFilter, upper: serialLteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter, upper: tokenIdLtFilter}),
        },
        lower: [{...tokenIdGtFilter, operator: eq}, serialGteFilter],
        inner: [{...tokenIdGtFilter}, {...tokenIdLtFilter}],
        upper: [{...tokenIdLtFilter, operator: eq}, serialLteFilter],
      },
    },
    {
      name: 'token gte and lte, serial gte and lte',
      filters: [serialGteFilter, serialLteFilter, tokenIdGteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({lower: serialGteFilter, upper: serialLteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter, upper: tokenIdLteFilter}),
        },
        lower: [{...tokenIdGteFilter, operator: eq}, serialGteFilter],
        inner: [
          {...tokenIdGteFilter, operator: gt},
          {...tokenIdLteFilter, operator: lt},
        ],
        upper: [{...tokenIdLteFilter, operator: eq}, serialLteFilter],
      },
    },
    {
      name: 'token eq and serial lt',
      filters: [serialLtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
          [SERIAL_NUMBER]: Bound.create({upper: serialLtFilter}),
        },
        upper: [tokenIdEqFilter, serialLtFilter],
      },
    },
    {
      name: 'token eq and serial gt',
      filters: [serialGtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
          [SERIAL_NUMBER]: Bound.create({lower: serialGtFilter}),
        },
        lower: [tokenIdEqFilter, serialGtFilter],
      },
    },
    {
      name: 'spender eq le gte',
      filters: [spenderEqFilter, spenderEqInFilter, spenderEqInFilter2, spenderLtFilter, spenderGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: new Bound(),
          [SERIAL_NUMBER]: new Bound(),
        },
        spenderIdFilters: [spenderLtFilter, spenderGteFilter],
        spenderIdInFilters: [spenderEqFilter, spenderEqInFilter, spenderEqInFilter2],
      },
    },
    {
      name: 'token eq spender eq le gte',
      filters: [
        tokenIdEqFilter,
        spenderEqFilter,
        spenderEqInFilter,
        spenderEqInFilter2,
        spenderLtFilter,
        spenderGteFilter,
      ],
      expected: {
        ...defaultExpected,
        bounds: {
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
          [SERIAL_NUMBER]: new Bound(),
        },
        lower: [tokenIdEqFilter],
        spenderIdFilters: [spenderLtFilter, spenderGteFilter],
        spenderIdInFilters: [spenderEqFilter, spenderEqInFilter, spenderEqInFilter2],
      },
    },
    {
      name: 'token gte and lte, serial gte and lte, spender eq lte gt',
      filters: [
        serialGteFilter,
        serialLteFilter,
        tokenIdGteFilter,
        tokenIdLteFilter,
        spenderEqFilter,
        spenderEqInFilter,
        spenderLteFilter,
        spenderGtFilter,
      ],
      expected: {
        ...defaultExpected,
        bounds: {
          [SERIAL_NUMBER]: Bound.create({lower: serialGteFilter, upper: serialLteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter, upper: tokenIdLteFilter}),
        },
        lower: [{...tokenIdGteFilter, operator: eq}, serialGteFilter],
        inner: [
          {...tokenIdGteFilter, operator: gt},
          {...tokenIdLteFilter, operator: lt},
        ],
        upper: [{...tokenIdLteFilter, operator: eq}, serialLteFilter],
        spenderIdFilters: [spenderLteFilter, spenderGtFilter],
        spenderIdInFilters: [spenderEqFilter, spenderEqInFilter],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(AccountController.extractNftMultiUnionQuery(spec.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});

describe('extractTokenMultiUnionQuery throw', () => {
  const specs = [
    {
      name: 'no token.id',
      filters: [{key: SERIAL_NUMBER, operator: ne, value: 1}],
    },
    {
      name: 'serial number ne',
      filters: [{key: SERIAL_NUMBER, operator: ne, value: 1}],
    },
    {
      name: 'serial number range and equal',
      filters: [serialEqFilter, serialGtFilter, serialLtFilter],
    },
    {
      name: 'serial number multiple equal',
      filters: [serialEqFilter, serialEqFilter],
    },
    {
      name: 'serial number multiple lower bounds',
      filters: [serialGtFilter, serialGteFilter],
    },
    {
      name: 'serial number multiple upper bounds',
      filters: [serialLtFilter, serialLteFilter],
    },
    {
      name: 'token.id ne',
      filters: [{key: TOKEN_ID, operator: ne, value: 1}],
    },
    {
      name: 'token.id multiple equal',
      filters: [tokenIdEqFilter, tokenIdEqFilter],
    },
    {
      name: 'token.id multiple lower bounds',
      filters: [tokenIdGtFilter, tokenIdGteFilter],
    },
    {
      name: 'token.id multiple upper bounds',
      filters: [tokenIdLtFilter, tokenIdLteFilter],
    },
    {
      name: 'spender le lte',
      filters: [tokenIdEqFilter, spenderLtFilter, spenderLteFilter],
    },
    {
      name: 'spender ge gte',
      filters: [tokenIdEqFilter, spenderGtFilter, spenderGteFilter],
    },
    {
      name: 'spender ne',
      filters: [{key: SPENDER_ID, operator: ne, value: 1}],
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(() =>
        AccountController.extractNftMultiUnionQuery(spec.filters, ownerAccountId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});
