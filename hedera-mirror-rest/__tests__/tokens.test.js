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
const TransactionTypesService = require('../services/transactionTypesService');

const formatSqlQueryString = (query) => {
  return query.trim().replace(/\n/g, ' ').replace(/\(\s+/g, '(').replace(/\s+\)/g, ')').replace(/\s+/g, ' ');
};

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
    const formattedInput = tokens.formatTokenRow(rowInput);

    expect(formattedInput.token_id).toStrictEqual(expectedFormat.token_id);
    expect(formattedInput.symbol).toStrictEqual(expectedFormat.symbol);
    expect(JSON.stringify(formattedInput.admin_key)).toStrictEqual(JSON.stringify(expectedFormat.admin_key));
  });
});

describe('token extractSqlFromTokenRequest tests', () => {
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
    const extraConditions = ['ta.associated is true'];
    const initialQuery = [tokens.tokensSelectQuery, tokens.accountIdJoinQuery, tokens.entityIdJoinQuery].join('\n');
    const initialParams = [5];
    const filters = [
      {
        key: filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
    ];

    const expectedquery = `select t.token_id, symbol, e.key, t.type
                           from token t
                                  join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id
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
      extraConditions,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify token type filter', () => {
    const extraConditions = ['ta.associated is true'];
    const initialQuery = [tokens.tokensSelectQuery, tokens.accountIdJoinQuery, tokens.entityIdJoinQuery].join('\n');
    const initialParams = [5];
    const tokenType = 'NON_FUNGIBLE_UNIQUE';
    const filters = [
      {
        key: filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
    ];

    const expectedquery = `select t.token_id, symbol, e.key, t.type
                           from token t
                                  join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true
                             and t.type = $2
                           order by t.token_id asc
                           limit $3`;
    const expectedparams = [5, tokenType, maxLimit];
    const expectedorder = orderFilterValues.ASC;
    const expectedlimit = maxLimit;

    verifyExtractSqlFromTokenRequest(
      initialQuery,
      initialParams,
      filters,
      extraConditions,
      expectedquery,
      expectedparams,
      expectedorder,
      expectedlimit
    );
  });

  test('Verify all filters', () => {
    const initialQuery = [tokens.tokensSelectQuery, tokens.accountIdJoinQuery, tokens.entityIdJoinQuery].join('\n');
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

    const expectedquery = `select t.token_id, symbol, e.key, t.type
                           from token t
                                  join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id
                                  join entity e on e.id = t.token_id
                           where e.public_key = $2
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
      [],
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
                      on e.type = 1
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
                      on e.type = 1
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
    freeze_key: [3, 3, 3],
    supply_key: [4, 4, 4],
    wipe_key: [5, 5, 5],
    created_timestamp: 10,
    decimals: 10,
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
  };

  const expectedFormat = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '010101',
    },
    auto_renew_account: '0.0.98',
    auto_renew_period: 7890000,
    created_timestamp: '0.000000010',
    decimals: 10,
    expiry_timestamp: 1594431063696143000,
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
    modified_timestamp: '1603394416.676293000',
    name: 'Token name',
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
  };

  test('Verify formatTokenRow', () => {
    const formattedInput = tokens.formatTokenInfoRow(rowInput);

    expect(JSON.stringify(formattedInput)).toStrictEqual(JSON.stringify(expectedFormat));
  });
});

const verifyInvalidAndValidTokensFilters = (invalidFilters, validFilters) => {
  invalidFilters.forEach((filter) => {
    const filterString = Array.isArray(filter)
      ? `${JSON.stringify(filter[0])} ${filter.length} times`
      : `${JSON.stringify(filter)}`;
    test(`Verify validateAndParseFilters for invalid ${filterString}`, () => {
      expect(() => tokens.validateTokensFilters([filter])).toThrowErrorMatchingSnapshot();
    });
  });

  validFilters.forEach((filter) => {
    const filterString = Array.isArray(filter)
      ? `${JSON.stringify(filter[0])} ${filter.length} times`
      : `${JSON.stringify(filter)}`;
    test(`Verify validateAndParseFilters for valid ${filterString}`, () => {
      tokens.validateTokensFilters([filter]);
    });
  });
};

describe('utils validateAndParseFilters token type tests', () => {
  const key = filterKeys.TOKEN_TYPE;
  const invalidFilters = [
    // invalid format
    utils.buildComparatorFilter(key, 'cred'),
    utils.buildComparatorFilter(key, 'deb'),
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
  ];

  const validFilters = [
    utils.buildComparatorFilter(key, 'all'),
    utils.buildComparatorFilter(key, 'ALL'),
    utils.buildComparatorFilter(key, 'NON_FUNGIBLE_UNIQUE'),
    utils.buildComparatorFilter(key, 'non_fungible_unique'),
    utils.buildComparatorFilter(key, 'FUNGIBLE_COMMON'),
    utils.buildComparatorFilter(key, 'fungible_common'),
  ];

  verifyInvalidAndValidTokensFilters(invalidFilters, validFilters);
});

