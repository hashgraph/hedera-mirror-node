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
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import integrationDbOps from '../integrationDbOps';
import {CachedToken} from '../../model';

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

describe('getCachedTokens', () => {
  beforeEach(async () => {
    const tokens = [
      {
        decimals: 3,
        token_id: 300,
      },
      {
        decimals: 4,
        freeze_key: [1, 1],
        kyc_key: [1, 1],
        token_id: 400,
      },
      {
        decimals: 5,
        freeze_default: true,
        freeze_key: [1, 1],
        token_id: 500,
      },
    ];
    await integrationDomainOps.loadTokens(tokens);
  });

  test('no match', async () => {
    expect(await TokenService.getCachedTokens(new Set([100]))).toBeEmpty();
  });

  test('single', async () => {
    const expected = new Map([
      [
        300,
        new CachedToken({
          decimals: 3,
          freeze_default: 0, // not applicable
          kyc_default: 0, // not applicable
          token_id: 300,
        }),
      ],
    ]);
    await expect(TokenService.getCachedTokens(new Set([300]))).resolves.toStrictEqual(expected);

    // cache hit
    await integrationDbOps.cleanUp();
    await expect(TokenService.getCachedTokens(new Set([300]))).resolves.toStrictEqual(expected);
  });

  test('multiple', async () => {
    const expected = new Map([
      [
        300,
        new CachedToken({
          decimals: 3,
          freeze_default: 0, // not applicable
          kyc_default: 0, // not applicable
          token_id: 300,
        }),
      ],
      [
        400,
        new CachedToken({
          decimals: 4,
          freeze_default: 2, // unfrozen
          kyc_default: 2, // revoked
          token_id: 400,
        }),
      ],
      [
        500,
        new CachedToken({
          decimals: 5,
          freeze_default: 1, // frozen
          kyc_default: 0, // not applicable
          token_id: 500,
        }),
      ],
    ]);

    await expect(TokenService.getCachedTokens(new Set([300, 400, 500]))).resolves.toStrictEqual(expected);
  });
});

describe('putTokenCache', () => {
  test('put then get', async () => {
    const token = {
      decimals: 2,
      freeze_default: 0,
      kyc_default: 0,
      token_id: 200,
    };
    TokenService.putTokenCache(token);
    const expected = new Map([[200, new CachedToken(token)]]);
    await expect(TokenService.getCachedTokens(new Set([200]))).resolves.toStrictEqual(expected);

    // put again, note some fields have different value, to validate the service returns the previous copy
    TokenService.putTokenCache({...token, decimals: 3});
    await expect(TokenService.getCachedTokens(new Set([200]))).resolves.toStrictEqual(expected);
  });
});
