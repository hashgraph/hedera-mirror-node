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
const {controller, Bound} = require('../../controllers/tokenAllowanceController');
const utils = require('../../utils');

const {
  filterKeys: {SPENDER_ID, TOKEN_ID, ORDER, LIMIT},
} = constants;
const {
  opsMap: {eq, gt, gte, lt, lte, ne},
} = utils;

const ownerAccountId = BigInt(1);

const spenderEqFilter = {key: SPENDER_ID, operator: eq, value: 5};
const spenderGtFilter = {key: SPENDER_ID, operator: gt, value: 6};
const spenderGteFilter = {key: SPENDER_ID, operator: gte, value: 7};
const spenderLtFilter = {key: SPENDER_ID, operator: lt, value: 15};
const spenderLteFilter = {key: SPENDER_ID, operator: lte, value: 16};

const tokenIdEqFilter = {key: TOKEN_ID, operator: eq, value: 100};
const tokenIdGtFilter = {key: TOKEN_ID, operator: gt, value: 101};
const tokenIdGteFilter = {key: TOKEN_ID, operator: gte, value: 102};
const tokenIdLtFilter = {key: TOKEN_ID, operator: lt, value: 150};
const tokenIdLteFilter = {key: TOKEN_ID, operator: lte, value: 151};

describe('extractTokenMultiUnionQuery', () => {
  const defaultExpected = {
    bounds: {[SPENDER_ID]: new Bound(), [TOKEN_ID]: new Bound()},
    lower: [],
    inner: [],
    upper: [],
    ownerAccountId,
    order: constants.orderFilterValues.ASC,
    limit: defaultLimit,
  };

  const specs = [
    {
      name: 'empty',
      filters: [],
      expected: defaultExpected,
    },
    {
      name: 'order desc',
      filters: [{key: ORDER, operator: eq, value: 'desc'}],
      expected: {...defaultExpected, order: 'desc'},
    },
    {
      name: 'limit',
      filters: [{key: LIMIT, operator: eq, value: 60}],
      expected: {...defaultExpected, limit: 60},
    },
    {
      name: 'spender eq',
      filters: [spenderEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({equal: spenderEqFilter}),
          [TOKEN_ID]: new Bound(),
        },
        lower: [spenderEqFilter],
      },
    },
    {
      name: 'spender gt',
      filters: [spenderGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGtFilter}),
          [TOKEN_ID]: new Bound(),
        },
        lower: [spenderGtFilter],
      },
    },
    {
      name: 'spender gte',
      filters: [spenderGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter}),
          [TOKEN_ID]: new Bound(),
        },
        lower: [spenderGteFilter],
      },
    },
    {
      name: 'spender lt',
      filters: [spenderLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({upper: spenderLtFilter}),
          [TOKEN_ID]: new Bound(),
        },
        lower: [spenderLtFilter],
      },
    },
    {
      name: 'spender lte',
      filters: [spenderLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({upper: spenderLteFilter}),
          [TOKEN_ID]: new Bound(),
        },
        lower: [spenderLteFilter],
      },
    },
    {
      name: 'spender gt and lt',
      filters: [spenderGtFilter, spenderLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGtFilter, upper: spenderLtFilter}),
          [TOKEN_ID]: new Bound(),
        },
        lower: [spenderGtFilter, spenderLtFilter],
      },
    },
    {
      name: 'spender eq and token eq',
      filters: [spenderEqFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({equal: spenderEqFilter}),
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
        },
        lower: [spenderEqFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gt and token eq',
      filters: [spenderGtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGtFilter}),
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
        },
        lower: [spenderGtFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender lt and token eq',
      filters: [spenderLtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({upper: spenderLtFilter}),
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
        },
        lower: [spenderLtFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gt and lt, token eq',
      filters: [spenderGtFilter, spenderLtFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGtFilter, upper: spenderLtFilter}),
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
        },
        lower: [spenderGtFilter, spenderLtFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gte and lte, token eq',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdEqFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter, upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({equal: tokenIdEqFilter}),
        },
        lower: [spenderGteFilter, spenderLteFilter, tokenIdEqFilter],
      },
    },
    {
      name: 'spender gte and token gte',
      filters: [spenderGteFilter, tokenIdGteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGteFilter],
        inner: [{...spenderGteFilter, operator: gt}],
      },
    },
    {
      name: 'spender gte and token gt',
      filters: [spenderGteFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGtFilter],
        inner: [{...spenderGteFilter, operator: gt}],
      },
    },
    {
      name: 'spender lte and token lte',
      filters: [spenderLteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({upper: tokenIdLteFilter}),
        },
        inner: [{...spenderLteFilter, operator: lt}],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLteFilter],
      },
    },
    {
      name: 'spender lte and token lt',
      filters: [spenderLteFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({upper: tokenIdLtFilter}),
        },
        inner: [{...spenderLteFilter, operator: lt}],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLtFilter],
      },
    },
    {
      name: 'spender gte and lte, token gt',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdGtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter, upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGtFilter],
        inner: [{...spenderGteFilter, operator: gt}, spenderLteFilter],
      },
    },
    {
      name: 'spender gte and lte, token lt',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter, upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({upper: tokenIdLtFilter}),
        },
        inner: [
          {...spenderGteFilter, operator: gte},
          {...spenderLteFilter, operator: lt},
        ],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLtFilter],
      },
    },
    {
      name: 'spender gte and lte, token gt and lt',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdGtFilter, tokenIdLtFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter, upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGtFilter, upper: tokenIdLtFilter}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGtFilter],
        inner: [
          {...spenderGteFilter, operator: gt},
          {...spenderLteFilter, operator: lt},
        ],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLtFilter],
      },
    },
    {
      name: 'spender gte and lte, token gte and lte',
      filters: [spenderGteFilter, spenderLteFilter, tokenIdGteFilter, tokenIdLteFilter],
      expected: {
        ...defaultExpected,
        bounds: {
          [SPENDER_ID]: Bound.create({lower: spenderGteFilter, upper: spenderLteFilter}),
          [TOKEN_ID]: Bound.create({lower: tokenIdGteFilter, upper: tokenIdLteFilter}),
        },
        lower: [{...spenderGteFilter, operator: eq}, tokenIdGteFilter],
        inner: [
          {...spenderGteFilter, operator: gt},
          {...spenderLteFilter, operator: lt},
        ],
        upper: [{...spenderLteFilter, operator: eq}, tokenIdLteFilter],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(controller.extractTokenMultiUnionQuery(spec.filters, ownerAccountId)).toEqual(spec.expected);
    });
  });
});

