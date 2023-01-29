/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {assertSqlQueryEqual} from '../testutils';
import {TokenService} from '../../service';

describe('getQuery', () => {
  const defaultQuery = {
    conditions: [],
    inConditions: [],
    order: 'asc',
    ownerAccountId: 98,
    limit: 25,
  };
  const tokenFields =
    'ta.automatic_association,ta.balance,ta.created_timestamp,ta.freeze_status,ta.kyc_status,ta.token_id ';
  const specs = [
    {
      name: 'default',
      query: defaultQuery,
      expected: {
        sqlQuery:
          'select ' +
          tokenFields +
          'from token_account ta ' +
          ' where ta.account_id = $1 and ta.associated = true order by ta.token_id asc limit $2',
        params: [98, 25],
      },
    },
    {
      name: 'order desc',
      query: {...defaultQuery, order: 'desc'},
      expected: {
        sqlQuery:
          'select ' +
          tokenFields +
          'from token_account ta ' +
          ' where ta.account_id = $1 and ta.associated = true order by ta.token_id desc limit $2',
        params: [98, 25],
      },
    }, // Going onwards fix it
    {
      name: 'token_id eq',
      query: {...defaultQuery, inConditions: [{key: 'token_id', operator: '=', value: 2}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenFields +
          `from token_account ta ` +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id in (2)
          order by ta.token_id asc
          limit $2`,
        params: [98, 25],
      },
    },
    {
      name: 'token_id gt',
      query: {...defaultQuery, conditions: [{key: 'token_id', operator: '>', value: 10}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenFields +
          `from token_account ta ` +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id > $3
          order by ta.token_id asc
          limit $2`,
        params: [98, 25, 10],
      },
    },
    {
      name: 'token_id lt',
      query: {...defaultQuery, conditions: [{key: 'token_id', operator: '<', value: 5}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenFields +
          `from token_account ta ` +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id < $3
          order by ta.token_id asc
          limit $2`,
        params: [98, 25, 5],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = TokenService.getTokenRelationshipsQuery(spec.query);
      assertSqlQueryEqual(actual.sqlQuery, spec.expected.sqlQuery);
      expect(actual.params).toEqual(spec.expected.params);
    });
  });
});