describe('utils validateAndParseFilters serialnumbers tests', () => {
  const key = filterKeys.SERIAL_NUMBER;
  const invalidFilters = [
    // invalid format
    utils.buildComparatorFilter(key, '-1'),
    utils.buildComparatorFilter(key, 'deb'),
    // erroneous data
    utils.buildComparatorFilter(key, '0'),
  ];

  const validFilters = [
    utils.buildComparatorFilter(key, '1'),
    utils.buildComparatorFilter(key, '21'),
    utils.buildComparatorFilter(key, '9007199254740991'),
    utils.buildComparatorFilter(key, 'eq:324'),
    utils.buildComparatorFilter(key, 'gt:324'),
    utils.buildComparatorFilter(key, 'gte:324'),
    utils.buildComparatorFilter(key, 'lt:324'),
    utils.buildComparatorFilter(key, 'lte:324'),
  ];

  verifyInvalidAndValidTokensFilters(invalidFilters, validFilters);
});

describe('utils validateAndParseFilters account.id tests', () => {
  const key = filterKeys.ACCOUNT_ID;
  const invalidFilters = [
    // invalid format
    utils.buildComparatorFilter(key, 'L'),
    utils.buildComparatorFilter(key, '@.#.$'),
    // erroneous data
    utils.buildComparatorFilter(key, '-1'),
    utils.buildComparatorFilter(key, '0.1.2.3'),
    utils.buildComparatorFilter(key, '-1.-1.-1'),
  ];

  const validFilters = [
    utils.buildComparatorFilter(key, '1001'),
    utils.buildComparatorFilter(key, '0.1001'),
    utils.buildComparatorFilter(key, '0.0.1001'),
    utils.buildComparatorFilter(key, '21'),
    utils.buildComparatorFilter(key, '1234567890'),
    utils.buildComparatorFilter(key, 'eq:0.0.1001'),
    utils.buildComparatorFilter(key, 'lt:0.0.1001'),
    utils.buildComparatorFilter(key, 'lte:0.0.1001'),
    utils.buildComparatorFilter(key, 'gt:0.0.1001'),
    utils.buildComparatorFilter(key, 'gte:0.0.1001'),
    utils.buildComparatorFilter(key, 'eq:1001'),
    utils.buildComparatorFilter(key, 'lt:324'),
    utils.buildComparatorFilter(key, 'lte:324'),
    utils.buildComparatorFilter(key, 'gt:324'),
    utils.buildComparatorFilter(key, 'gte:324'),
  ];

  verifyInvalidAndValidTokensFilters(invalidFilters, validFilters);
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
                                  nft.deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.deleted = false
                             and e.deleted != true
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
                                  nft.deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.deleted = false
                             and e.deleted != true
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
                                  nft.deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.deleted = false
                             and e.deleted != true
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
                                  nft.deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
                                  join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.deleted = false
                             and e.deleted != true
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
                                  nft.deleted,
                                  nft.metadata,
                                  nft.modified_timestamp,
                                  nft.serial_number,
                                  nft.token_id
                           from nft
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
  TransactionTypesService.populateTransactionTypeMaps([{entity_type: 5, name: 'TOKENDELETION', proto_id: 35}]);

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

    const expectedQuery = `select nft_tr.consensus_timestamp,
                                  t.payer_account_id,
                                  t.valid_start_ns,
                                  nft_tr.receiver_account_id,
                                  nft_tr.sender_account_id,
                                  t.type
                           from nft
                                  right outer join nft_transfer nft_tr
                                                   on nft.token_id = nft_tr.token_id and
                                                      nft.serial_number = nft_tr.serial_number
                                  join transaction t on nft_tr.consensus_timestamp = t.consensus_ns
                           where nft_tr.token_id = $1
                             and nft_tr.serial_number = $2
                           union
                           select t.consensus_ns as consensus_timestamp,
                                  t.payer_account_id,
                                  t.valid_start_ns,
                                  null           as receiver_account_id,
                                  null           as sender_account_id,
                                  t.type
                           from transaction t
                           where t.entity_id = $1
                             and t.type = 35
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
    const expectedQuery = `select nft_tr.consensus_timestamp,
                                  t.payer_account_id,
                                  t.valid_start_ns,
                                  nft_tr.receiver_account_id,
                                  nft_tr.sender_account_id,
                                  t.type
                           from nft
                                  right outer join nft_transfer nft_tr
                                                   on nft.token_id = nft_tr.token_id and
                                                      nft.serial_number = nft_tr.serial_number
                                  join transaction t on nft_tr.consensus_timestamp = t.consensus_ns
                           where nft_tr.token_id = $1
                             and nft_tr.serial_number = $2
                           union
                           select t.consensus_ns as consensus_timestamp,
                                  t.payer_account_id,
                                  t.valid_start_ns,
                                  null           as receiver_account_id,
                                  null           as sender_account_id,
                                  t.type
                           from transaction t
                           where t.entity_id = $1
                             and t.type = 35
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
});
