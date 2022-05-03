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

const {ContractService} = require('../../service');
const {assertSqlQueryEqual} = require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');
const {NotFoundError} = require('../../errors/notFoundError');

jest.setTimeout(40000);

let dbConfig;

// set timeout for beforeAll to 4 minutes as downloading docker image if not exists can take quite some time
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

describe('ContractService.getContractResultsByIdAndFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      ['cr.contract_id = $1'],
      [2],
      'asc',
      5
    );
    const expected = `select *
      from contract_result cr
      where cr.contract_id = $1
      order by cr.consensus_timestamp asc
      limit $2`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const additionalConditions = ['cr.contract_id = $1', 'cr.consensus_timestamp > $2', 'cr.payer_account_id = $3'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10, 20],
      'asc',
      5
    );
    const expected = `select *
      from contract_result cr
      where cr.contract_id = $1
        and cr.consensus_timestamp > $2
        and cr.payer_account_id = $3
      order by cr.consensus_timestamp asc
      limit $4`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 20, 5]);
  });
});

const contractLogContractIdWhereClause = `cl.contract_id = $1`;
describe('ContractService.getContractLogsByIdAndFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractLogsByIdAndFiltersQuery(
      [contractLogContractIdWhereClause],
      [2],
      'desc',
      'asc',
      5
    );
    assertSqlQueryEqual(
      query,
      `select
        bloom,
        contract_id,
        consensus_timestamp,
        data,
        index,
        root_contract_id,
        topic0,
        topic1,
        topic2,
        topic3
      from contract_log cl
      where cl.contract_id = $1
      order by cl.consensus_timestamp desc,
               cl.index asc
      limit $2`
    );
    expect(params).toEqual([2, 5]);
  });

  test('Verify additional conditions', async () => {
    const [query, params] = ContractService.getContractLogsByIdAndFiltersQuery(
      [
        `cl.contract_id  = $1`,
        `cl.topic0 = $2`,
        `cl.topic1 = $3`,
        `cl.topic2 = $4`,
        `cl.topic3 = $5`,
        'cl.index = $6',
        `cl.consensus_timestamp in ($7, $8)`,
      ],
      [
        1002,
        Buffer.from('11', 'hex'),
        Buffer.from('12', 'hex'),
        Buffer.from('13', 'hex'),
        Buffer.from('14', 'hex'),
        0,
        20,
        30,
      ],
      'desc',
      'desc',
      5
    );
    assertSqlQueryEqual(
      query,
      `select
       bloom,
       contract_id,
       consensus_timestamp,
       data,
       index,
       root_contract_id,
       topic0,
       topic1,
       topic2,
       topic3
      from contract_log cl
      where cl.contract_id = $1
        and cl.topic0 = $2
        and cl.topic1 = $3
        and cl.topic2 = $4
        and cl.topic3 = $5
        and cl.index = $6
        and cl.consensus_timestamp in ($7, $8)
      order by cl.consensus_timestamp desc,
               cl.index desc
      limit $9`
    );
    expect(params).toEqual([
      1002,
      Buffer.from('11', 'hex'),
      Buffer.from('12', 'hex'),
      Buffer.from('13', 'hex'),
      Buffer.from('14', 'hex'),
      0,
      20,
      30,
      5,
    ]);
  });
});

