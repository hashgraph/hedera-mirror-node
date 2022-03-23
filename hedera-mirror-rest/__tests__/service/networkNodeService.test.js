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

const {CryptoAllowanceService, NetworkNodeService} = require('../../service');
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

const defaultFileFilter = 'file_id = $1';
const additionalConditions = [defaultFileFilter, 'spender > $2'];
describe('NetworkNodeService.getNetworkNodesWithFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = NetworkNodeService.getNetworkNodesWithFiltersQuery([], [102], 'asc', 5);
    const expected = `with adb as (
      select start_consensus_timestamp, end_consensus_timestamp, file_id from address_book where file_id = $1
     ),
     entries as (
      select description, memo, node_id, node_account_id, node_cert_hash, public_key, adb.file_id, adb.start_consensus_timestamp, adb.end_consensus_timestamp
      from address_book_entry abe
      join adb on adb.start_consensus_timestamp = abe.consensus_timestamp
     ),
     endpoints as (
      select consensus_timestamp, node_id, jsonb_agg(jsonb_build_object('ip_address_v4', ip_address_v4, 'port', port) order by port desc) as service_endpoints
      from address_book_service_endpoint abse
      join adb on adb.start_consensus_timestamp = abse.consensus_timestamp
      group by consensus_timestamp, node_id
     )
     select abe.description, abe.file_id, abe.memo, abe.node_id, abe.node_account_id, abe.node_cert_hash, abe.start_consensus_timestamp, abe.end_consensus_timestamp,
     abse.service_endpoints, abe.public_key
     from entries abe
     left join endpoints abse on abe.start_consensus_timestamp = abse.consensus_timestamp and abe.node_id = abse.node_id
     order by abe.node_id asc, abe.start_consensus_timestamp asc
     limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([102, 5]);
  });

  // test('Verify additional conditions', async () => {
  //   const [query, params] = NetworkNodeService.getNetworkNodesWithFiltersQuery(
  //     additionalConditions,
  //     [2, 10],
  //     'asc',
  //     5
  //   );
  //   const expected = `select amount,owner,payer_account_id,spender,timestamp_range
  //   from crypto_allowance
  //   where owner = $1 and spender > $2
  //   order by spender asc limit $3`;
  //   assertSqlQueryEqual(query, expected);
  //   expect(params).toEqual([2, 10, 5]);
  // });
});

const defaultInputAddressBooks = [
  {
    start_consensus_timestamp: 1,
    file_id: 101,
    node_count: 3,
  },
  {
    start_consensus_timestamp: 2,
    file_id: 102,
    node_count: 4,
  },
];

const defaultInputAddressBookEntries = [
  {
    consensus_timestamp: 1,
    memo: 'memo 1',
    node_id: 0,
    node_account_id: 3,
    node_cert_hash: '[0,)',
    description: 'desc 1',
  },
  {
    consensus_timestamp: 1,
    memo: 'memo 2',
    node_id: 1,
    node_account_id: 4,
    node_cert_hash: '[0,)',
    description: 'desc 2',
  },
  {
    consensus_timestamp: 2,
    memo: '0.0.3',
    node_id: 0,
    node_account_id: 3,
    node_cert_hash: '[0,)',
    description: 'desc 3',
  },
  {
    consensus_timestamp: 2,
    memo: '0.0.4',
    node_id: 1,
    node_account_id: 4,
    node_cert_hash: '[0,)',
    description: 'desc 4',
  },
];

const defaultInputServiceEndpointBooks = [
  {
    consensus_timestamp: 1,
    ip_address_v4: '127.0.0.1',
    node_id: 0,
    port: 50211,
  },
  {
    consensus_timestamp: 1,
    ip_address_v4: '127.0.0.2',
    node_id: 1,
    port: 50212,
  },
  {
    consensus_timestamp: 2,
    ip_address_v4: '128.0.0.1',
    node_id: 0,
    port: 50212,
  },
  {
    consensus_timestamp: 2,
    ip_address_v4: '128.0.0.2',
    node_id: 1,
    port: 50212,
  },
];

const defaultExpectedNetworkNode101 = [
  {
    description: 'desc 2',
    end_consensus_timestamp: null,
    file_id: '101',
    memo: 'memo 2',
    node_account_id: '4',
    node_id: '1',
    service_endpoints: [
      {
        ip_address_v4: '127.0.0.2',
        port: 50212,
      },
    ],
    start_consensus_timestamp: '1',
  },
  {
    description: 'desc 1',
    end_consensus_timestamp: null,
    file_id: '101',
    memo: 'memo 1',
    node_account_id: '3',
    node_id: '0',
    service_endpoints: [
      {
        ip_address_v4: '127.0.0.1',
        port: 50211,
      },
    ],
    start_consensus_timestamp: '1',
  },
];

