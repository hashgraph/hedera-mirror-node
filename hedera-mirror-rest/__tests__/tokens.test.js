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

const tokens = require('../tokens.js');
const constants = require('../constants.js');
const config = require('../config.js');

beforeAll(async () => {
  jest.setTimeout(1000);
});

afterAll(() => {});

describe('token formatTokenRow tests', () => {
  const rowInput = {
    key: [3, 3, 3],
    public_key: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
    symbol: 'YBTJBOAZ',
    token_id: 7,
  };

  let expectedFormat = {
    token_id: '0.0.7',
    symbol: 'YBTJBOAZ',
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
  };

  test('Verify formatTokenRow', () => {
    let formattedInput = tokens.formatTokenRow(rowInput);

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
    const expectedparams = [config.maxLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = config.maxLimit;

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
    const expectedparams = ['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', config.maxLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = config.maxLimit;

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
    const expectedparams = [5, config.maxLimit];
    const expectedorder = constants.orderFilterValues.ASC;
    const expectedlimit = config.maxLimit;

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
  let {query, params, order, limit} = tokens.extractSqlFromTokenRequest(
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
