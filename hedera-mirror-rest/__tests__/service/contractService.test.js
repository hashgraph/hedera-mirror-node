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

const _ = require('lodash');

const {ContractService} = require('../../service');
const {assertSqlQueryEqual} = require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

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
      `select contract_id,
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
      `select contract_id,
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
        amount: '10',
        contractId: '2',
        consensusTimestamp: '1',
        gasLimit: '1000',
        gasUsed: '10',
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
        amount: '10',
        contractId: '2',
        consensusTimestamp: '2',
        gasLimit: '1000',
        gasUsed: '10',
        payerAccountId: '123',
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
        contractId: '3',
        consensusTimestamp: '3',
        payerAccountId: '124',
      },
      {
        contractId: '3',
        consensusTimestamp: '4',
        payerAccountId: '124',
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
      consensusTimestamp: '1',
      contractId: '1',
      index: 0,
      rootContractId: '1',
    },
    {
      consensusTimestamp: '1',
      contractId: '2',
      index: 1,
      rootContractId: '1',
    },
    {
      consensusTimestamp: '2',
      contractId: '1',
      index: 0,
      rootContractId: '1',
    },
    {
      consensusTimestamp: '2',
      contractId: '3',
      index: 1,
      rootContractId: '1',
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
      amount: '10',
      callResult: null,
      consensusTimestamp: '2',
      contractId: '2',
      createdContractIds: [],
      errorMessage: '',
      gasLimit: '1000',
      gasUsed: '10',
      payerAccountId: '5',
    },
    {
      amount: '15',
      callResult: null,
      consensusTimestamp: '6',
      contractId: '3',
      createdContractIds: [],
      errorMessage: '',
      gasLimit: '1000',
      gasUsed: '10',
      payerAccountId: '5',
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
        consensusTimestamp: '1',
        contractId: '2',
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
        consensusTimestamp: '2',
        contractId: '3',
        index: 1,
      },
      {
        consensusTimestamp: '2',
        contractId: '3',
        index: 0,
      },
      {
        consensusTimestamp: '1',
        contractId: '3',
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
        consensusTimestamp: '20',
        contractId: '3',
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
