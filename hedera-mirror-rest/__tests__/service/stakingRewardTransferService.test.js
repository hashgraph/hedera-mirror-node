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
import {StakingRewardTransferService} from '../../service';

describe('getRewardsQuery', () => {
  const queryFields = 'account_id,amount,consensus_timestamp ';
  const specs = [
    {
      name: 'default',
      accountId: 1111,
      order: 'desc',
      limit: 25,
      whereConditions: [],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'limit 100',
      accountId: 1122,
      order: 'desc',
      limit: 100,
      whereConditions: [],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'order asc',
      accountId: 2222,
      order: 'asc',
      limit: 25,
      whereConditions: [],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 order by consensus_timestamp asc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp eq',
      accountId: 3333,
      order: 'desc',
      limit: 25,
      whereConditions: [{key: 'timestamp', operator: '=', value: 1000}],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp = 1000 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp in',
      accountId: 3333,
      order: 'desc',
      limit: 25,
      whereConditions: [{key: 'timestamp', operator: 'in', value: '(1000, 2000)'}],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp in (1000, 2000) order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp lt',
      accountId: 4444,
      order: 'desc',
      limit: 25,
      whereConditions: [{key: 'timestamp', operator: '<', value: 2000}],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp < 2000 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp lte',
      accountId: 5555,
      order: 'desc',
      limit: 25,
      whereConditions: [{key: 'timestamp', operator: '<=', value: 3000}],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp <= 3000 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp gt',
      accountId: 6666,
      order: 'desc',
      limit: 25,
      whereConditions: [{key: 'timestamp', operator: '>', value: 4000}],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp > 4000 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp gte',
      accountId: 7777,
      order: 'desc',
      limit: 25,
      whereConditions: [{key: 'timestamp', operator: '>=', value: 5000}],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp >= 5000 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'multiple conditions',
      accountId: 8888,
      order: 'desc',
      limit: 5,
      whereConditions: [
        {key: 'timestamp', operator: '>=', value: 3000},
        {key: 'timestamp', operator: '<=', value: 5000},
      ],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer ' +
          'where account_id = $1 and consensus_timestamp >= 3000 and consensus_timestamp <= 5000 order by consensus_timestamp desc limit $2',
        params: [],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = StakingRewardTransferService.getRewardsQuery(
        spec.accountId,
        spec.order,
        spec.limit,
        spec.whereConditions,
        spec.whereParams
      );
      assertSqlQueryEqual(actual.query, spec.expected.sqlQuery);
      expect(actual.params).toEqual(spec.expected.params);
    });
  });
});
