/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const _ = require('lodash');

const {CryptoAllowanceService} = require('../../service');
const {assertSqlQueryEqual} = require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

jest.setTimeout(40000);

let dbConfig;

// set timeout for beforeAll to 2 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  dbConfig = await integrationDbOps.instantiateDatabase();
  await integrationDomainOps.setUp({}, dbConfig.sqlConnection);
  global.pool = dbConfig.sqlConnection;
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection(dbConfig);
});

beforeEach(async () => {
  if (!dbConfig.sqlConnection) {
    logger.warn(`sqlConnection undefined, acquire new connection`);
    dbConfig.sqlConnection = integrationDbOps.getConnection(dbConfig.dbSessionConfig);
  }

  await integrationDbOps.cleanUp(dbConfig.sqlConnection);
});

const defaultOwnerFilter = 'owner = $1';
const additionalConditions = [defaultOwnerFilter, 'spender > $2'];
describe('CryptoAllowanceService.getAccountCryptoAllowancesWithFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = CryptoAllowanceService.getAccountCryptoAllowancesWithFiltersQuery(
      [defaultOwnerFilter],
      [2],
      'asc',
      5
    );
    const expected = `select amount,owner,payer_account_id,spender,timestamp_range
    from crypto_allowance
    where owner = $1
    order by spender asc limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const [query, params] = CryptoAllowanceService.getAccountCryptoAllowancesWithFiltersQuery(
      additionalConditions,
      [2, 10],
      'asc',
      5
    );
    const expected = `select amount,owner,payer_account_id,spender,timestamp_range
    from crypto_allowance
    where owner = $1 and spender > $2
    order by spender asc limit $3`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 5]);
  });
});

const defaultInputCryptoAllowance = [
  {
    amount: 1000,
    owner: 2000,
    payer_account_id: 3000,
    spender: 4000,
    timestamp_range: '[0,)',
  },
];

const defaultExpectedCryptoAllowance = [
  {
    amount: '1000',
    owner: '2000',
    payerAccountId: '3000',
    spender: '4000',
  },
];

describe('CryptoAllowanceService.getAccountCrytoAllownces tests', () => {
  test('CryptoAllowanceService.getAccountCrytoAllownces - No match', async () => {
    await expect(
      CryptoAllowanceService.getAccountCrytoAllowances([defaultOwnerFilter], [2], 'asc', 5)
    ).resolves.toStrictEqual([]);
  });

  test('CryptoAllowanceService.getAccountCrytoAllownces - Matching entity', async () => {
    await integrationDomainOps.loadCryptoAllowance(defaultInputCryptoAllowance);

    await expect(
      CryptoAllowanceService.getAccountCrytoAllowances([defaultOwnerFilter], [2000], 'asc', 5)
    ).resolves.toMatchObject(defaultExpectedCryptoAllowance);
  });

  const inputCryptoAllowance = [
    {
      amount: 1000,
      owner: 2000,
      payer_account_id: 3000,
      spender: 4000,
      timestamp_range: '[0,)',
    },
    {
      amount: 1000,
      owner: 2000,
      payer_account_id: 3000,
      spender: 4001,
      timestamp_range: '[0,)',
    },
    {
      amount: 1000,
      owner: 2000,
      payer_account_id: 3000,
      spender: 4002,
      timestamp_range: '[0,)',
    },
    {
      amount: 1000,
      owner: 2000,
      payer_account_id: 3000,
      spender: 4003,
      timestamp_range: '[0,)',
    },
  ];

  const expectedCryptoAllowance = [
    {
      amount: '1000',
      owner: '2000',
      payerAccountId: '3000',
      spender: '4002',
    },
    {
      amount: '1000',
      owner: '2000',
      payerAccountId: '3000',
      spender: '4003',
    },
  ];

  test('CryptoAllowanceService.getAccountCrytoAllownces - Matching spender gt entity', async () => {
    await integrationDomainOps.loadCryptoAllowance(inputCryptoAllowance);

    await expect(
      CryptoAllowanceService.getAccountCrytoAllowances([defaultOwnerFilter, 'spender > $2'], [2000, 4001], 'asc', 5)
    ).resolves.toMatchObject(expectedCryptoAllowance);
  });

  test('CryptoAllowanceService.getAccountCrytoAllownces - Matching spender entity', async () => {
    await integrationDomainOps.loadCryptoAllowance(inputCryptoAllowance);

    await expect(
      CryptoAllowanceService.getAccountCrytoAllowances(
        [defaultOwnerFilter, 'spender in ($2, $3)'],
        [2000, 4002, 4003],
        'asc',
        5
      )
    ).resolves.toMatchObject(expectedCryptoAllowance);
  });
});