describe('ContractService.getContractResultsByIdAndFilters tests', () => {
  test('No match', async () => {
    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toEqual([]);
  });

  test('Row match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: 2,
        consensus_timestamp: 1,
        function_parameters: '\\x0D',
        amount: 10,
      },
    ]);

    const expectedContractResult = [
      {
        amount: 10n,
        contractId: 2n,
        consensusTimestamp: 1n,
        gasLimit: 1000n,
        gasUsed: null,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toMatchObject(expectedContractResult);
  });

  test('Id match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: 1,
        consensus_timestamp: 1,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 123,
      },
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 123,
      },
    ]);

    const expectedContractResult = [
      {
        amount: 10n,
        contractId: 2n,
        consensusTimestamp: 2n,
        gasLimit: 1000n,
        gasUsed: null,
        payerAccountId: 123n,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters(['contract_id = $1'], [2], 'asc', 1);
    expect(response).toMatchObject(expectedContractResult);
  });

  test('All params match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: 2,
        consensus_timestamp: 1,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 123,
      },
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 123,
      },
      {
        contract_id: 3,
        consensus_timestamp: 3,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 124,
      },
      {
        contract_id: 3,
        consensus_timestamp: 4,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 124,
      },
      {
        contract_id: 3,
        consensus_timestamp: 5,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: 124,
      },
    ]);

    const expectedContractResult = [
      {
        contractId: 3n,
        consensusTimestamp: 3n,
        payerAccountId: 124n,
      },
      {
        contractId: 3n,
        consensusTimestamp: 4n,
        payerAccountId: 124n,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['contract_id = $1', 'consensus_timestamp > $2', 'payer_account_id = $3'],
      [3, 2, 124],
      'asc',
      2
    );

    expect(response).toMatchObject(expectedContractResult);
  });
});

describe('ContractService.getContractLogsByTimestamps tests', () => {
  const timestamps = [1, 2];
  const input = [
    {
      consensus_timestamp: 1,
      contract_id: 1,
      data: '\\x0012',
      index: 0,
      root_contract_id: 1,
      topic0: '\\x000a',
    },
    {
      consensus_timestamp: 1,
      contract_id: 2,
      data: '\\x0013',
      index: 1,
      root_contract_id: 1,
      topic0: '\\x000b',
    },
    {
      consensus_timestamp: 2,
      contract_id: 1,
      data: '\\x0014',
      index: 0,
      root_contract_id: 1,
      topic0: '\\x000c',
    },
    {
      consensus_timestamp: 2,
      contract_id: 3,
      data: '\\x0015',
      index: 1,
      root_contract_id: 1,
      topic0: '\\x000d',
    },
  ];
  const expected = [
    {
      consensusTimestamp: 1n,
      contractId: 1n,
      index: 0,
      rootContractId: 1n,
    },
    {
      consensusTimestamp: 1n,
      contractId: 2n,
      index: 1,
      rootContractId: 1n,
    },
    {
      consensusTimestamp: 2n,
      contractId: 1n,
      index: 0,
      rootContractId: 1n,
    },
    {
      consensusTimestamp: 2n,
      contractId: 3n,
      index: 1,
      rootContractId: 1n,
    },
  ];

  const pickContractLogFields = (contractLogs) => {
    return contractLogs.map((cr) => _.pick(cr, ['consensusTimestamp', 'contractId', 'index', 'rootContractId']));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadContractLogs(input);
  });

  test('No match', async () => {
    await expect(ContractService.getContractLogsByTimestamps('3')).resolves.toHaveLength(0);
  });
  test('Match both timestamps', async () => {
    const results = pickContractLogFields(
      await ContractService.getContractLogsByTimestamps([timestamps[0], timestamps[1]])
    );
    expect(results).toIncludeSameMembers(expected);
  });
  test('Match one timestamp with additional umatched timestamp', async () => {
    const results = pickContractLogFields(await ContractService.getContractLogsByTimestamps([timestamps[0], '3']));
    expect(results).toIncludeSameMembers(expected.slice(0, 2));
  });
  test('Match one timestamp', async () => {
    const results = pickContractLogFields(await ContractService.getContractLogsByTimestamps([timestamps[1]]));
    expect(results).toIncludeSameMembers(expected.slice(2));
  });
});