describe('extractTokenMultiUnionQuery throw', () => {
  const specs = [
    {
      name: 'spender.id ne',
      filters: [{key: SPENDER_ID, operator: ne, value: 1}],
    },
    {
      name: 'spender.id range and equal',
      filters: [spenderEqFilter, spenderGtFilter, spenderLtFilter],
    },
    {
      name: 'spender.id multiple equal',
      filters: [spenderEqFilter, spenderEqFilter],
    },
    {
      name: 'spender.id multiple lower bounds',
      filters: [spenderGtFilter, spenderGteFilter],
    },
    {
      name: 'spender.id multiple upper bounds',
      filters: [spenderLtFilter, spenderLteFilter],
    },
    {
      name: 'token.id ne',
      filters: [{key: TOKEN_ID, operator: ne, value: 1}],
    },
    {
      name: 'token.id range and equal',
      filters: [tokenIdEqFilter, tokenIdGtFilter, tokenIdLtFilter],
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
      name: 'token.id eq without spender.id query',
      filters: [tokenIdEqFilter],
    },
    {
      name: 'token.id gt without spender.id query',
      filters: [tokenIdGtFilter],
    },
    {
      name: 'token.id gt with spender.id gt',
      filters: [tokenIdGtFilter, spenderGtFilter],
    },
    {
      name: 'token.id lt without spender.id query',
      filters: [tokenIdLtFilter],
    },
    {
      name: 'token.id lt with spender.id lt',
      filters: [tokenIdLtFilter, spenderLtFilter],
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(() => controller.extractTokenMultiUnionQuery(spec.filters, ownerAccountId)).toThrowErrorMatchingSnapshot();
    });
  });
});
