/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
const {opsMap} = require('../utils');
const utils = require('../utils');
const {TransactionResultService, TransactionTypeService} = require('../service');
const {formatSqlQueryString} = require('./testutils');

describe('token formatTokenRow tests', () => {
  const rowInput = {
    key: [3, 3, 3],
    public_key: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
    symbol: 'YBTJBOAZ',
    token_id: '7',
    type: 'FUNGIBLE_COMMON',
  };

  const expectedFormat = {
    token_id: '0.0.7',
    symbol: 'YBTJBOAZ',
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
    type: 'FUNGIBLE_COMMON',
  };

  test('Verify formatTokenRow', () => {
    expect(tokens.formatTokenRow(rowInput)).toStrictEqual(expectedFormat);
  });
});

describe('token extractSqlFromTokenRequest tests', () => {
  const accountQueryCondition = 'ta.associated is true';

  test('Verify simple discovery query', () => {
    const initialQuery = [tokens.tokensSelectQuery, tokens.entityIdJoinQuery].join('\n');
    const initialParams = [];
    const filters = [];

    const expectedquery =
      'select t.token_id, symbol, e.key, t.type from token t join entity e on e.id = t.token_id order by t.token_id asc limit $1';
    const expectedparams = [maxLimit];
    const expectedorder = orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      null,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify public key filter', () => {
    const initialQuery = [tokens.tokensSelectQuery, tokens.entityIdJoinQuery].join('\n');
    const initialParams = [];
    const filters = [
      {
        key: filterKeys.ENTITY_PUBLICKEY,
        operator: ' = ',
        value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
      },
    ];

    const expectedquery = `select t.token_id, symbol, e.key, t.type
                           from token t
                                  join entity e on e.id = t.token_id
                           where e.public_key = $1
                           order by t.token_id asc
                           limit $2`;
    const expectedparams = ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', maxLimit];
    const expectedorder = orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      undefined,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify account id filter', () => {
    const initialQuery = [
      tokens.tokenAccountCte,
      tokens.tokensSelectQuery,
      tokens.tokenAccountJoinQuery,
      tokens.entityIdJoinQuery,
    ].join('\n');
    const initialParams = [5];
    const filters = [
      {
        key: filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
    ];

    const expectedquery = `with ta as (
                             select distinct on (account_id, token_id) *
                             from token_account
                             where account_id = $1
                             order by account_id, token_id, modified_timestamp desc
                           )
                           select t.token_id, symbol, e.key, t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true
                           order by t.token_id asc
                           limit $2`;
    const expectedparams = [5, maxLimit];
    const expectedorder = orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      [accountQueryCondition],
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify token type filter', () => {
    const initialQuery = [
      tokens.tokenAccountCte,
      tokens.tokensSelectQuery,
      tokens.tokenAccountJoinQuery,
      tokens.entityIdJoinQuery,
    ].join('\n');
    const initialParams = [5];
    const tokenType = 'NON_FUNGIBLE_UNIQUE';
    const filters = [
      {
        key: filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
    ];

    const expectedquery = `with ta as (
                             select distinct on (account_id, token_id) *
                             from token_account
                             where account_id = $1
                             order by account_id, token_id, modified_timestamp desc
                           )
                           select t.token_id, symbol, e.key, t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true and t.type = $2
                           order by t.token_id asc
                           limit $3`;
    const expectedparams = [5, tokenType, maxLimit];
    const expectedorder = orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      [accountQueryCondition],
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify all filters', () => {
    const initialQuery = [
      tokens.tokenAccountCte,
      tokens.tokensSelectQuery,
      tokens.tokenAccountJoinQuery,
      tokens.entityIdJoinQuery,
    ].join('\n');
    const initialParams = [5];
    const tokenType = 'FUNGIBLE_COMMON';
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
      {
        key: filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
      {key: filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: filterKeys.ORDER, operator: ' = ', value: orderFilterValues.DESC},
    ];

    const expectedquery = `with ta as (
                             select distinct on (account_id, token_id) *
                             from token_account
                             where account_id = $1
                             order by account_id, token_id, modified_timestamp desc
                           )
                           select t.token_id, symbol, e.key, t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true
                             and e.public_key = $2
                             and t.token_id > $3
                             and t.type = $4
                           order by t.token_id desc
                           limit $5`;
    const expectedparams = [5, '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', '2', tokenType, '3'];
    const expectedorder = orderFilterValues.DESC;
    const expectedlimit = 3;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      [accountQueryCondition],
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
  filters,
  extraConditions,
  expectedquery,
  expectedparams,
  expectedorder,
  expectedlimit
) => {
  const {query, params, order, limit} = tokens.extractSqlFromTokenRequest(
    pgSqlQuery,
    pgSqlParams,
    filters,
    extraConditions
  );

  expect(formatSqlQueryString(query)).toStrictEqual(formatSqlQueryString(expectedquery));
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
  const operators = Object.values(opsMap);
  const initialQuery = tokens.tokenBalancesSelectQuery;
  const tokenId = '1009'; // encoded
  const accountId = '960'; // encoded
  const balance = '2000';
  const publicKey = '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be';
  const timestampNsLow = '123456789000000000';
  const timestampNsHigh = '123456999000000000';

  const testSpecs = [
    {
      name: 'no filters',
      tokenId,
      initialQuery,
      filters: [],
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
        params: [tokenId, maxLimit],
        order: orderFilterValues.DESC,
        limit: maxLimit,
      },
    },
    {
      name: `timestamp > ${timestampNsLow} and timestamp < ${timestampNsHigh}`,
      tokenId,
      initialQuery,
      filters: [
        {
          key: filterKeys.TIMESTAMP,
          operator: '>',
          value: timestampNsLow,
        },
        {
          key: filterKeys.TIMESTAMP,
          operator: '<',
          value: timestampNsHigh,
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
            where tb.consensus_timestamp > $2
              and tb.consensus_timestamp < $3
            order by tb.consensus_timestamp desc
            limit 1
          )
          order by tb.account_id desc
          limit $4`,
        params: [tokenId, timestampNsLow, timestampNsHigh, maxLimit],
        order: orderFilterValues.DESC,
        limit: maxLimit,
      },
    },
    ...operators.map((op) => {
      return {
        name: `timestamp ${op} ${timestampNsLow}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.TIMESTAMP,
            operator: op,
            value: timestampNsLow,
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
              where tb.consensus_timestamp ${op !== opsMap.eq ? op : '<='}
                $2
              order by tb.consensus_timestamp desc
              limit 1)
            order by tb.account_id desc
            limit $3`,
          params: [tokenId, timestampNsLow, maxLimit],
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
          params: [tokenId, expectedLimit],
          order: orderFilterValues.DESC,
          limit: expectedLimit,
        },
      };
    }),
    ...operators.map((op) => {
      return {
        name: `account.id ${op} ${accountId}`,
        tokenId,
        initialQuery,
        filters: [
          {
            key: filterKeys.ACCOUNT_ID,
            operator: op,
            value: accountId,
          },
        ],
        expected: {
          query: `
            select tb.consensus_timestamp,
                   tb.account_id,
                   tb.balance
            from token_balance tb
            where tb.token_id = $1
              and tb.account_id ${op} $2
              and tb.consensus_timestamp = (
                select tb.consensus_timestamp
                from token_balance tb
                order by tb.consensus_timestamp desc
                limit 1
              )
            order by tb.account_id desc
            limit $3`,
          params: [tokenId, accountId, maxLimit],
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
            select tb.consensus_timestamp,
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
          params: [tokenId, balance, maxLimit],
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
            order by tb.account_id ${order}
            limit $2`,
          params: [tokenId, maxLimit],
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
          select tb.consensus_timestamp,
                 tb.account_id,
                 tb.balance
          from token_balance tb
                 join entity e
                      on e.type = ${utils.ENTITY_TYPE_ACCOUNT}
                        and e.id = tb.account_id
                        and e.public_key = $2
          where tb.token_id = $1
            and tb.consensus_timestamp = (
            select tb.consensus_timestamp
            from token_balance tb
            order by tb.consensus_timestamp desc
            limit 1
          )
          order by tb.account_id desc
          limit $3`,
        params: [tokenId, publicKey, maxLimit],
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
          value: accountId,
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
          value: timestampNsLow,
        },
      ],
      expected: {
        query: `
          select tb.consensus_timestamp,
                 tb.account_id,
                 tb.balance
          from token_balance tb
                 join entity e
                      on e.type = ${utils.ENTITY_TYPE_ACCOUNT}
                        and e.id = tb.account_id
                        and e.public_key = $4
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
        params: [tokenId, accountId, balance, publicKey, timestampNsLow, 1],
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

describe('token formatTokenInfoRow tests', () => {
  const rowInput = {
    token_id: '7',
    symbol: 'YBTJBOAZ',
    treasury_account_id: '3',
    freeze_default: true,
    key: [1, 1, 1],
    kyc_key: [2, 2, 2],
    fee_schedule_key: [6, 6, 6],
    freeze_key: [3, 3, 3],
    pause_key: [7, 7, 7],
    pause_status: 'UNPAUSED',
    supply_key: [4, 4, 4],
    wipe_key: [5, 5, 5],
    created_timestamp: 10,
    decimals: 10,
    deleted: true,
    initial_supply: 1000000,
    total_supply: 2000000,
    expiration_timestamp: 1594431063696143000,
    auto_renew_account_id: '98',
    auto_renew_period: 7890000,
    modified_timestamp: 1603394416676293000,
    name: 'Token name',
    type: 'FUNGIBLE_COMMON',
    max_supply: '9000000',
    supply_type: 'FINITE',
    memo: 'token.memo',
    custom_fees: [
      {
        amount: 55,
        collector_account_id: 8901,
        created_timestamp: 10,
        token_id: '7',
      },
      {
        amount: 59,
        collector_account_id: 8901,
        created_timestamp: 10,
        denominating_token_id: 19502,
        token_id: '7',
      },
      {
        amount: 66,
        amount_denominator: 77,
        collector_account_id: 8902,
        created_timestamp: 10,
        maximum_amount: 150,
        minimum_amount: 43,
        token_id: '7',
      },
      {
        amount: 83,
        amount_denominator: 94,
        collector_account_id: 8903,
        created_timestamp: 10,
        minimum_amount: 1,
        token_id: '7',
      },
    ],
  };

  const expected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '010101',
    },
    auto_renew_account: '0.0.98',
    auto_renew_period: 7890000,
    created_timestamp: '0.000000010',
    decimals: 10,
    deleted: true,
    expiry_timestamp: 1594431063696143000,
    fee_schedule_key: {
      _type: 'ProtobufEncoded',
      key: '060606',
    },
    freeze_default: true,
    freeze_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
    initial_supply: 1000000,
    kyc_key: {
      _type: 'ProtobufEncoded',
      key: '020202',
    },
    max_supply: '9000000',
    memo: 'token.memo',
    modified_timestamp: '1603394416.676293000',
    name: 'Token name',
    pause_key: {
      _type: 'ProtobufEncoded',
      key: '070707',
    },
    pause_status: 'UNPAUSED',
    supply_key: {
      _type: 'ProtobufEncoded',
      key: '040404',
    },
    supply_type: 'FINITE',
    symbol: 'YBTJBOAZ',
    token_id: '0.0.7',
    total_supply: 2000000,
    treasury_account_id: '0.0.3',
    type: 'FUNGIBLE_COMMON',
    wipe_key: {
      _type: 'ProtobufEncoded',
      key: '050505',
    },
    custom_fees: {
      created_timestamp: '0.000000010',
      fixed_fees: [
        {
          amount: 55,
          collector_account_id: '0.0.8901',
          denominating_token_id: null,
        },
        {
          amount: 59,
          collector_account_id: '0.0.8901',
          denominating_token_id: '0.0.19502',
        },
      ],
      fractional_fees: [
        {
          amount: {
            numerator: 66,
            denominator: 77,
          },
          collector_account_id: '0.0.8902',
          denominating_token_id: '0.0.7',
          maximum: 150,
          minimum: 43,
        },
        {
          amount: {
            numerator: 83,
            denominator: 94,
          },
          collector_account_id: '0.0.8903',
          denominating_token_id: '0.0.7',
          minimum: 1,
        },
      ],
    },
  };

  test('Verify formatTokenRow', () => {
    const actual = tokens.formatTokenInfoRow(rowInput);
    expect(actual).toEqual(expected);
  });
});

const verifyInvalidAndValidTokensFilters = (invalidQueries, validQueries, validator) => {
  invalidQueries.forEach((query) => {
    test(`Verify buildAndValidateFilters for invalid ${JSON.stringify(query)}`, () => {
      expect(() => utils.buildAndValidateFilters(query, validator)).toThrowErrorMatchingSnapshot();
    });
  });

  validQueries.forEach((query) => {
    test(`Verify buildAndValidateFilters for valid ${JSON.stringify(query)}`, () => {
      utils.buildAndValidateFilters(query, validator);
    });
  });
};

const makeQueries = (key, values) => {
  return values.map((v) => ({[key]: v}));
};

describe('utils buildAndValidateFilters token type tests', () => {
  const key = filterKeys.TOKEN_TYPE;
  const invalidQueries = makeQueries(key, [
    // invalid format
    'cred',
    'deb',
    // erroneous data
    '-1',
  ]);
  const validQueries = makeQueries(key, [
    'all',
    'ALL',
    'NON_FUNGIBLE_UNIQUE',
    'non_fungible_unique',
    'FUNGIBLE_COMMON',
    'fungible_common',
  ]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenQueryFilter);
});

describe('utils buildAndValidateFilters serialnumbers tests', () => {
  const key = filterKeys.SERIAL_NUMBER;
  const invalidQueries = makeQueries(key, [
    // invalid format
    '-1',
    'deb',
    // erroneous data
    '0',
  ]);
  const validQueries = makeQueries(key, [
    '1',
    '21',
    '9007199254740991',
    'eq:324',
    'gt:324',
    'gte:324',
    'lt:324',
    'lte:324',
  ]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenQueryFilter);
});

describe('utils validateAndParseFilters account.id tests', () => {
  const key = filterKeys.ACCOUNT_ID;
  const invalidQueries = makeQueries(key, [
    // invalid format
    'L',
    '@.#.$',
    // erroneous data
    '-1',
    '0.1.2.3',
    '-1.-1.-1',
  ]);
  const validQueries = makeQueries(key, [
    '1001',
    '0.1001',
    '0.0.1001',
    '21',
    '1234567890',
    'eq:0.0.1001',
    'lt:0.0.1001',
    'lte:0.0.1001',
    'gt:0.0.1001',
    'gte:0.0.1001',
    'eq:1001',
    'lt:324',
    'lte:324',
    'gt:324',
    'gte:324',
  ]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenQueryFilter);
});

describe('utils buildAndValidateFilters token info query timestamp filter tests', () => {
  const key = filterKeys.TIMESTAMP;
  const invalidQueries = makeQueries(key, ['abc', '', 'ne:1234', 'gt:1234', 'gte:1234']).concat(
    makeQueries(filterKeys.SERIAL_NUMBER, ['123456'])
  );
  const validQueries = makeQueries(key, ['1', '123456789.000111', ['1', 'lt:123456789.000111']]);

  verifyInvalidAndValidTokensFilters(invalidQueries, validQueries, tokens.validateTokenInfoFilter);
});

describe('token extractSqlFromNftTokensRequest tests', () => {
  const verifyExtractSqlFromNftTokensRequest = (
    tokenId,
    pgSqlQuery,
    filters,
    expectedquery,
    expectedparams,
    expectedorder,
    expectedlimit
  ) => {
    const {query, params, order, limit} = tokens.extractSqlFromNftTokensRequest(tokenId, pgSqlQuery, filters);

    expect(formatSqlQueryString(query)).toStrictEqual(formatSqlQueryString(expectedquery));
    expect(params).toStrictEqual(expectedparams);
    expect(order).toStrictEqual(expectedorder);
    expect(limit).toStrictEqual(expectedlimit);
  };

  test('Verify simple discovery query', () => {
    const tokenId = '1009'; // encoded
    const initialQuery = tokens.nftSelectQuery;
    const filters = [];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.deleted or e.deleted as deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                           order by nft.serial_number desc
                           limit $2`;
    const expectedParams = [tokenId, maxLimit];
    const expectedOrder = orderFilterValues.DESC;
    const expectedLimit = maxLimit;

    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify account id', () => {
    const tokenId = '1009'; // encoded
    const accountId = '111'; // encoded
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [
      {
        key: filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: accountId,
      },
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.deleted or e.deleted as deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.account_id = $2
                           order by nft.serial_number desc
                           limit $3`;
    const expectedParams = [tokenId, accountId, maxLimit];
    const expectedOrder = orderFilterValues.DESC;
    const expectedLimit = maxLimit;
    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify serial number id', () => {
    const tokenId = '1009'; // encoded
    const serialFilter = 'gt:111';
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [
      {
        key: filterKeys.SERIAL_NUMBER,
        operator: ' = ',
        value: serialFilter,
      },
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.deleted or e.deleted as deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.serial_number = $2
                           order by nft.serial_number desc
                           limit $3`;
    const expectedParams = [tokenId, serialFilter, maxLimit];
    const expectedOrder = orderFilterValues.DESC;
    const expectedLimit = maxLimit;
    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });

  test('Verify all filters', () => {
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const tokenId = '1009'; // encoded
    const accountId = '5';
    const serialNum = '2';
    const limit = '3';
    const order = orderFilterValues.ASC;
    const filters = [
      {
        key: filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: accountId,
      },
      {
        key: filterKeys.SERIAL_NUMBER,
        operator: ' = ',
        value: serialNum,
      },
      {key: filterKeys.LIMIT, operator: ' = ', value: limit},
      {key: filterKeys.ORDER, operator: ' = ', value: order},
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.deleted or e.deleted as deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.account_id = $2
                             and nft.serial_number = $3
                           order by nft.serial_number asc
                           limit $4`;
    const expectedParams = [tokenId, accountId, serialNum, limit];
    const expectedOrder = order;
    const expectedLimit = 3;

    verifyExtractSqlFromNftTokensRequest(
      tokenId,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams,
      expectedOrder,
      expectedLimit
    );
  });
});

describe('token extractSqlFromNftTokenInfoRequest tests', () => {
  const verifyExtractSqlFromNftTokenInfoRequest = (
    tokenId,
    serialNumber,
    pgSqlQuery,
    filters,
    expectedquery,
    expectedparams
  ) => {
    const {query, params} = tokens.extractSqlFromNftTokenInfoRequest(tokenId, serialNumber, pgSqlQuery, filters);

    expect(formatSqlQueryString(query)).toStrictEqual(formatSqlQueryString(expectedquery));
    expect(params).toStrictEqual(expectedparams);
  };

  test('Verify simple serial query', () => {
    const tokenId = '1009'; // encoded
    const serialNumber = '960';
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.deleted or e.deleted as deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                           join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.serial_number = $2`;
    const expectedParams = [tokenId, serialNumber];
    verifyExtractSqlFromNftTokenInfoRequest(
      tokenId,
      serialNumber,
      initialQuery,
      filters,
      expectedQuery,
      expectedParams
    );
  });
});

describe('token validateSerialNumberParam tests', () => {
  const invalidSerialNums = ['', '-1', null, undefined, 'end', 0, '0'];
  const validSerialNums = [1, '1', '9007199254740991'];

  invalidSerialNums.forEach((serialNum) => {
    test(`Verify validateSerialNumberParam for invalid ${serialNum}`, () => {
      expect(() => tokens.validateSerialNumberParam(serialNum)).toThrowErrorMatchingSnapshot();
    });
  });

  validSerialNums.forEach((serialNum) => {
    test(`Verify validateSerialNumberParam for valid ${serialNum}`, () => {
      tokens.validateSerialNumberParam(serialNum);
    });
  });
});

describe('token validateTokenIdParam tests', () => {
  const invalidTokenIds = ['', '-1', null, undefined, 'end'];
  const validTokenIds = ['1', '0.1', '0.20.1'];

  invalidTokenIds.forEach((tokenId) => {
    test(`Verify validateTokenIdParam for invalid ${tokenId}`, () => {
      expect(() => tokens.validateTokenIdParam(tokenId)).toThrowErrorMatchingSnapshot();
    });
  });

  validTokenIds.forEach((tokenId) => {
    test(`Verify validateTokenIdParam for valid ${tokenId}`, () => {
      tokens.validateTokenIdParam(tokenId);
    });
  });
});

describe('token extractSqlFromNftTransferHistoryRequest tests', () => {
  TransactionTypeService.populateTransactionTypeMaps([{entityType: 5, name: 'TOKENDELETION', protoId: 35}]);
  TransactionResultService.populateTransactionResultMaps([{result: 'SUCCESS', protoId: 22}]);

  const verifyExtractSqlFromNftTransferHistoryRequest = (
    tokenId,
    serialNumber,
    transferQuery,
    deletedQuery,
    expectedQuery,
    expectedParams,
    filters
  ) => {
    const {query, params} = tokens.extractSqlFromNftTransferHistoryRequest(
      tokenId,
      serialNumber,
      transferQuery,
      deletedQuery,
      filters
    );

    expect(formatSqlQueryString(query)).toStrictEqual(formatSqlQueryString(expectedQuery));
    expect(params).toStrictEqual(expectedParams);
  };

  test('Verify simple query', () => {
    const tokenId = '1009'; // encoded
    const serialNumber = '960';
    const transferQuery = [tokens.nftTransferHistorySelectQuery].join('\n');
    const deletedQuery = [tokens.nftDeleteHistorySelectQuery].join('\n');
    const filters = [];

    const expectedQuery = `with serial_transfers as (
        select nft_tr.consensus_timestamp,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          nft_tr.token_id
        from nft_transfer nft_tr
        where nft_tr.token_id = $1 and nft_tr.serial_number = $2
      ),
      token_transactions as (
        select nft_tr.consensus_timestamp,
          t.payer_account_id,
          t.valid_start_ns,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          t.type
        from serial_transfers nft_tr
        join transaction t on nft_tr.consensus_timestamp = t.consensus_timestamp and nft_tr.token_id = t.entity_id
      ),
      token_transfers as (
        select nft_tr.consensus_timestamp,
          t.payer_account_id,
          t.valid_start_ns,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          t.type
        from serial_transfers nft_tr
        join transaction t on nft_tr.consensus_timestamp = t.consensus_timestamp and t.entity_id is null
      )
      select *
      from token_transactions
      union
      select *
      from token_transfers
      union
      select t.consensus_timestamp as consensus_timestamp,
        t.payer_account_id,
        t.valid_start_ns,
        null as receiver_account_id,
        null as sender_account_id,
        t.type
      from transaction t
      where t.entity_id = $1 and t.type = 35 and t.result = 22
      order by consensus_timestamp desc
      limit $3`;
    const expectedParams = [tokenId, serialNumber, maxLimit];
    verifyExtractSqlFromNftTransferHistoryRequest(
      tokenId,
      serialNumber,
      transferQuery,
      deletedQuery,
      expectedQuery,
      expectedParams,
      filters
    );
  });

  test('Verify limit and order query', () => {
    const tokenId = '1009'; // encoded
    const serialNumber = '960';
    const transferQuery = [tokens.nftTransferHistorySelectQuery].join('\n');
    const deletedQuery = [tokens.nftDeleteHistorySelectQuery].join('\n');
    const limit = '3';
    const order = orderFilterValues.ASC;
    const filters = [
      {key: filterKeys.LIMIT, operator: ' = ', value: limit},
      {key: filterKeys.ORDER, operator: ' = ', value: order},
    ];
    const expectedQuery = `with serial_transfers as (
        select nft_tr.consensus_timestamp,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          nft_tr.token_id
        from nft_transfer nft_tr
        where nft_tr.token_id = $1 and nft_tr.serial_number = $2
      ),
      token_transactions as (
        select nft_tr.consensus_timestamp,
          t.payer_account_id,
          t.valid_start_ns,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          t.type
        from serial_transfers nft_tr
        join transaction t on nft_tr.consensus_timestamp = t.consensus_timestamp and nft_tr.token_id = t.entity_id
      ),
      token_transfers as (
        select nft_tr.consensus_timestamp,
          t.payer_account_id,
          t.valid_start_ns,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          t.type
        from serial_transfers nft_tr
        join transaction t on nft_tr.consensus_timestamp = t.consensus_timestamp and t.entity_id is null
      )
      select *
      from token_transactions
      union
      select *
      from token_transfers
      union
      select t.consensus_timestamp as consensus_timestamp,
        t.payer_account_id,
        t.valid_start_ns,
        null as receiver_account_id,
        null as sender_account_id,
        t.type
      from transaction t
      where t.entity_id = $1 and t.type = 35 and t.result = 22
      order by consensus_timestamp asc
      limit $3`;
    const expectedParams = [tokenId, serialNumber, limit];
    verifyExtractSqlFromNftTransferHistoryRequest(
      tokenId,
      serialNumber,
      transferQuery,
      deletedQuery,
      expectedQuery,
      expectedParams,
      filters
    );
  });

  test('Verify timestamp query', () => {
    const tokenId = '1009'; // encoded
    const serialNumber = '960';
    const transferQuery = [tokens.nftTransferHistorySelectQuery].join('\n');
    const deletedQuery = [tokens.nftDeleteHistorySelectQuery].join('\n');
    const timestamp = 5;
    const filters = [{key: filterKeys.TIMESTAMP, operator: ' > ', value: timestamp}];
    const expectedQuery = `with serial_transfers as (
        select nft_tr.consensus_timestamp,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          nft_tr.token_id
        from nft_transfer nft_tr
        where nft_tr.token_id = $1 and nft_tr.serial_number = $2 and nft_tr.consensus_timestamp > $3
      ),
      token_transactions as (
        select nft_tr.consensus_timestamp,
          t.payer_account_id,
          t.valid_start_ns,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          t.type
        from serial_transfers nft_tr
        join transaction t on nft_tr.consensus_timestamp = t.consensus_timestamp and nft_tr.token_id = t.entity_id
      ),
      token_transfers as (
        select nft_tr.consensus_timestamp,
          t.payer_account_id,
          t.valid_start_ns,
          nft_tr.receiver_account_id,
          nft_tr.sender_account_id,
          t.type
        from serial_transfers nft_tr
        join transaction t on nft_tr.consensus_timestamp = t.consensus_timestamp and t.entity_id is null
      )
      select *
      from token_transactions
      union
      select *
      from token_transfers
      union
      select t.consensus_timestamp as consensus_timestamp,
        t.payer_account_id,
        t.valid_start_ns,
        null as receiver_account_id,
        null as sender_account_id,
        t.type
      from transaction t
      where t.entity_id = $1 and t.type = 35 and t.result = 22 and t.consensus_timestamp > $3
      order by consensus_timestamp desc
      limit $4`;
    const expectedParams = [tokenId, serialNumber, timestamp, maxLimit];
    verifyExtractSqlFromNftTransferHistoryRequest(
      tokenId,
      serialNumber,
      transferQuery,
      deletedQuery,
      expectedQuery,
      expectedParams,
      filters
    );
  });
});

describe('token extractSqlFromTokenInfoRequest tests', () => {
  const getExpectedQuery = (timestampCondition = '') => {
    return `select e.auto_renew_account_id,
                   e.auto_renew_period,
                   t.created_timestamp,
                   decimals,
                   e.deleted,
                   e.expiration_timestamp,
                   fee_schedule_key,
                   freeze_default,
                   freeze_key,
                   initial_supply,
                   e.key,
                   kyc_key,
                   max_supply,
                   e.memo,
                   t.modified_timestamp,
                   name,
                   pause_key,
                   pause_status,
                   supply_key,
                   supply_type,
                   symbol,
                   token_id,
                   total_supply,
                   treasury_account_id,
                   t.type,
                   wipe_key,
                   (select jsonb_agg(jsonb_build_object(
                     'amount', amount,
                     'amount_denominator', amount_denominator,
                     'collector_account_id', collector_account_id::text,
                     'created_timestamp', created_timestamp::text,
                     'denominating_token_id', denominating_token_id::text,
                     'maximum_amount', maximum_amount,
                     'minimum_amount', minimum_amount,
                     'net_of_transfers', net_of_transfers,
                     'royalty_denominator', royalty_denominator,
                     'royalty_numerator', royalty_numerator,
                     'token_id', token_id::text
                    ) order by collector_account_id, denominating_token_id, amount, royalty_numerator)
                    from custom_fee cf
                    where token_id = $1 ${timestampCondition && 'and ' + timestampCondition}
                    group by cf.created_timestamp
                    order by cf.created_timestamp desc
                    limit 1
                   ) as custom_fees
            from token t
            join entity e on e.id = t.token_id
            where token_id = $1`;
  };

  const verifyExtractSqlFromTokenInfoRequest = (tokenId, filters, expectedQuery, expectedParams) => {
    const {query, params} = tokens.extractSqlFromTokenInfoRequest(tokenId, filters);

    expect(formatSqlQueryString(query)).toStrictEqual(formatSqlQueryString(expectedQuery));
    expect(params).toStrictEqual(expectedParams);
  };

  const timestamp = '123456789000111222';
  const encodedTokenId = '1009'; // encoded

  test('Verify simple query', () => {
    verifyExtractSqlFromTokenInfoRequest(encodedTokenId, [], getExpectedQuery(), [encodedTokenId]);
  });

  [opsMap.lt, opsMap.lte].forEach((op) =>
    test(`Verify query with timestamp and op ${op}`, () => {
      const expectedQuery = getExpectedQuery(`cf.created_timestamp ${op} $2`);
      const expectedParams = [encodedTokenId, timestamp];
      const filters = [
        {
          key: filterKeys.TIMESTAMP,
          operator: op,
          value: timestamp,
        },
      ];

      verifyExtractSqlFromTokenInfoRequest(encodedTokenId, filters, expectedQuery, expectedParams);
    })
  );

  test('Verify query with multiple timestamp filters', () => {
    const expectedQuery = getExpectedQuery('cf.created_timestamp <= $2');
    const expectedParams = [encodedTokenId, timestamp];
    // honor the last one
    const filters = [
      {
        key: filterKeys.TIMESTAMP,
        operator: opsMap.lt,
        value: timestamp,
      },
      {
        key: filterKeys.TIMESTAMP,
        operator: opsMap.lte,
        value: timestamp,
      },
    ];

    verifyExtractSqlFromTokenInfoRequest(encodedTokenId, filters, expectedQuery, expectedParams);
  });
});