describe('ContractService.getContractResultsByTimestamps tests', () => {
  const input = [
    {
      contract_id: 2,
      consensus_timestamp: 2,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: '5',
    },
    {
      contract_id: 3,
      consensus_timestamp: 6,
      function_parameters: '\\x0D',
      amount: 15,
      payer_account_id: '5',
    },
  ];
  const expected = [
    {
      amount: 10n,
      callResult: null,
      consensusTimestamp: 2n,
      contractId: 2n,
      createdContractIds: [],
      errorMessage: '',
      gasLimit: 1000n,
      gasUsed: null,
      payerAccountId: 5n,
    },
    {
      amount: 15n,
      callResult: null,
      consensusTimestamp: 6n,
      contractId: 3n,
      createdContractIds: [],
      errorMessage: '',
      gasLimit: 1000n,
      gasUsed: null,
      payerAccountId: 5n,
    },
  ];

  const pickContractResultFields = (contractResults) => {
    return contractResults.map((cr) =>
      _.pick(cr, [
        'amount',
        'callResult',
        'consensusTimestamp',
        'contractId',
        'createdContractIds',
        'errorMessage',
        'gasLimit',
        'gasUsed',
        'payerAccountId',
      ])
    );
  };

  beforeEach(async () => {
    await integrationDomainOps.loadContractResults(input);
  });

  test('No match', async () => {
    await expect(ContractService.getContractResultsByTimestamps('1')).resolves.toHaveLength(0);
  });

  test('Sing row match single timestamp', async () => {
    const actual = await ContractService.getContractResultsByTimestamps(expected[0].consensusTimestamp);
    expect(pickContractResultFields(actual)).toIncludeSameMembers(expected.slice(0, 1));
  });

  test('Sing row match multiple timestamps', async () => {
    const actual = await ContractService.getContractResultsByTimestamps([expected[0].consensusTimestamp, '100']);
    expect(pickContractResultFields(actual)).toIncludeSameMembers(expected.slice(0, 1));
  });

  test('Multiple rows match multiple timestamps', async () => {
    const actual = await ContractService.getContractResultsByTimestamps(expected.map((e) => e.consensusTimestamp));
    expect(pickContractResultFields(actual)).toIncludeSameMembers(expected);
  });
});

describe('ContractService.getContractLogsByIdAndFilters tests', () => {
  test('No match', async () => {
    const response = await ContractService.getContractLogsByIdAndFilters();
    expect(response).toEqual([]);
  });

  test('Row match', async () => {
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: 2,
        index: 0,
      },
    ]);

    const expectedContractLog = [
      {
        consensusTimestamp: 1n,
        contractId: 2n,
      },
    ];

    const response = await ContractService.getContractLogsByIdAndFilters();
    expect(response).toMatchObject(expectedContractLog);
  });

  test('Id match', async () => {
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 1,
        contract_id: 2,
        index: 0,
        root_contract_id: 8,
      },
      {
        consensus_timestamp: 1,
        contract_id: 3,
        index: 1,
        root_contract_id: 8,
      },
      {
        consensus_timestamp: 2,
        contract_id: 3,
        index: 0,
        root_contract_id: 9,
      },
      {
        consensus_timestamp: 2,
        contract_id: 3,
        index: 1,
        root_contract_id: 9,
      },
      {
        consensus_timestamp: 3,
        contract_id: 4,
        index: 0,
        root_contract_id: 8,
      },
    ]);

    const expectedContractLog = [
      {
        consensusTimestamp: 2n,
        contractId: 3n,
        index: 1,
      },
      {
        consensusTimestamp: 2n,
        contractId: 3n,
        index: 0,
      },
      {
        consensusTimestamp: 1n,
        contractId: 3n,
        index: 1,
      },
    ];

    const response = await ContractService.getContractLogsByIdAndFilters([contractLogContractIdWhereClause], [3]);
    expect(response).toMatchObject(expectedContractLog);
  });

  test('All params match', async () => {
    await integrationDomainOps.loadContractLogs([
      {
        consensus_timestamp: 20,
        contract_id: 2,
        index: 0,
        root_contract_id: 10,
        topic0: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea',
        topic1: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb',
        topic2: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec',
        topic3: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed',
      },
      {
        consensus_timestamp: 20,
        contract_id: 3,
        index: 1,
        root_contract_id: 10,
        topic0: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea',
        topic1: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb',
        topic2: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec',
        topic3: 'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed',
      },
      {
        consensus_timestamp: 2,
        contract_id: 3,
        index: 0,
        root_contract_id: 10,
      },
    ]);

    const expectedContractLog = [
      {
        consensusTimestamp: 20n,
        contractId: 3n,
      },
    ];
    const response = await ContractService.getContractLogsByIdAndFilters(
      [
        contractLogContractIdWhereClause,
        'cl.topic0 = $2',
        'cl.topic1 = $3',
        'cl.topic2 = $4',
        'cl.topic3 = $5',
        'cl.index = $6',
        'cl.consensus_timestamp in ($7)',
      ],
      [
        3,
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea',
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb',
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec',
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed',
        1,
        20,
      ],
      'desc',
      'asc',
      25
    );
    expect(response).toMatchObject(expectedContractLog);
  });
});

