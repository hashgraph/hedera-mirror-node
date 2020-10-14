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
const {filterKeys, orderFilterValues} = require('../constants');
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
    const expectedorder = orderFilterValues.ASC;
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
        key: filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
    ];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join t_entities e on e.id = t.token_id where e.ed25519_public_key_hex = $1 order by t.token_id asc limit $2;';
    const expectedparams = ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', maxLimit];
    const expectedorder = orderFilterValues.ASC;
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
        key: filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
    ];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id join t_entities e on e.id = t.token_id order by t.token_id asc limit $2;';
    const expectedparams = [5, maxLimit];
    const expectedorder = orderFilterValues.ASC;
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
        key: filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
      {
        key: filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
      {key: filterKeys.TOKEN_ID, operator: ' > ', value: '2'},
      {key: filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: filterKeys.ORDER, operator: ' = ', value: orderFilterValues.DESC},
    ];

    const expectedquery =
      'select t.token_id, symbol, e.key from token t join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id join t_entities e on e.id = t.token_id where e.ed25519_public_key_hex = $2 and t.token_id > $3 order by t.token_id desc limit $4;';
    const expectedparams = [5, '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', '2', '3'];
    const expectedorder = orderFilterValues.DESC;
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
    return query.trim().replace(/\n/g, ' ').replace(/\(\s+/g, '(').replace(/\s+\)/g, ')').replace(/\s+/g, ' ');
  };

  const operators = Object.values(opsMap);
  const initialQuery = tokens.tokenBalancesSelectQuery;
  const encodedTokenIdStr = '1009';
  const tokenId = EntityId.fromString(encodedTokenIdStr);
  const accountIdStr = '960';
  const balance = '2000';
  const publicKey = '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be';
  const timestampNs = '123456789000000000';

  const testSpecs = [
    {
      name: 'no filters',
      tokenId,
      initialQuery,
      filters: [],
      expected: {
        query: `
          select
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          from token_balance tb
          where tb.token_id = $1
            and tb.consensus_timestamp = (
              select tb.consensus_timestamp
              from token_balance tb
              order by tb.consensus_timestamp desc
              limit 1
            )
          order by tb.account_id desc
          limit $2`,
        params: [encodedTokenIdStr, maxLimit],
        order: orderFilterValues.DESC,
        limit: maxLimit,
      },
    },
    ...operators.map((op) => {
      return {
        name: `timestamp ${op} ${timestampNs}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.TIMESTAMP,
            operator: op,
            value: timestampNs,
          },
        ],
        expected: {
          query: `
            select
              tb.consensus_timestamp,
              tb.account_id,
              tb.balance
            from token_balance tb
            where tb.token_id = $1
              and tb.consensus_timestamp = (
                select tb.consensus_timestamp
                from token_balance tb
                where tb.consensus_timestamp ${op !== opsMap.eq ? op : '<='} $2
                order by tb.consensus_timestamp desc
                limit 1
              )
            order by tb.account_id desc
            limit $3`,
          params: [encodedTokenIdStr, timestampNs, maxLimit],
          order: orderFilterValues.DESC,
          limit: maxLimit,
        },
      };
    }),
    ...[maxLimit - 1, maxLimit + 1].map((limit) => {
      const expectedLimit = Math.min(limit, maxLimit);
      return {
        name: `limit = ${limit}, maxLimit = ${maxLimit}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.LIMIT,
            operator: opsMap.eq,
            value: expectedLimit,
          },
        ],
        expected: {
          query: `
            select tb.consensus_timestamp,
                   tb.account_id,
                   tb.balance
            from token_balance tb
            where tb.token_id = $1
              and tb.consensus_timestamp = (
                select tb.consensus_timestamp
                from token_balance tb
                order by tb.consensus_timestamp desc
                limit 1
              )
            order by tb.account_id desc
            limit $2`,
          params: [encodedTokenIdStr, expectedLimit],
          order: orderFilterValues.DESC,
          limit: expectedLimit,
        },
      };
    }),
    ...operators.map((op) => {
      return {
        name: `account.id ${op} ${accountIdStr}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.ACCOUNT_ID,
            operator: op,
            value: accountIdStr,
          },
        ],
        expected: {
          query: `
            select
              tb.consensus_timestamp,
              tb.account_id,
              tb.balance
            from token_balance tb
            where  tb.token_id = $1
              and tb.account_id ${op} $2
              and tb.consensus_timestamp = (
                select tb.consensus_timestamp
                from token_balance tb
                order by tb.consensus_timestamp desc
                limit 1
              )
            order by tb.account_id desc
            limit $3`,
          params: [encodedTokenIdStr, accountIdStr, maxLimit],
          order: orderFilterValues.DESC,
          limit: maxLimit,
        },
      };
    }),
    ...operators.map((op) => {
      return {
        name: `balance ${op} ${balance}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.ACCOUNT_BALANCE,
            operator: op,
            value: balance,
          },
        ],
        expected: {
          query: `
            select
              tb.consensus_timestamp,
              tb.account_id,
              tb.balance
            from token_balance tb
            where tb.token_id = $1
              and tb.balance ${op} $2
              and tb.consensus_timestamp = (
                select tb.consensus_timestamp
                from token_balance tb
                order by tb.consensus_timestamp desc
                limit 1
              )
            order by tb.account_id desc
            limit $3`,
          params: [encodedTokenIdStr, balance, maxLimit],
          order: orderFilterValues.DESC,
          limit: maxLimit,
        },
      };
    }),
    ...Object.values(orderFilterValues).map((order) => {
      return {
        name: `order ${order}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.ORDER,
            operator: opsMap.eq,
            value: order,
          },
        ],
        expected: {
          query: `
            select
              tb.consensus_timestamp,
              tb.account_id,
              tb.balance
            from token_balance tb
            where tb.token_id = $1
              and tb.consensus_timestamp = (
                select tb.consensus_timestamp
                from token_balance tb
                order by tb.consensus_timestamp desc
                limit 1
              )
            order by tb.account_id ${order}
            limit $2`,
          params: [encodedTokenIdStr, maxLimit],
          order,
          limit: maxLimit,
        },
      };
    }),
    {
      name: `account publickey "${publicKey}"`,
      tokenId,
      initialQuery,
      filters: [
        {
          key: filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
      ],
      expected: {
        query: `
          select
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          from token_balance tb
          join t_entities e
            on e.fk_entity_type_id = 1
            and e.id = tb.account_id
            and e.ed25519_public_key_hex = $2
          where tb.token_id = $1
            and tb.consensus_timestamp = (
              select tb.consensus_timestamp
              from token_balance tb
              order by tb.consensus_timestamp desc
              limit 1
            )
          order by tb.account_id desc
          limit $3`,
        params: [encodedTokenIdStr, publicKey, maxLimit],
        order: orderFilterValues.DESC,
        limit: maxLimit,
      },
    },
    {
      name: 'all filters',
      tokenId,
      initialQuery,
      filters: [
        {
          key: filterKeys.ACCOUNT_ID,
          operator: opsMap.eq,
          value: accountIdStr,
        },
        {
          key: filterKeys.ACCOUNT_BALANCE,
          operator: opsMap.eq,
          value: balance,
        },
        {
          key: filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
        {
          key: filterKeys.LIMIT,
          operator: opsMap.eq,
          value: 1,
        },
        {
          key: filterKeys.ORDER,
          operator: opsMap.eq,
          value: orderFilterValues.ASC,
        },
        {
          key: filterKeys.TIMESTAMP,
          operator: opsMap.eq,
          value: timestampNs,
        },
      ],
      expected: {
        query: `
          select
            tb.consensus_timestamp,
            tb.account_id,
            tb.balance
          from token_balance tb
          join t_entities e
            on e.fk_entity_type_id = 1
            and e.id = tb.account_id
            and e.ed25519_public_key_hex = $4
          where tb.token_id = $1
            and tb.account_id = $2
            and tb.balance = $3
            and tb.consensus_timestamp = (
              select tb.consensus_timestamp
              from token_balance tb
              where tb.consensus_timestamp <= $5
              order by tb.consensus_timestamp desc
              limit 1
            )
          order by tb.account_id asc
          limit $6`,
        params: [encodedTokenIdStr, accountIdStr, balance, publicKey, timestampNs, 1],
        order: orderFilterValues.ASC,
        limit: 1,
      },
    },
  ];

  for (const spec of testSpecs) {
    const {name, tokenId, initialQuery, filters, expected} = spec;
    test(name, () => {
      const actual = tokens.extractSqlFromTokenBalancesRequest(tokenId, initialQuery, filters);

      actual.query = formatSqlQueryString(actual.query);
      expected.query = formatSqlQueryString(expected.query);
      expect(actual).toEqual(expected);
    });
  }
});