const defaultExpectedNetworkNode102 = [
  {
    description: 'desc 3',
    end_consensus_timestamp: null,
    file_id: '102',
    memo: '0.0.3',
    node_account_id: '3',
    node_id: '0',
    service_endpoints: [
      {
        ip_address_v4: '128.0.0.1',
        port: 50212,
      },
    ],
    start_consensus_timestamp: '2',
  },
  {
    description: 'desc 4',
    end_consensus_timestamp: null,
    file_id: '102',
    memo: '0.0.4',
    node_account_id: '4',
    node_id: '1',
    service_endpoints: [
      {
        ip_address_v4: '128.0.0.2',
        port: 50212,
      },
    ],
    start_consensus_timestamp: '2',
  },
];

describe('NetworkNodeService.getNetworkNodes tests', () => {
  test('NetworkNodeService.getNetworkNodes - No match', async () => {
    await expect(NetworkNodeService.getNetworkNodes([defaultFileFilter], [2], 'asc', 5)).resolves.toStrictEqual([]);
  });

  test('NetworkNodeService.getNetworkNodes - Matching 101 entity', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);

    await expect(NetworkNodeService.getNetworkNodes([defaultFileFilter], [101], 'desc', 5)).resolves.toMatchObject(
      defaultExpectedNetworkNode101
    );
  });

  test('NetworkNodeService.getNetworkNodes - Matching 102 entity', async () => {
    await integrationDomainOps.loadAddressBooks(defaultInputAddressBooks);
    await integrationDomainOps.loadAddressBookEntries(defaultInputAddressBookEntries);
    await integrationDomainOps.loadAddressBookServiceEndpoints(defaultInputServiceEndpointBooks);

    await expect(NetworkNodeService.getNetworkNodes([defaultFileFilter], [102], 'asc', 5)).resolves.toMatchObject(
      defaultExpectedNetworkNode102
    );
  });

  // const inputCryptoAllowance = [
  //   {
  //     amount: 1000,
  //     owner: 2000,
  //     payer_account_id: 3000,
  //     spender: 4000,
  //     timestamp_range: '[0,)',
  //   },
  //   {
  //     amount: 1000,
  //     owner: 2000,
  //     payer_account_id: 3000,
  //     spender: 4001,
  //     timestamp_range: '[0,)',
  //   },
  //   {
  //     amount: 1000,
  //     owner: 2000,
  //     payer_account_id: 3000,
  //     spender: 4002,
  //     timestamp_range: '[0,)',
  //   },
  //   {
  //     amount: 1000,
  //     owner: 2000,
  //     payer_account_id: 3000,
  //     spender: 4003,
  //     timestamp_range: '[0,)',
  //   },
  // ];

  // const expectedCryptoAllowance = [
  //   {
  //     amount: '1000',
  //     owner: '2000',
  //     payerAccountId: '3000',
  //     spender: '4002',
  //   },
  //   {
  //     amount: '1000',
  //     owner: '2000',
  //     payerAccountId: '3000',
  //     spender: '4003',
  //   },
  // ];

  // test('NetworkNodeService.getAccountCrytoAllownces - Matching spender gt entity', async () => {
  //   await integrationDomainOps.loadCryptoAllowances(inputCryptoAllowance);

  //   await expect(
  //     NetworkNodeService.getNetworkNodes([defaultFileFilter, 'node_id = $2'], [102, 4001], 'asc', 5)
  //   ).resolves.toMatchObject(expectedCryptoAllowance);
  // });

  // test('NetworkNodeService.getAccountCrytoAllownces - Matching spender gt entity', async () => {
  //   await integrationDomainOps.loadCryptoAllowances(inputCryptoAllowance);

  //   await expect(
  //     NetworkNodeService.getNetworkNodes([defaultFileFilter, 'node_id = $2'], [101, 4001], 'desc', 5)
  //   ).resolves.toMatchObject(expectedCryptoAllowance);
  // });

  // test('NetworkNodeService.getNetworkNodes - Matching spender entity', async () => {
  //   await integrationDomainOps.loadCryptoAllowances(inputCryptoAllowance);

  //   await expect(
  //     NetworkNodeService.getNetworkNodes([defaultFileFilter, 'spender in ($2, $3)'], [2000, 4002, 4003], 'asc', 5)
  //   ).resolves.toMatchObject(expectedCryptoAllowance);
  // });
});
