/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const tokens = require('../tokens');
const constants = require('../constants');
const {maxLimit} = require('../config');
const EntityId = require('../entityId');
const {opsMap} = require('../utils');

beforeAll(async () => {
  jest.setTimeout(1000);
});

afterAll(() => {});

describe('token formatTokenRow tests', () => {
  const rowInput = {
    key: [3, 3, 3],
    public_key: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
    symbol: 'YBTJBOAZ',
    token_id: '7',
  };

  const expectedFormat = {
    token_id: '0.0.7',
    symbol: 'YBTJBOAZ',
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
  };

  test('Verify formatTokenRow', () => {
    const formattedInput = tokens.formatTokenRow(rowInput);

    expect(formattedInput.token_id).toStrictEqual(expectedFormat.token_id);
    expect(formattedInput.symbol).toStrictEqual(expectedFormat.symbol);
    expect(JSON.stringify(formattedInput.admin_key)).toStrictEqual(JSON.stringify(expectedFormat.admin_key));
  });
});

describe('token extractSqlFromTokenRequest tests', () => {
  test('Verify simple discovery query', () => {
    const initialQuery = `${tokens.tokensSelectQuery}${tokens.entityIdJoinQuery}`;
    const initialParams = [];
    const nextParamCount = 1;
    const filters = [];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join t_entities e on e.id = t.token_id order by t.token_id asc limit $1;';
    const expectedparams = [maxLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      nextParamCount,
      filters,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify public key filter', () => {
    const initialQuery = `${tokens.tokensSelectQuery}${tokens.entityIdJoinQuery}`;
    const initialParams = [];
    const nextParamCount = 1;
    const filters = [
      {
        key: constants.filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
    ];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join t_entities e on e.id = t.token_id where e.ed25519_public_key_hex = $1 order by t.token_id asc limit $2;';
    const expectedparams = ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', maxLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      nextParamCount,
      filters,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify account id filter', () => {
    const initialQuery = `${tokens.tokensSelectQuery}${tokens.accountIdJoinQuery}${tokens.entityIdJoinQuery}`;
    const initialParams = [5];
    const nextParamCount = 2;
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
    ];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id join t_entities e on e.id = t.token_id order by t.token_id asc limit $2;';
    const expectedparams = [5, maxLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      nextParamCount,
      filters,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify all filters', () => {
    const initialQuery = `${tokens.tokensSelectQuery}${tokens.accountIdJoinQuery}${tokens.entityIdJoinQuery}`;
    const initialParams = [5];
    const nextParamCount = 2;
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
      {
        key: constants.filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
      {key: constants.filterKeys.TOKEN_ID, operator: ' > ', value: '2'},
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id join t_entities e on e.id = t.token_id where e.ed25519_public_key_hex = $2 and t.token_id > $3 order by t.token_id desc limit $4;';
    const expectedparams = [5, '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', '2', '3'];
    const expectedorder = constants.orderFilterValues.DESC;
    const expectedlimit = 3;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      nextParamCount,
      filters,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });
});

const verifyExtractSqlFromTokenRequest = (
  pgSqlQuery,
  pgSqlParams,
  nextParamCount,
  filters,
  expectedquery,
  expectedparams,
  expectedorder,
  expectedlimit
) => {
  const {query, params, order, limit} = tokens.extractSqlFromTokenRequest(
    pgSqlQuery,
    pgSqlParams,
    nextParamCount,
    filters
  );

  expect(query).toStrictEqual(expectedquery);
  expect(params).toStrictEqual(expectedparams);
  expect(order).toStrictEqual(expectedorder);
  expect(limit).toStrictEqual(expectedlimit);
};

describe('token formatTokenBalanceRow tests', () => {
  test('Verify formatTokenBalanceRow', () => {
    const rowInput = {
      account_id: '193',
      balance: 200,
    };
    const expectedOutput = {
      account: '0.0.193',
      balance: 200,
    };

    expect(tokens.formatTokenBalanceRow(rowInput)).toEqual(expectedOutput);
  });
});

describe('token extractSqlFromTokenBalancesRequest tests', () => {
  const formatSqlQueryString = (query) => {
    return query
      .trim()
      .toLowerCase()
      .replace(/\n/g, ' ')
      .replace(/\(\s+/g, '(')
      .replace(/\s+\)/g, ')')
      .replace(/\s+/g, ' ');
  };

  const tokenId = EntityId.fromString('1009');
  const balanceOpsMap = {
    ...opsMap,
    '': '=',
  };
  const timestampOpsMap = {
    ...opsMap,
    '': '<=',
    eq: '<=',
  };
  const testSpecs = [
    {
      name: 'no query params',
      req: {
        query: {},
      },
      tokenId,
      expected: {
        pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            ) AND tb.token_id = $1
           ORDER BY tb.account_id DESC
           LIMIT $2
        `,
        pgSqlParams: ['1009', maxLimit],
        order: 'desc',
        limit: maxLimit,
      },
    },
    ...Object.keys(timestampOpsMap).map((op) => {
      return {
        name: `timestamp ${op} 123456789`,
        req: {
          query: {
            timestamp: `${op !== '' ? `${op}:123456789` : '123456789'}`,
          },
        },
        tokenId,
        expected: {
          pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              WHERE tb.consensus_timestamp ${timestampOpsMap[op]} $1
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            ) AND tb.token_id = $2
           ORDER BY tb.account_id DESC
           LIMIT $3
        `,
          pgSqlParams: ['123456789000000000', '1009', maxLimit],
          order: 'desc',
          limit: maxLimit,
        },
      };
    }),
    ...[maxLimit - 1, maxLimit + 1].map((limit) => {
      const expectedLimit = Math.min(limit, maxLimit);
      return {
        name: `limit = ${limit}, maxLimit = ${maxLimit}`,
        req: {
          query: {
            limit,
          },
        },
        tokenId,
        expected: {
          pgSqlQuery: `
            SELECT tb.consensus_timestamp,
                   tb.account_id,
                   tb.balance
            FROM token_balance tb
            WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            )
              AND tb.token_id = $1
            ORDER BY tb.account_id DESC
            LIMIT $2
          `,
          pgSqlParams: ['1009', expectedLimit],
          order: 'desc',
          limit: expectedLimit,
        },
      };
    }),
    {
      name: 'account.id = 960',
      req: {
        query: {
          'account.id': '960',
        },
      },
      tokenId,
      expected: {
        pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            ) AND tb.token_id = $1
            AND tb.account_id = $2
           ORDER BY tb.account_id DESC
           LIMIT $3
        `,
        pgSqlParams: ['1009', '960', maxLimit],
        order: 'desc',
        limit: maxLimit,
      },
    },
    ...Object.keys(balanceOpsMap).map((op) => {
      return {
        name: `balance ${op} 2000`,
        req: {
          query: {
            'account.balance': `${op !== '' ? `${op}:2000` : '2000'}`,
          },
        },
        tokenId,
        expected: {
          pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          WHERE tb.consensus_timestamp = (
            SELECT tb.consensus_timestamp
            FROM token_balance tb
            ORDER BY tb.consensus_timestamp DESC
            LIMIT 1
          ) AND tb.token_id = $1
            AND tb.balance ${balanceOpsMap[op]} $2
          ORDER BY tb.account_id DESC
          LIMIT $3
        `,
          pgSqlParams: ['1009', '2000', maxLimit],
          order: 'desc',
          limit: maxLimit,
        },
      };
    }),
    ...['asc', 'desc'].map((order) => {
      return {
        name: `order ${order}`,
        req: {
          query: {
            order,
          },
        },
        tokenId,
        expected: {
          pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            ) AND tb.token_id = $1
           ORDER BY tb.account_id ${order}
           LIMIT $2
        `,
          pgSqlParams: ['1009', maxLimit],
          order,
          limit: maxLimit,
        },
      };
    }),
    {
      name: 'account publickey "3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be"',
      req: {
        query: {
          'account.publickey': '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
        },
      },
      tokenId,
      expected: {
        pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          JOIN t_entities e
            ON e.fk_entity_type_id = 1
            AND e.id = tb.account_id
            AND e.ed25519_public_key_hex = $1
          WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            ) AND tb.token_id = $2
           ORDER BY tb.account_id DESC
           LIMIT $3
        `,
        pgSqlParams: ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', '1009', maxLimit],
        order: 'desc',
        limit: maxLimit,
      },
    },
    {
      name: 'all query params',
      req: {
        query: {
          'account.balance': '2000',
          'account.id': '960',
          'account.publickey': '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
          limit: 1,
          timestamp: '123456789',
          order: 'asc',
        },
      },
      tokenId,
      expected: {
        pgSqlQuery: `
          SELECT
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          FROM token_balance tb
          JOIN t_entities e
            ON e.fk_entity_type_id = 1
            AND e.id = tb.account_id
            AND e.ed25519_public_key_hex = $1
          WHERE tb.consensus_timestamp = (
              SELECT tb.consensus_timestamp
              FROM token_balance tb
              WHERE tb.consensus_timestamp <= $2
              ORDER BY tb.consensus_timestamp DESC
              LIMIT 1
            )
            AND tb.token_id = $3
            AND tb.account_id = $4
            AND tb.balance = $5
           ORDER BY tb.account_id ASC
           LIMIT $6
        `,
        pgSqlParams: [
          '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
          '123456789000000000',
          '1009',
          '960',
          '2000',
          1,
        ],
        order: 'asc',
        limit: 1,
      },
    },
  ];

  for (const spec of testSpecs) {
    const {name, req, tokenId: testTokenId, expected} = spec;
    test(name, () => {
      const actual = tokens.extractSqlFromTokenBalancesRequest(req, testTokenId);

      actual.pgSqlQuery = formatSqlQueryString(actual.pgSqlQuery);
      expected.pgSqlQuery = formatSqlQueryString(expected.pgSqlQuery);
      expect(actual).toEqual(expected);
    });
  }
});
