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

import {filterKeys} from '../../constants';
import {opsMap} from '../../utils';
import {assertSqlQueryEqual} from '../testutils';
import {TokenAllowanceService} from '../../service';

describe('getQuery', () => {
  const defaultQuery = {
    lower: [],
    inner: [],
    upper: [],
    order: 'asc',
    ownerAccountId: 1,
    limit: 25,
  };

  const specs = [
    {
      name: 'default',
      query: defaultQuery,
      expected: {
        sqlQuery: 'select * from token_allowance where owner = $1 order by spender asc, token_id asc limit $2',
        params: [1, 25],
      },
    },
    {
      name: 'order desc',
      query: {...defaultQuery, order: 'desc'},
      expected: {
        sqlQuery: 'select * from token_allowance where owner = $1 order by spender desc, token_id desc limit $2',
        params: [1, 25],
      },
    },
    {
      name: 'spender eq',
      query: {...defaultQuery, lower: [{key: SPENDER_ID, operator: eq, value: 2}]},
      expected: {
        sqlQuery: `select * from token_allowance
          where owner = $1 and spender = $3
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2],
      },
    },
    {
      name: 'spender eq and token eq',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: eq, value: 3},
        ],
      },
      expected: {
        sqlQuery: `select * from token_allowance
          where owner = $1 and spender = $3 and token_id = $4
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 3],
      },
    },
    {
      name: 'spender lower bound and token lower bound',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: gt, value: 3},
        ],
        inner: [{key: SPENDER_ID, operator: gt, value: 2}],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and spender = $3 and token_id > $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and spender > $5
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2
          `,
        params: [1, 25, 2, 3, 2],
      },
    },
    {
      name: 'spender upper bound and token upper bound',
      query: {
        ...defaultQuery,
        inner: [{key: SPENDER_ID, operator: lt, value: 2}],
        upper: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: lt, value: 3},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and spender < $3
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and spender = $4 and token_id < $5
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 2, 3],
      },
    },
    {
      name: 'spender closed range and token eq',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: gt, value: 2},
          {key: SPENDER_ID, operator: lt, value: 10},
          {key: TOKEN_ID, operator: eq, value: 3},
        ],
      },
      expected: {
        sqlQuery: `select * from token_allowance
          where owner = $1 and spender > $3 and spender < $4 and token_id = $5
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 10, 3],
      },
    },
    {
      name: 'spender closed range and token gt',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: gt, value: 3},
        ],
        inner: [
          {key: SPENDER_ID, operator: gt, value: 2},
          {key: SPENDER_ID, operator: lte, value: 11},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and spender = $3 and token_id > $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and spender > $5 and spender <= $6
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 3, 2, 11],
      },
    },
    {
      name: 'spender closed range and token lt',
      query: {
        ...defaultQuery,
        inner: [
          {key: SPENDER_ID, operator: gte, value: 2},
          {key: SPENDER_ID, operator: lt, value: 11},
        ],
        upper: [
          {key: SPENDER_ID, operator: eq, value: 11},
          {key: TOKEN_ID, operator: lt, value: 3},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and spender >= $3 and spender < $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and spender = $5 and token_id < $6
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 11, 11, 3],
      },
    },
    {
      name: 'spender closed range and token closed range',
      query: {
        ...defaultQuery,
        lower: [
          {key: SPENDER_ID, operator: eq, value: 2},
          {key: TOKEN_ID, operator: gte, value: 100},
        ],
        inner: [
          {key: SPENDER_ID, operator: gt, value: 2},
          {key: SPENDER_ID, operator: lt, value: 8},
        ],
        upper: [
          {key: SPENDER_ID, operator: eq, value: 8},
          {key: TOKEN_ID, operator: lte, value: 200},
        ],
      },
      expected: {
        sqlQuery: `(select * from token_allowance
            where owner = $1 and spender = $3 and token_id >= $4
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and spender > $5 and spender < $6
            order by spender asc, token_id asc
            limit $2
          ) union all (
            select * from token_allowance
            where owner = $1 and spender = $7 and token_id <= $8
            order by spender asc, token_id asc
            limit $2
          )
          order by spender asc, token_id asc
          limit $2`,
        params: [1, 25, 2, 100, 2, 8, 8, 200],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = TokenAllowanceService.getQuery(spec.query);
      assertSqlQueryEqual(actual.sqlQuery, spec.expected.sqlQuery);
      expect(actual.params).toEqual(spec.expected.params);
    });
  });
});
