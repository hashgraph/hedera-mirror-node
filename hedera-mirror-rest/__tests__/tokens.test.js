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

  let formattedInput = tokens.formatTokenRow(rowInput);

  let expectedFormat = {
    token_id: '0.0.7',
    symbol: 'YBTJBOAZ',
    admin_key: {
      _type: 'ProtobufEncoded',
      key: '030303',
    },
  };

  expect(formattedInput.token_id).toStrictEqual(expectedFormat.token_id);
  expect(formattedInput.symbol).toStrictEqual(expectedFormat.symbol);
  expect(JSON.stringify(formattedInput.admin_key)).toStrictEqual(JSON.stringify(expectedFormat.admin_key));
});

describe('token extractSqlFromTokenRequest tests', () => {
  const filters = [
    {
      key: constants.filterKeys.ENTITY_PUBLICKEY,
      operator: ' = ',
      value: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
    },
    {key: constants.filterKeys.TOKEN_ID, operator: ' > ', value: '0.0.2'},
    {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
    {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
  ];

  let {query, params, order, limit} = tokens.extractSqlFromTokenRequest(
    `${tokens.tokensSelectQuery}${tokens.entityIdJoinQuery}`,
    [],
    1,
    'token_id',
    filters
  );

  expect(query).toStrictEqual(
    'select t.token_id, symbol, e.key from token t join t_entities e on e.id = t.token_id where ed25519_public_key_hex = $1 and t.token_id > $2 order by token_id desc limit $3;'
  );
  expect(params).toStrictEqual(['3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be', '0.0.2', '3']);
  expect(order).toStrictEqual(constants.orderFilterValues.DESC);
  expect(limit).toStrictEqual(3);
});

describe('token with account filter extractSqlFromTokenRequest tests', () => {
  const filters = [
    {
      key: constants.filterKeys.ACCOUNT_ID,
      operator: ' = ',
      value: '0.0.5',
    },
    {key: constants.filterKeys.LIMIT, operator: ' = ', value: '3'},
    {key: constants.filterKeys.ORDER, operator: ' = ', value: constants.orderFilterValues.DESC},
  ];

  const sqlquery = `${tokens.tokensSelectQuery}${tokens.accountIdJoinQuery}${tokens.entityIdJoinQuery}`;
  let {query, params, order, limit} = tokens.extractSqlFromTokenRequest(sqlquery, [5], 2, 'token_id', filters);

  expect(query).toStrictEqual(
    'select t.token_id, symbol, e.key from token t join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id join t_entities e on e.id = t.token_id order by token_id desc limit $2;'
  );
  expect(params).toStrictEqual([5, '3']);
  expect(order).toStrictEqual(constants.orderFilterValues.DESC);
  expect(limit).toStrictEqual(3);
});
