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

describe('ContractService.getContractResultsByIdAndTimestamp tests', () => {
  test('ContractService.getContractResultsByIdAndTimestamp - Row match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '\\x0D',
        amount: 10,
      },
    ]);

    await integrationDomainOps.loadRecordFiles([
      {
        index: 1,
        consensus_start: 1,
        consensus_end: 3,
        file_hash: 'dee34',
      },
    ]);

    await integrationDomainOps.loadTransactions([
      {
        consensus_timestamp: 2,
        payerAccountId: '5',
      },
    ]);

    const expectedDetailedContractResult = {
      contractResult: {
        amount: '10',
        bloom: null,
        callResult: null,
        consensusTimestamp: '2',
        contractId: '2',
        createdContractIds: [],
        errorMessage: '',
        functionResult: null,
        gasLimit: '1000',
        gasUsed: '10',
        payerAccountId: '5',
      },
      recordFile: {
        consensusEnd: '3',
        fileHash: 'dee34',
        index: '1',
      },
      transaction: {
        consensusTimestamp: '2',
        payerAccountId: '5',
      },
    };
    const response = await ContractService.getContractResultsByIdAndTimestamp(2, 2);
    expect(response).toMatchObject(expectedDetailedContractResult);
  });
});

describe('ContractService.getContractResultsByTransactionId tests', () => {
  test('ContractService.getContractResultsByTransactionId - Row match', async () => {
    await integrationDomainOps.loadContractResults([
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '\\x0D',
        amount: 10,
      },
    ]);

    await integrationDomainOps.loadRecordFiles([
      {
        index: 1,
        consensus_start: 1,
        consensus_end: 3,
        file_hash: 'dee34',
      },
    ]);

    await integrationDomainOps.loadTransactions([
      {
        consensus_timestamp: 2,
        payerAccountId: '5',
        valid_start_timestamp: 1,
      },
    ]);

    const expectedDetailedContractResult = {
      contractResult: {
        amount: '10',
        bloom: null,
        callResult: null,
        consensusTimestamp: '2',
        contractId: '2',
        createdContractIds: [],
        errorMessage: '',
        functionResult: null,
        gasLimit: '1000',
        gasUsed: '10',
        payerAccountId: '5',
      },
      recordFile: {
        consensusEnd: '3',
        fileHash: 'dee34',
        index: '1',
      },
      transaction: {
        consensusTimestamp: '2',
        payerAccountId: '5',
      },
    };
    const response = await ContractService.getContractResultsByTransactionId(1, 5);
    expect(response).toMatchObject(expectedDetailedContractResult);
  });
});
