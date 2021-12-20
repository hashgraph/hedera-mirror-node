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

const {ContractService} = require('../../service');
const {formatSqlQueryString} = require('../testutils');

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
  test('ContractService.getContractResultsByIdAndFiltersQuery - Verify simple query', async () => {
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      ['cr.contract_id = $1'],
      [2],
      'asc',
      5
    );
    expect(formatSqlQueryString(query)).toEqual(
      formatSqlQueryString(`select *
                            from contract_result cr
                            where cr.contract_id = $1
                            order by cr.consensus_timestamp asc
                            limit $2`)
    );
    expect(params).toEqual([2, 5]);
  });

  test('ContractService.getContractResultsByIdAndFiltersQuery - Verify additional conditions', async () => {
    const additionalConditions = ['cr.contract_id = $1', 'cr.consensus_timestamp > $2', 'cr.payer_account_id = $3'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10, 20],
      'asc',
      5
    );
    expect(formatSqlQueryString(query)).toEqual(
      formatSqlQueryString(`select *
                            from contract_result cr
                            where cr.contract_id = $1
                              and cr.consensus_timestamp > $2
                              and cr.payer_account_id = $3
                            order by cr.consensus_timestamp asc
                            limit $4`)
    );
    expect(params).toEqual([2, 10, 20, 5]);
  });
});

const contractLogContractIdWhereClause = `cl.contract_id = $1`;
describe('ContractService.getContractLogsByIdAndFiltersQuery tests', () => {
  test('ContractService.getContractLogsByIdAndFiltersQuery - Verify simple query', async () => {
    const [query, params] = ContractService.getContractLogsByIdAndFiltersQuery(
      [contractLogContractIdWhereClause],
      [2],
      'desc',
      'asc',
      5
    );
    expect(formatSqlQueryString(query)).toEqual(
      formatSqlQueryString(`select contract_id,
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
                            limit $2`)
    );
    expect(params).toEqual([2, 5]);
  });

  test('ContractService.getContractLogsByIdAndFiltersQuery - Verify additional conditions', async () => {
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
    expect(formatSqlQueryString(query)).toEqual(
      formatSqlQueryString(`select contract_id,
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
                            limit $9`)
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
  test('ContractService.getContractResultsByIdAndFilters - No match', async () => {
    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toEqual([]);
  });

  test('ContractService.getContractResultsByIdAndFilters - Row match', async () => {
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

  test('ContractService.getContractResultsByTimestamp - Id match', async () => {
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

  test('ContractService.getContractResultsByIdAndFilters - All params match', async () => {
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

describe('ContractService.getContractResultsByTimestamp tests', () => {
  test('ContractService.getContractResultsByTimestamp - No match', async () => {
    await expect(ContractService.getContractResultByTimestamp(1)).resolves.toBeNull();
  });

  test('ContractService.getContractResultsByTimestamp - Row match', async () => {
    const contractResultsInput = [
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '\\x0D',
        amount: 10,
        payer_account_id: '5',
      },
    ];

    const expectedContractResult = {
      amount: '10',
      callResult: null,
      consensusTimestamp: '2',
      contractId: '2',
      createdContractIds: [],
      errorMessage: '',
      gasLimit: '1000',
      gasUsed: '10',
      payerAccountId: '5',
    };

    await integrationDomainOps.loadContractResults(contractResultsInput);

    await expect(ContractService.getContractResultByTimestamp(2)).resolves.toMatchObject(expectedContractResult);
  });
});

describe('ContractService.getContractLogsByIdAndFilters tests', () => {
  test('ContractService.getContractLogsByIdAndFilters - No match', async () => {
    const response = await ContractService.getContractLogsByIdAndFilters();
    expect(response).toEqual([]);
  });

  test('ContractService.getContractLogsByIdAndFilters - Row match', async () => {
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

  test('ContractService.getContractLogsByIdAndFilters - Id match', async () => {
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
        index: 0,
      },
      {
        consensusTimestamp: '1',
        contractId: '3',
        index: 1,
      },
    ];

    const response = await ContractService.getContractLogsByIdAndFilters(
      [contractLogContractIdWhereClause],
      [3],
      'desc',
      'desc',
      25
    );
    expect(response).toMatchObject(expectedContractLog);
  });

  test('ContractService.getContractLogsByIdAndFilters - All params match', async () => {
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