// state changes
describe('ContractService.getContractStateChangesByTimestamps tests', () => {
  test('No match', async () => {
    const response = await ContractService.getContractStateChangesByTimestamps('1');
    expect(response).toEqual([]);
  });

  test('Row match', async () => {
    await integrationDomainOps.loadContractStateChanges([
      {
        consensus_timestamp: 1,
        contract_id: 2,
      },
    ]);

    const expectedContractStateChange = [
      {
        consensusTimestamp: 1n,
        contractId: 2n,
      },
    ];

    const response = await ContractService.getContractStateChangesByTimestamps('1');
    expect(response).toMatchObject(expectedContractStateChange);
  });

  test('Id match', async () => {
    await integrationDomainOps.loadContractStateChanges([
      {
        consensus_timestamp: 1,
        contract_id: 3,
        slot: '\\x000a',
      },
      {
        consensus_timestamp: 1,
        contract_id: 4,
        slot: '\\x000b',
      },
      {
        consensus_timestamp: 1,
        contract_id: 5,
        slot: '\\x000c',
      },
      {
        consensus_timestamp: 2,
        contract_id: 3,
        slot: '\\x0001',
      },
      {
        consensus_timestamp: 2,
        contract_id: 4,
        slot: '\\x0002',
      },
      {
        consensus_timestamp: 2,
        contract_id: 5,
        slot: '\\x0003',
      },
    ]);

    const expectedContractStateChange = [
      {
        consensusTimestamp: 2n,
        contractId: 3n,
        slot: Buffer.from([92, 120, 48, 48, 48, 49]),
      },
      {
        consensusTimestamp: 2n,
        contractId: 4n,
        slot: Buffer.from([92, 120, 48, 48, 48, 50]),
      },
      {
        consensusTimestamp: 2n,
        contractId: 5n,
        slot: Buffer.from([92, 120, 48, 48, 48, 51]),
      },
    ];

    const response = await ContractService.getContractStateChangesByTimestamps('2');
    expect(response).toMatchObject(expectedContractStateChange);
  });
});

