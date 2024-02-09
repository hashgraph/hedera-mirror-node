/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import {TokenService} from '../../service';
import integrationDomainOps from '../integrationDomainOps.js';
import {setupIntegrationTest} from '../integrationUtils.js';

setupIntegrationTest();

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

describe('TokenService.getDecimals tests', () => {
  test('TokenService.getDecimals - No match', async () => {
    expect(await TokenService.getDecimals([100])).toBeEmpty();
  });

  test('TokenService.getDecimals - getDecimal cache', async () => {
    const token3 = {
      token_id: '0.0.300',
      decimals: 3,
    };
    const token4 = {
      token_id: '0.0.400',
      decimals: 40,
    };
    await integrationDomainOps.loadTokens([token3, token4]);

    const original = await TokenService.getDecimals([300]);
    expect(original.get(300)).toBe(3);
    const cached = await TokenService.getDecimals([300]);
    expect(cached.get(300)).toBe(original.get(300));

    const multiToken = await TokenService.getDecimals([300, 400]);
    expect(multiToken.get(300)).toBe(3);
    expect(multiToken.get(400)).toBe(40);

    const multiTokenCached = await TokenService.getDecimals([300, 400]);
    expect(multiTokenCached.get(300)).toBe(multiToken.get(300));
    expect(multiTokenCached.get(400)).toBe(multiToken.get(400));
  });

  test('TokenService.getDecimals - test where in clause', async () => {
    const token5 = {
      token_id: '0.0.500',
      decimals: 5,
    };
    const token6 = {
      token_id: '0.0.600',
      decimals: 60,
    };
    await integrationDomainOps.loadTokens([token5, token6]);

    const original = await TokenService.getDecimals([500, 600]);
    expect(original.get(500)).toBe(5);
    expect(original.get(600)).toBe(60);

    const cached = await TokenService.getDecimals([500, 600]);
    expect(cached.get(500)).toBe(original.get(500));
    expect(cached.get(600)).toBe(original.get(600));
  });

  test('TokenService.getDecimals - set and get decimal cache', async () => {
    TokenService.addDecimalsToCache(5000, 5);
    const cached = await TokenService.getDecimals([5000]);
    expect(cached.get(5000)).toBe(5);
  });
});
