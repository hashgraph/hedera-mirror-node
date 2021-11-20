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

const log4js = require('log4js');

const {ContractResult} = require('../../model');
const {ContractService} = require('../../service');
const {formatSqlQueryString} = require('../testutils');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

jest.setTimeout(40000);

let dbProps;

// set timeout for beforeAll to 2 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  dbProps = await integrationDbOps.instantiateDatabase();
  await integrationDomainOps.setUp({}, dbProps);
  global.pool = dbProps;
  global.logger = log4js.getLogger();
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection(dbProps);
});

beforeEach(async () => {
  await integrationDbOps.cleanUp(dbProps.sqlConnection);
});

describe('DB integration test - ContractService.getContractResultsByIdAndFiltersQuery', () => {
  test('DB integration test -  ContractService.getContractResultsByIdAndFiltersQuery - Verify simple query', async () => {
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

  test('DB integration test -  ContractService.getContractResultsByIdAndFiltersQuery - Verify additional conditions', async () => {
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
      where cr.contract_id = $1 and cr.consensus_timestamp > $2 and cr.payer_account_id = $3
      order by cr.consensus_timestamp asc
      limit $4`)
    );
    expect(params).toEqual([2, 10, 20, 5]);
  });
});

describe('DB integration test - ContractService.getContractResultsByIdAndFilters', () => {
  test('DB integration test -  ContractService.getContractResultsByIdAndFilters - No match', async () => {
    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toEqual(null);
  });

  test('DB integration test -  ContractService.getContractResultsByIdAndFilters - Row match', async () => {
    await integrationDomainOps.addContractResult({
      contract_id: 2,
      consensus_timestamp: 1,
      function_parameters: '\\x0D',
      amount: 10,
    });

    const expectedContractResult = [
      new ContractResult({
        amount: '10',
        contract_id: '2',
        consensus_timestamp: '1',
        function_parameters: {
          data: [13],
          type: 'Buffer',
        },
        gas_limit: '1000',
        gas_used: '10',
      }),
    ];
    const response = await ContractService.getContractResultsByIdAndFilters();
    expect(response).toMatchObject(expectedContractResult);
  });

  test('DB integration test -  ContractService.getContractResultsByTimestamp - Id match', async () => {
    await integrationDomainOps.addContractResult({
      contract_id: 1,
      consensus_timestamp: 1,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 123,
    });

    await integrationDomainOps.addContractResult({
      contract_id: 2,
      consensus_timestamp: 2,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 123,
    });

    const expectedContractResult = [
      new ContractResult({
        amount: '10',
        contract_id: '2',
        consensus_timestamp: '2',
        function_parameters: {
          data: [13],
          type: 'Buffer',
        },
        gas_limit: '1000',
        gas_used: '10',
        payer_account_id: '123',
      }),
    ];

    const response = await ContractService.getContractResultsByIdAndFilters(['contract_id = $1'], [2], 'asc', 1);
    expect(response).toMatchObject(expectedContractResult);
  });

  test('DB integration test -  ContractService.getContractResultsByIdAndFilters - All params match', async () => {
    await integrationDomainOps.addContractResult({
      contract_id: 2,
      consensus_timestamp: 1,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 123,
    });

    await integrationDomainOps.addContractResult({
      contract_id: 2,
      consensus_timestamp: 2,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 123,
    });

    await integrationDomainOps.addContractResult({
      contract_id: 3,
      consensus_timestamp: 3,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 124,
    });

    await integrationDomainOps.addContractResult({
      contract_id: 3,
      consensus_timestamp: 4,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 124,
    });

    await integrationDomainOps.addContractResult({
      contract_id: 3,
      consensus_timestamp: 5,
      function_parameters: '\\x0D',
      amount: 10,
      payer_account_id: 124,
    });

    const expectedContractResult = [
      new ContractResult({
        amount: '10',
        contract_id: '3',
        consensus_timestamp: '3',
        function_parameters: {
          data: [13],
          type: 'Buffer',
        },
        gas_limit: '1000',
        gas_used: '10',
        payer_account_id: '124',
      }),
      new ContractResult({
        amount: '10',
        contract_id: '3',
        consensus_timestamp: '4',
        function_parameters: {
          data: [13],
          type: 'Buffer',
        },
        gas_limit: '1000',
        gas_used: '10',
        payer_account_id: '124',
      }),
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