describe('ContractService.getContractIdByEvmAddress tests', () => {
  test('No match', async () => {
    const evmAddressFilter = {shard: 0, realm: 0, create2_evm_address: Buffer.from('123', 'hex')};
    await expect(() => ContractService.getContractIdByEvmAddress(evmAddressFilter)).rejects.toThrow(
      new NotFoundError(`No contract with the given evm address: ${JSON.stringify(evmAddressFilter)} has been found.`)
    );
  });

  test('Multiple rows match', async () => {
    const evmAddress = Buffer.from('3d4ffd867fac5d9c228d1dbeb7f218a29c94b', 'hex');
    await integrationDomainOps.loadContracts([
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570802912691212000,
        deleted: false,
        expiration_timestamp: null,
        id: 111169,
        key: "E'\\\\x326C0A221220F7ECD392568A9ECE84097C4B3C04C74AE52653D54398E132747B498B287245610A221220FA34ADAC826D3F878CCA5E4B074C7060DAE76259611543D6A876FF4E4B8B5C3A0A2212201ADBD17C33C48D59D0961356D5C0B19B391760A6504C3FC78D3094266FA290D2'",
        memo: '',
        num: 111169,
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570802912691212000,)',
        file_id: 111168,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: evmAddress,
      },
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570803115160698000,
        deleted: false,
        expiration_timestamp: null,
        id: 111278,
        key: "E'\\\\x326C0A2212203053E42F8D8978BC5999080C4A625BBB1BF96CBCA6BAD6A4796808A6812564490A221220CC16FF9223B2E8F8151E8EFB054203CEF5EE9AF6171D2649D46734ECDD7F5A280A22122097C6975280B82DC1969ABA4E7DDE4F478E872E04CD6E0FE3849EAFB5D86315F1'",
        memo: '',
        num: 111278,
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570803115160698000,)',
        file_id: 111276,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: null,
      },
      {
        auto_renew_period: 7885000,
        created_timestamp: 1570803584382787001,
        deleted: false,
        expiration_timestamp: 1581285572000000000,
        id: 111482,
        key: "E'\\\\x326C0A221220A13DDC50A38C7ED4A7F64CFD05E364746B8DABC3DAE8B2AFBE9A94FF2105AB1F0A2212202CECF7F1A3EADBBD678EC9D62EED162893A2069D456A4E5061E86F96C95F4FFF0A221220C6C448A8B628C11C55F773A3366D8B75E8188EEF46A50A2CCDDDA6B3B4EF55E3'",
        memo: '',
        num: 111482,
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570803587949346001,)',
        file_id: 111481,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: evmAddress,
      },
    ]);

    const evmAddressFilter = {shard: 0, realm: 0, create2_evm_address: evmAddress};
    await expect(() => ContractService.getContractIdByEvmAddress(evmAddressFilter)).rejects.toThrow(
      new NotFoundError(
        `More than one contract with the evm address ${JSON.stringify(evmAddressFilter)} have been found.`
      )
    );
  });

  test('One row match', async () => {
    const evmAddress = Buffer.from('1aaafd867fac5d9c228d1dbeb7f218a29c94b', 'hex');
    await integrationDomainOps.loadContracts([
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570802912691212000,
        deleted: false,
        expiration_timestamp: null,
        id: 111169,
        key: "E'\\\\x326C0A221220F7ECD392568A9ECE84097C4B3C04C74AE52653D54398E132747B498B287245610A221220FA34ADAC826D3F878CCA5E4B074C7060DAE76259611543D6A876FF4E4B8B5C3A0A2212201ADBD17C33C48D59D0961356D5C0B19B391760A6504C3FC78D3094266FA290D2'",
        memo: '',
        num: 111169,
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570802912691212000,)',
        file_id: 111168,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: evmAddress,
      },
      {
        auto_renew_period: 7890000,
        created_timestamp: 1570803115160698000,
        deleted: false,
        expiration_timestamp: null,
        id: 111278,
        key: "E'\\\\x326C0A2212203053E42F8D8978BC5999080C4A625BBB1BF96CBCA6BAD6A4796808A6812564490A221220CC16FF9223B2E8F8151E8EFB054203CEF5EE9AF6171D2649D46734ECDD7F5A280A22122097C6975280B82DC1969ABA4E7DDE4F478E872E04CD6E0FE3849EAFB5D86315F1'",
        memo: '',
        num: 111278,
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570803115160698000,)',
        file_id: 111276,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: null,
      },
      {
        auto_renew_period: 7885000,
        created_timestamp: 1570803584382787001,
        deleted: false,
        expiration_timestamp: 1581285572000000000,
        id: 111482,
        key: "E'\\\\x326C0A221220A13DDC50A38C7ED4A7F64CFD05E364746B8DABC3DAE8B2AFBE9A94FF2105AB1F0A2212202CECF7F1A3EADBBD678EC9D62EED162893A2069D456A4E5061E86F96C95F4FFF0A221220C6C448A8B628C11C55F773A3366D8B75E8188EEF46A50A2CCDDDA6B3B4EF55E3'",
        memo: '',
        num: 111482,
        public_key: null,
        proxy_account_id: null,
        realm: 0,
        shard: 0,
        timestamp_range: '[1570803587949346001,)',
        file_id: 111481,
        obtainer_id: null,
        type: 'CONTRACT',
        evm_address: null,
      },
    ]);

    const contractId = await ContractService.getContractIdByEvmAddress({
      realm: 0,
      shard: 0,
      create2_evm_address: evmAddress,
    });
    expect(contractId.toString()).toEqual('111169');
  });
});
