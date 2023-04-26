/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

import {assertSqlQueryEqual} from '../testutils';
import {StakingRewardTransferService} from '../../service';

describe('getRewardsQuery', () => {
  const queryFields = 'srt.account_id,srt.amount,srt.consensus_timestamp ';
  const specs = [
    {
      name: 'default',
      order: 'desc',
      limit: 25,
      whereConditions: [],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'limit 100',
      order: 'desc',
      limit: 100,
      whereConditions: [],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'order asc',
      order: 'asc',
      limit: 25,
      whereConditions: [],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 order by srt.consensus_timestamp asc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp eq',
      order: 'desc',
      limit: 25,
      whereConditions: ['srt.consensus_timestamp = 1000'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp = 1000 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp in',
      order: 'desc',
      limit: 25,
      whereConditions: ['srt.consensus_timestamp in (1000, 2000)'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp in (1000, 2000) order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp lt',
      order: 'desc',
      limit: 25,
      whereConditions: ['srt.consensus_timestamp < 2000'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp < 2000 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp lte',
      order: 'desc',
      limit: 25,
      whereConditions: ['srt.consensus_timestamp <= 3000'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp <= 3000 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp gt',
      order: 'desc',
      limit: 25,
      whereConditions: ['srt.consensus_timestamp > 4000'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp > 4000 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'timestamp gte',
      order: 'desc',
      limit: 25,
      whereConditions: ['srt.consensus_timestamp >= 5000'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp >= 5000 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
    {
      name: 'multiple conditions',
      order: 'desc',
      limit: 5,
      whereConditions: ['srt.consensus_timestamp >= 3000', 'srt.consensus_timestamp <= 5000'],
      whereParams: [],
      expected: {
        sqlQuery:
          'select ' +
          queryFields +
          'from staking_reward_transfer srt ' +
          'where srt.account_id = $1 and srt.consensus_timestamp >= 3000 and srt.consensus_timestamp <= 5000 order by srt.consensus_timestamp desc limit $2',
        params: [],
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      const actual = StakingRewardTransferService.getRewardsQuery(
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
