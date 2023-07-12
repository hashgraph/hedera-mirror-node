/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import {getResponseLimit} from '../config';
import * as constants from '../constants';
import * as utils from '../utils';
import {opsMap} from '../utils';
import {assertSqlQueryEqual} from './testutils';
import tokens from '../tokens';

const {default: defaultLimit} = getResponseLimit();

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
    const expectedparams = [defaultLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = defaultLimit;

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
        key: constants.filterKeys.ENTITY_PUBLICKEY,
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
    const expectedparams = ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', defaultLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = defaultLimit;

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
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: '5',
      },
    ];

    const expectedquery = `with ta as (
                             select *
                             from token_account
                             where account_id = $1
                             order by token_id
                           )
                           select t.token_id, symbol, e.key, t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true
                           order by t.token_id asc
                           limit $2`;
    const expectedparams = [5, defaultLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = defaultLimit;

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
        key: constants.filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
    ];

    const expectedquery = `with ta as (
                             select *
                             from token_account
                             where account_id = $1
                             order by token_id
                           )
                           select t.token_id, symbol, e.key, t.type
                           from token t
                                  join ta on ta.token_id = t.token_id
                                  join entity e on e.id = t.token_id
                           where ta.associated is true and t.type = $2
                           order by t.token_id asc
                           limit $3`;
    const expectedparams = [5, tokenType, defaultLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = defaultLimit;

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
      {
        key: constants.filterKeys.TOKEN_TYPE,
        operator: ' = ',
        value: tokenType,
      },
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
    ];

    const expectedquery = `with ta as (
                             select *
                             from token_account
                             where account_id = $1
                             order by token_id
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
    const expectedorder = constants.orderFilterValues.DESC;
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

  assertSqlQueryEqual(query, expectedquery);
  expect(params).toStrictEqual(expectedparams);
  expect(order).toStrictEqual(expectedorder);
  expect(limit).toStrictEqual(expectedlimit);
};

describe('token formatNftHistoryRow', () => {
  const defaultRow = {
    consensus_timestamp: 123456000987654,
    nonce: 0,
    nft_transfer: [
      {
        is_approval: true,
        receiver_account_id: 2000,
        serial_number: 1,
        sender_account_id: 3000,
        token_id: 4000,
      },
    ],
    payer_account_id: '500',
    type: 14,
    valid_start_ns: 123450000111222,
    is_approval: true,
  };
  const expected = {
    consensus_timestamp: '123456.000987654',
    nonce: 0,
    receiver_account_id: '0.0.2000',
    sender_account_id: '0.0.3000',
    transaction_id: '0.0.500-123450-000111222',
    type: 'CRYPTOTRANSFER',
    is_approval: true,
  };

  const testSpecs = [
    {
      name: 'default',
      row: defaultRow,
      expected: expected,
    },
    {
      name: 'nonce',
      row: {
        ...defaultRow,
        nonce: 1,
      },
      expected: {
        ...expected,
        nonce: 1,
      },
    },
    {
      name: 'tokendeletion',
      row: {
        ...defaultRow,
        nft_transfer: [
          {
            is_approval: false,
            receiver_account_id: null,
            serial_number: 1,
            sender_account_id: null,
            token_id: 4000,
          },
        ],
        type: 35,
      },
      expected: {
        ...expected,
        is_approval: false,
        receiver_account_id: null,
        sender_account_id: null,
        type: 'TOKENDELETION',
      },
    },
  ];

  for (const testSpec of testSpecs) {
    test(testSpec.name, () => {
      expect(tokens.formatNftHistoryRow(testSpec.row)).toEqual(testSpec.expected);
    });
  }
});

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
      filters: [],
      expected: {
        query: `
          select ti.account_id,
                 ti.balance,
                 (select max(consensus_end) from record_file) as consensus_timestamp
          from token_account ti
          where ti.token_id = $1
          order by ti.account_id desc
          limit $2`,
        params: [tokenId, defaultLimit],
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: `timestamp > ${timestampNsLow} and timestamp < ${timestampNsHigh}`,
      tokenId,
      filters: [
        {
          key: constants.filterKeys.TIMESTAMP,
          operator: '>',
          value: timestampNsLow,
        },
        {
          key: constants.filterKeys.TIMESTAMP,
          operator: '<',
          value: timestampNsHigh,
        },
      ],
      expected: {
        query: `
          select ti.account_id,
                 ti.balance,
                 ti.consensus_timestamp
          from token_balance ti
          where ti.token_id = $1
            and ti.consensus_timestamp = (
            select ti.consensus_timestamp
            from token_balance ti
            where ti.consensus_timestamp > $2
              and ti.consensus_timestamp < $3
            order by ti.consensus_timestamp desc
            limit 1
          )
          order by ti.account_id desc
          limit $4`,
        params: [tokenId, timestampNsLow, timestampNsHigh, defaultLimit],
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    ...operators.map((op) => {
      return {
        name: `timestamp ${op} ${timestampNsLow}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: op,
            value: timestampNsLow,
          },
        ],
        expected: {
          query: `
            select ti.account_id,
                   ti.balance,
                   ti.consensus_timestamp
            from token_balance ti
            where ti.token_id = $1
              and ti.consensus_timestamp = (
              select ti.consensus_timestamp
              from token_balance ti
              where ti.consensus_timestamp ${op !== opsMap.eq ? op : '<='}
                $2
              order by ti.consensus_timestamp desc
              limit 1)
            order by ti.account_id desc
            limit $3`,
          params: [tokenId, timestampNsLow, defaultLimit],
          order: constants.orderFilterValues.DESC,
          limit: defaultLimit,
        },
      };
    }),
    {
      name: 'limit = 30',
      tokenId,
      filters: [
        {
          key: constants.filterKeys.LIMIT,
          operator: opsMap.eq,
          value: 30,
        },
      ],
      expected: {
        query: `
            select ti.account_id,
                   ti.balance,
                   (select max(consensus_end) from record_file) as consensus_timestamp
            from token_account ti
            where ti.token_id = $1
            order by ti.account_id desc
            limit $2`,
        params: [tokenId, 30],
        order: constants.orderFilterValues.DESC,
        limit: 30,
      },
    },
    ...operators.map((op) => {
      return {
        name: `account.id ${op} ${accountId}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ACCOUNT_ID,
            operator: op,
            value: accountId,
          },
        ],
        expected: {
          query: `
            select ti.account_id,
                   ti.balance,
                   (select max(consensus_end) from record_file) as consensus_timestamp
            from token_account ti
            where ti.token_id = $1
              and ti.account_id ${op} $2
            order by ti.account_id desc
            limit $3`,
          params: [tokenId, accountId, defaultLimit],
          order: constants.orderFilterValues.DESC,
          limit: defaultLimit,
        },
      };
    }),
    ...operators.map((op) => {
      return {
        name: `balance ${op} ${balance}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ACCOUNT_BALANCE,
            operator: op,
            value: balance,
          },
        ],
        expected: {
          query: `
            select ti.account_id,
                   ti.balance,
                   (select max(consensus_end) from record_file) as consensus_timestamp
            from token_account ti
            where ti.token_id = $1
              and ti.balance ${op} $2
            order by ti.account_id desc
            limit $3`,
          params: [tokenId, balance, defaultLimit],
          order: constants.orderFilterValues.DESC,
          limit: defaultLimit,
        },
      };
    }),
    ...operators.map((op) => {
      const timestamp = '12345';
      return {
        name: `balance ${op} ${balance} and timestamp ${timestamp}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ACCOUNT_BALANCE,
            operator: op,
            value: balance,
          },
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: opsMap.eq,
            value: timestamp,
          },
        ],
        expected: {
          query: `
            select ti.account_id,
                   ti.balance,
                   ti.consensus_timestamp
            from token_balance ti
            where ti.token_id = $1
              and ti.balance ${op} $2
              and ti.consensus_timestamp = (
              select ti.consensus_timestamp
              from token_balance ti
              where ti.consensus_timestamp <= $3
              order by ti.consensus_timestamp desc
              limit 1
              )
            order by ti.account_id desc
            limit $4`,
          params: [tokenId, balance, timestamp, defaultLimit],
          order: constants.orderFilterValues.DESC,
          limit: defaultLimit,
        },
      };
    }),
    ...Object.values(constants.orderFilterValues).map((order) => {
      return {
        name: `order ${order}`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: opsMap.eq,
            value: order,
          },
        ],
        expected: {
          query: `
            select ti.account_id,
                   ti.balance,
                   (select max(consensus_end) from record_file) as consensus_timestamp
            from token_account ti
            where ti.token_id = $1
            order by ti.account_id ${order}
            limit $2`,
          params: [tokenId, defaultLimit],
          order,
          limit: defaultLimit,
        },
      };
    }),
    ...Object.values(constants.orderFilterValues).map((order) => {
      const timestamp = '12345';
      return {
        name: `order ${order} timestamp 12345`,
        tokenId,
        filters: [
          {
            key: constants.filterKeys.ORDER,
            operator: opsMap.eq,
            value: order,
          },
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: opsMap.eq,
            value: timestamp,
          },
        ],
        expected: {
          query: `
            select ti.account_id,
                   ti.balance,
                   ti.consensus_timestamp
            from token_balance ti
            where ti.token_id = $1
              and ti.consensus_timestamp = (
              select ti.consensus_timestamp
              from token_balance ti
              where ti.consensus_timestamp <= $2
              order by ti.consensus_timestamp desc
              limit 1
              )
            order by ti.account_id ${order}
            limit $3`,
          params: [tokenId, timestamp, defaultLimit],
          order,
          limit: defaultLimit,
        },
      };
    }),
    {
      name: `account publickey "${publicKey}"`,
      tokenId,
      filters: [
        {
          key: constants.filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
      ],
      expected: {
        query: `
          select ti.account_id,
                 ti.balance,
                 (select max(consensus_end) from record_file) as consensus_timestamp
          from token_account ti
                 join entity e
                      on e.type = '${constants.entityTypes.ACCOUNT}'
                        and e.id = ti.account_id
                        and e.public_key = $2
          where ti.token_id = $1
          order by ti.account_id desc
          limit $3`,
        params: [tokenId, publicKey, defaultLimit],
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: `account publickey "${publicKey}" timestamp `,
      tokenId,
      filters: [
        {
          key: constants.filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
        {
          key: constants.filterKeys.TIMESTAMP,
          operator: opsMap.eq,
          value: timestampNsLow,
        },
      ],
      expected: {
        query: `
          select ti.account_id,
                 ti.balance,
                 ti.consensus_timestamp
          from token_balance ti
                 join entity e
                      on e.type = '${constants.entityTypes.ACCOUNT}'
                        and e.id = ti.account_id
                        and e.public_key = $2
          where ti.token_id = $1
            and ti.consensus_timestamp = (
            select ti.consensus_timestamp
            from token_balance ti
            where ti.consensus_timestamp <= $3
            order by ti.consensus_timestamp desc
            limit 1
          )
          order by ti.account_id desc
          limit $4`,
        params: [tokenId, publicKey, timestampNsLow, defaultLimit],
        order: constants.orderFilterValues.DESC,
        limit: defaultLimit,
      },
    },
    {
      name: 'all filters',
      tokenId,
      filters: [
        {
          key: constants.filterKeys.ACCOUNT_ID,
          operator: opsMap.eq,
          value: accountId,
        },
        {
          key: constants.filterKeys.ACCOUNT_BALANCE,
          operator: opsMap.eq,
          value: balance,
        },
        {
          key: constants.filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
        {
          key: constants.filterKeys.LIMIT,
          operator: opsMap.eq,
          value: 1,
        },
        {
          key: constants.filterKeys.ORDER,
          operator: opsMap.eq,
          value: constants.orderFilterValues.ASC,
        },
        {
          key: constants.filterKeys.TIMESTAMP,
          operator: opsMap.eq,
          value: timestampNsLow,
        },
      ],
      expected: {
        query: `
          select ti.account_id,
                 ti.balance,
                 ti.consensus_timestamp
          from token_balance ti
                 join entity e
                      on e.type = '${constants.entityTypes.ACCOUNT}'
                        and e.id = ti.account_id
                        and e.public_key = $4
          where ti.token_id = $1
            and ti.account_id = $2
            and ti.balance = $3
            and ti.consensus_timestamp = (
            select ti.consensus_timestamp
            from token_balance ti
            where ti.consensus_timestamp <= $5
            order by ti.consensus_timestamp desc
            limit 1
          )
          order by ti.account_id asc
          limit $6`,
        params: [tokenId, accountId, balance, publicKey, timestampNsLow, 1],
        order: constants.orderFilterValues.ASC,
        limit: 1,
      },
    },
    {
      name: 'all filters except timestamp',
      tokenId,
      filters: [
        {
          key: constants.filterKeys.ACCOUNT_ID,
          operator: opsMap.eq,
          value: accountId,
        },
        {
          key: constants.filterKeys.ACCOUNT_BALANCE,
          operator: opsMap.eq,
          value: balance,
        },
        {
          key: constants.filterKeys.ACCOUNT_PUBLICKEY,
          operator: opsMap.eq,
          value: publicKey,
        },
        {
          key: constants.filterKeys.LIMIT,
          operator: opsMap.eq,
          value: 1,
        },
        {
          key: constants.filterKeys.ORDER,
          operator: opsMap.eq,
          value: constants.orderFilterValues.ASC,
        },
      ],
      expected: {
        query: `
          select ti.account_id,
                 ti.balance,
                 (select max(consensus_end) from record_file) as consensus_timestamp
          from token_account ti
                 join entity e
                      on e.type = '${constants.entityTypes.ACCOUNT}'
                        and e.id = ti.account_id
                        and e.public_key = $4
          where ti.token_id = $1
            and ti.account_id = $2
            and ti.balance = $3
          order by ti.account_id asc
          limit $5`,
        params: [tokenId, accountId, balance, publicKey, 1],
        order: constants.orderFilterValues.ASC,
        limit: 1,
      },
    },
  ];

  for (const spec of testSpecs) {
    const {name, tokenId, filters, expected} = spec;
    test(name, () => {
      const actual = tokens.extractSqlFromTokenBalancesRequest(tokenId, filters);
      assertSqlQueryEqual(actual.query, expected.query);
      expect(actual).toEqual(
        expect.objectContaining({
          params: spec.expected.params,
          order: spec.expected.order,
          limit: spec.expected.limit,
        })
      );
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
    decimals: '10',
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
    initial_supply: '1000000',
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
    total_supply: '2000000',
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
  const acceptedParameters = new Set([
    constants.filterKeys.ACCOUNT_ID,
    constants.filterKeys.TIMESTAMP,
    constants.filterKeys.SERIAL_NUMBER,
    constants.filterKeys.TOKEN_TYPE,
  ]);

  invalidQueries.forEach((query) => {
    test(`Verify buildAndValidateFilters for invalid ${JSON.stringify(query)}`, () => {
      expect(() => utils.buildAndValidateFilters(query, acceptedParameters, validator)).toThrowErrorMatchingSnapshot();
    });
  });

  validQueries.forEach((query) => {
    test(`Verify buildAndValidateFilters for valid ${JSON.stringify(query)}`, () => {
      utils.buildAndValidateFilters(query, acceptedParameters, validator);
    });
  });
};

const makeQueries = (key, values) => {
  return values.map((v) => ({[key]: v}));
};

describe('utils buildAndValidateFilters token type tests', () => {
  const key = constants.filterKeys.TOKEN_TYPE;
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
  const key = constants.filterKeys.SERIAL_NUMBER;
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
  const key = constants.filterKeys.ACCOUNT_ID;
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
  const key = constants.filterKeys.TIMESTAMP;
  const invalidQueries = makeQueries(key, ['abc', '', 'ne:1234', 'gt:1234', 'gte:1234']).concat(
    makeQueries(constants.filterKeys.SERIAL_NUMBER, ['123456'])
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

    assertSqlQueryEqual(query, expectedquery);
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
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                           order by nft.serial_number desc
                           limit $2`;
    const expectedParams = [tokenId, defaultLimit];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = defaultLimit;

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
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: accountId,
      },
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.account_id = $2
                           order by nft.serial_number desc
                           limit $3`;
    const expectedParams = [tokenId, accountId, defaultLimit];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = defaultLimit;
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
        key: constants.filterKeys.SERIAL_NUMBER,
        operator: ' = ',
        value: serialFilter,
      },
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
                           where nft.token_id = $1
                             and nft.serial_number = $2
                           order by nft.serial_number desc
                           limit $3`;
    const expectedParams = [tokenId, serialFilter, defaultLimit];
    const expectedOrder = constants.orderFilterValues.DESC;
    const expectedLimit = defaultLimit;
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
    const order = constants.orderFilterValues.ASC;
    const filters = [
      {
        key: constants.filterKeys.ACCOUNT_ID,
        operator: ' = ',
        value: accountId,
      },
      {
        key: constants.filterKeys.SERIAL_NUMBER,
        operator: ' = ',
        value: serialNum,
      },
      {key: constants.filterKeys.LIMIT, operator: ' = ', value: limit},
      {key: constants.filterKeys.ORDER, operator: ' = ', value: order},
    ];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                                  left join entity e on e.id = nft.token_id
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

    assertSqlQueryEqual(query, expectedquery);
    expect(params).toStrictEqual(expectedparams);
  };

  test('Verify simple serial query', () => {
    const tokenId = '1009'; // encoded
    const serialNumber = '960';
    const initialQuery = [tokens.nftSelectQuery].join('\n');
    const filters = [];

    const expectedQuery = `select nft.account_id,
                                  nft.created_timestamp,
                                  nft.delegating_spender,
                                  nft.deleted or coalesce(e.deleted, false) as deleted,
                                  nft.metadata,
                                  nft.serial_number,
                                  nft.spender,
                                  nft.timestamp_range,
                                  nft.token_id
                           from nft
                           left join entity e on e.id = nft.token_id
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

describe('token extractSqlFromNftTransferHistoryRequest tests', () => {
  const getExpectedQuery = (order = constants.orderFilterValues.DESC, timestampFilters = []) => {
    let paramIndex = 4;
    const nftTimestampCondition = timestampFilters
      .map((f) => `lower(timestamp_range) ${f.operator} $${paramIndex++}`)
      .join(' and ');
    const deleteTimestampCondition = nftTimestampCondition.replace('lower(timestamp_range)', 'consensus_timestamp');
    const limitQuery = `limit $${paramIndex}`;
    return `with nft_event as (
      select lower(timestamp_range) as timestamp
      from nft
      where token_id = $2 and serial_number = $3 ${nftTimestampCondition && 'and ' + nftTimestampCondition}
      union all
      select lower(timestamp_range) as timestamp
      from nft_history
      where token_id = $2 and serial_number = $3 ${nftTimestampCondition && 'and ' + nftTimestampCondition}
      order by timestamp ${order}
      ${limitQuery}
    ), nft_transaction as (
      select
        consensus_timestamp,
        (select jsonb_path_query_array(
          nft_transfer,
          '$[*] ? (@.token_id == $token_id && @.serial_number == $serial_number)',
          $1)
        ) as nft_transfer,
        nonce,
        payer_account_id,
        type,
        valid_start_ns
      from transaction
      join nft_event on timestamp = consensus_timestamp
    ), token_deletion as (
      select
        consensus_timestamp,
        jsonb_build_array(jsonb_build_object(
          'is_approval', false,
          'receiver_account_id', null,
          'sender_account_id', null,
          'serial_number', $3,
          'token_id', $2)) as nft_transfer,
        nonce,
        payer_account_id,
        type,
        valid_start_ns
      from transaction
      where consensus_timestamp = (select lower(timestamp_range) from entity where id = $2 and deleted is true)
        ${deleteTimestampCondition && 'and ' + deleteTimestampCondition}
    )
    select * from nft_transaction
    union all
    select * from token_deletion
    order by consensus_timestamp ${order}
    ${limitQuery}`;
  };

  const tokenId = 1009; // encoded
  const serialNumber = 9886299254743552n; // larger than MAX_SAFE_INTEGER
  const defaultParams = [`{"token_id":${tokenId},"serial_number":${serialNumber}}`, tokenId, serialNumber];

  test('Verify simple query', () => {
    const expectedQuery = getExpectedQuery();
    const expectedParams = [...defaultParams, defaultLimit];

    const actual = tokens.extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, []);
    assertSqlQueryEqual(actual.query, expectedQuery);
    expect(actual.params).toStrictEqual(expectedParams);
  });

  test('Verify limit and order query', () => {
    const limit = '3';
    const order = constants.orderFilterValues.ASC;
    const filters = [
      {key: constants.filterKeys.LIMIT, operator: utils.opsMap.eq, value: limit},
      {key: constants.filterKeys.ORDER, operator: utils.opsMap.eq, value: order},
    ];

    const expectedQuery = getExpectedQuery(order);
    const expectedParams = [...defaultParams, limit];

    const actual = tokens.extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, filters);
    assertSqlQueryEqual(actual.query, expectedQuery);
    expect(actual.params).toStrictEqual(expectedParams);
  });

  test('Verify timestamp query', () => {
    const timestamp = 5;
    const filters = [{key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.gt, value: timestamp}];

    const expectedQuery = getExpectedQuery(constants.orderFilterValues.DESC, filters);
    const expectedParams = [...defaultParams, timestamp, defaultLimit];

    const actual = tokens.extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, filters);
    assertSqlQueryEqual(actual.query, expectedQuery);
    expect(actual.params).toStrictEqual(expectedParams);
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
                   lower(t.timestamp_range) as modified_timestamp,
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
                     'all_collectors_are_exempt', all_collectors_are_exempt,
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

    assertSqlQueryEqual(query, expectedQuery);
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
          key: constants.filterKeys.TIMESTAMP,
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
        key: constants.filterKeys.TIMESTAMP,
        operator: opsMap.lt,
        value: timestamp,
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: opsMap.lte,
        value: timestamp,
      },
    ];

    verifyExtractSqlFromTokenInfoRequest(encodedTokenId, filters, expectedQuery, expectedParams);
  });
});
