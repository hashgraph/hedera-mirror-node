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

import {assertSqlQueryEqual} from '../testutils';
import {TokenService} from '../../service';

describe('getQuery', () => {
  const defaultQuery = {
    filters: [],
    order: 'asc',
    ownerAccountId: 98,
    limit: 25,
  };
  const tokenBalanceJoin =
    'left join (select token_id,balance from token_balance where account_id = $1 and consensus_timestamp = (select max(consensus_timestamp) from account_balance_file)) tb on ta.token_id = tb.token_id';

  const tokenAccountFields = 'ta.automatic_association,ta.created_timestamp,ta.freeze_status,ta.kyc_status,ta.token_id';
  const specs = [
    {
      name: 'default',
      query: defaultQuery,
      expected: {
        sqlQuery:
          'select ' +
          tokenAccountFields +
          ',tb.balance from token_account ta ' +
          tokenBalanceJoin +
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
          tokenAccountFields +
          ',tb.balance from token_account ta ' +
          tokenBalanceJoin +
          ' where ta.account_id = $1 and ta.associated = true order by ta.token_id desc limit $2',
        params: [98, 25],
      },
    }, // Going onwards fix it
    {
      name: 'token_id eq',
      query: {...defaultQuery, conditions: [{key: 'TOKEN_ID', operator: '=', value: 2}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenAccountFields +
          `, tb.balance
           from token_account ta ` +
          tokenBalanceJoin +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id = $3
          order by ta.token_id asc
          limit $2`,
        params: [98, 25, 2],
      },
    },
    {
      name: 'token_id gt',
      query: {...defaultQuery, conditions: [{key: 'TOKEN_ID', operator: '>', value: 10}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenAccountFields +
          `, tb.balance
           from token_account ta ` +
          tokenBalanceJoin +
          ` where ta.account_id = $1 and ta.associated = true and ta.token_id > $3
          order by ta.token_id asc
          limit $2`,
        params: [98, 25, 10],
      },
    },
    {
      name: 'token_id lt',
      query: {...defaultQuery, conditions: [{key: 'TOKEN_ID', operator: '<', value: 5}]},
      expected: {
        sqlQuery:
          `select ` +
          tokenAccountFields +
          `, tb.balance
           from token_account ta ` +
          tokenBalanceJoin +
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
