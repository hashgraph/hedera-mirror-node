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

import _ from 'lodash';
import {ContractService} from '../../service';
import {assertSqlQueryEqual} from '../testutils';
import integrationDbOps from '../integrationDbOps';
import integrationDomainOps from '../integrationDomainOps';
import {NotFoundError} from '../../errors/notFoundError';
import {defaultMochaStatements} from './defaultMochaStatements';
defaultMochaStatements(jest, integrationDbOps, integrationDomainOps);

describe('ContractService.getContractResultsByIdAndFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      ['cr.contract_id = $1'],
      [2],
      'asc',
      5
    );
    const expected = `with etht as (select hash,consensus_timestamp from ethereum_transaction)
      select cr.*,etht.hash from contract_result cr
      left join etht on cr.consensus_timestamp = etht.consensus_timestamp
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
    const expected = `with etht as (select hash,consensus_timestamp from ethereum_transaction)
      select cr.*,etht.hash from contract_result cr
      left join etht on cr.consensus_timestamp = etht.consensus_timestamp
      where cr.contract_id = $1
        and cr.consensus_timestamp > $2
        and cr.payer_account_id = $3
      order by cr.consensus_timestamp asc
      limit $4`;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 20, 5]);
  });

  test('Verify transaction.nonce condition', async () => {
    const additionalConditions = ['cr.contract_id = $1', 't.nonce = $2'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10],
      'asc',
      5
    );
    const expected = `with etht as (select hash,consensus_timestamp from ethereum_transaction) ,
        t as (select consensus_timestamp,index,nonce from transaction where transaction.nonce = $2)
        select cr.*,etht.hash from contract_result cr
        left join etht on cr.consensus_timestamp = etht.consensus_timestamp
        left join t on cr.consensus_timestamp = t.consensus_timestamp
        where cr.contract_id = $1 and t.nonce = $2
        order by cr.consensus_timestamp asc
        limit $3
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 5]);
  });

  test('Verify transaction.index condition', async () => {
    const additionalConditions = ['cr.contract_id = $1', 't.index = $2'];
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      additionalConditions,
      [2, 10],
      'asc',
      5
    );
    const expected = `with etht as (select hash,consensus_timestamp from ethereum_transaction) ,
        t as (select consensus_timestamp,index,nonce from transaction where transaction.index = $2)
        select cr.*,etht.hash from contract_result cr
        left join etht on cr.consensus_timestamp = etht.consensus_timestamp
        left join t on cr.consensus_timestamp = t.consensus_timestamp
        where cr.contract_id = $1 and t.index = $2
        order by cr.consensus_timestamp asc
        limit $3
    `;
    assertSqlQueryEqual(query, expected);
    expect(params).toEqual([2, 10, 5]);
  });
});

const contractLogContractIdWhereClause = `cl.contract_id = $1`;
describe('ContractService.getContractLogsQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [],
      inner: [],
      upper: [],
      conditions: [contractLogContractIdWhereClause],
      params: [2],
      timestampOrder: 'desc',
      indexOrder: 'asc',
      limit: 5,
    });
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
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [],
      inner: [],
      upper: [],
      conditions: [
        `cl.contract_id  = $1`,
        `cl.topic0 in ($2)`,
        `cl.topic1 in ($3)`,
        `cl.topic2 in ($4)`,
        `cl.topic3 in ($5)`,
      ],
      params: [
        1002,
        Buffer.from('11', 'hex'),
        Buffer.from('12', 'hex'),
        Buffer.from('13', 'hex'),
        Buffer.from('14', 'hex'),
      ],
      timestampOrder: 'desc',
      indexOrder: 'desc',
      limit: 5,
    });
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
        and cl.topic0 in ($2)
        and cl.topic1 in ($3)
        and cl.topic2 in ($4)
        and cl.topic3 in ($5)
      order by cl.consensus_timestamp desc,
               cl.index desc
      limit $6`
    );
    expect(params).toEqual([
      1002,
      Buffer.from('11', 'hex'),
      Buffer.from('12', 'hex'),
      Buffer.from('13', 'hex'),
      Buffer.from('14', 'hex'),
      5,
    ]);
  });
  test('Verify [lower, inner] filters', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [
        {key: 'index', operator: ' >= ', value: '1'},
        {key: 'timestamp', operator: ' = ', value: '1001'},
      ],
      inner: [{key: 'timestamp', operator: ' > ', value: '1001'}],
      upper: [],
      conditions: [`cl.contract_id  = $1`, `cl.topic0 in ($2)`],
      params: [1002, Buffer.from('11', 'hex')],
      timestampOrder: 'desc',
      indexOrder: 'desc',
      limit: 5,
    });
    assertSqlQueryEqual(
      query,
      `(select
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
        and cl.topic0 in ($2)
        and cl.index >= $4
        and cl.consensus_timestamp = $5
      order by cl.consensus_timestamp desc,
               cl.index desc
      limit $3) union (
        select
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
        and cl.topic0 in ($2)
        and cl.consensus_timestamp > $6
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3)
      order by consensus_timestamp desc, index desc
      limit $3`
    );
    expect(params).toEqual([1002, Buffer.from('11', 'hex'), 5, '1', '1001', '1001']);
  });
  test('Verify [lower, inner, upper] filters', async () => {
    const [query, params] = ContractService.getContractLogsQuery({
      lower: [
        {key: 'index', operator: ' >= ', value: '1'},
        {key: 'timestamp', operator: ' = ', value: '1001'},
      ],
      inner: [
        {key: 'timestamp', operator: ' > ', value: '1001'},
        {key: 'timestamp', operator: ' < ', value: '1005'},
      ],
      upper: [
        {key: 'index', operator: ' <= ', value: '5'},
        {key: 'timestamp', operator: ' = ', value: '1005'},
      ],
      conditions: [`cl.contract_id  = $1`, `cl.topic0 in ($2)`],
      params: [1002, Buffer.from('11', 'hex')],
      timestampOrder: 'desc',
      indexOrder: 'desc',
      limit: 5,
    });
    assertSqlQueryEqual(
      query,
      `(
        select
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
          and cl.topic0 in ($2)
          and cl.index >= $4
          and cl.consensus_timestamp = $5
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      ) union (
        select
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
        and cl.topic0 in ($2)
        and cl.consensus_timestamp > $6
        and cl.consensus_timestamp < $7
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      ) union (
        select
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
        and cl.topic0 in ($2)
        and cl.index <= $8
        and cl.consensus_timestamp = $9
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      )
      order by consensus_timestamp desc, index desc
      limit $3`
    );
    expect(params).toEqual([1002, Buffer.from('11', 'hex'), 5, '1', '1001', '1001', '1005', '5', '1005']);
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
        amount: 10,
        contractId: 2,
        consensusTimestamp: 1,
        gasLimit: 1000,
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
        amount: 10,
        contractId: 2,
        consensusTimestamp: 2,
        gasLimit: 1000,
        gasUsed: null,
        payerAccountId: 123,
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
        contractId: 3,
        consensusTimestamp: 3,
        payerAccountId: 124,
      },
      {
        contractId: 3,
        consensusTimestamp: 4,
        payerAccountId: 124,
      },
    ];

    const response = await ContractService.getContractResultsByIdAndFilters(
      ['cr.contract_id = $1', 'cr.consensus_timestamp > $2', 'cr.payer_account_id = $3'],
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
      consensusTimestamp: 1,
      contractId: 1,
      index: 0,
      rootContractId: 1,
    },
    {
      consensusTimestamp: 1,
      contractId: 2,
      index: 1,
      rootContractId: 1,
    },
    {
      consensusTimestamp: 2,
      contractId: 1,
      index: 0,
      rootContractId: 1,
    },
    {
      consensusTimestamp: 2,
      contractId: 3,
      index: 1,
      rootContractId: 1,
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
      amount: 10,
      callResult: null,
      consensusTimestamp: 2,
      contractId: 2,
      createdContractIds: [],
      errorMessage: '',
      gasLimit: 1000,
      gasUsed: null,
      payerAccountId: 5,
    },
    {
      amount: 15,
      callResult: null,
      consensusTimestamp: 6,
      contractId: 3,
      createdContractIds: [],
      errorMessage: '',
      gasLimit: 1000,
      gasUsed: null,
      payerAccountId: 5,
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
describe('ContractService.getContractLogs tests', () => {
  const defaultQuery = {
    lower: [],
    inner: [],
    upper: [],
    conditions: [],
    params: [],
    timestampOrder: 'desc',
    indexOrder: 'desc',
    limit: 100,
  };

  test('No match', async () => {
    const response = await ContractService.getContractLogs(defaultQuery);
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
        consensusTimestamp: 1,
        contractId: 2,
      },
    ];

    const response = await ContractService.getContractLogs({...defaultQuery, params: []});
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
        consensusTimestamp: 2,
        contractId: 3,
        index: 1,
      },
      {
        consensusTimestamp: 2,
        contractId: 3,
        index: 0,
      },
      {
        consensusTimestamp: 1,
        contractId: 3,
        index: 1,
      },
    ];

    const response = await ContractService.getContractLogs({
      ...defaultQuery,
      conditions: [contractLogContractIdWhereClause],
      params: [3],
    });
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
        consensusTimestamp: 20,
        contractId: 3,
      },
    ];
    const response = await ContractService.getContractLogs({
      ...defaultQuery,
      lower: [
        {key: 'index', operator: ' = ', value: 1},
        {key: 'timestamp', operator: ' = ', value: 20},
      ],
      conditions: [
        contractLogContractIdWhereClause,
        'cl.topic0 in ($2)',
        'cl.topic1 in ($3)',
        'cl.topic2 in ($4)',
        'cl.topic3 in ($5)',
      ],
      params: [
        3,
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea',
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb',
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec',
        'ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed',
      ],
      timestampOrder: 'desc',
      indexOrder: 'asc',
      limit: 25,
    });
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
        consensusTimestamp: 1,
        contractId: 2,
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
        consensusTimestamp: 2,
        contractId: 3,
        slot: Buffer.from([92, 120, 48, 48, 48, 49]),
      },
      {
        consensusTimestamp: 2,
        contractId: 4,
        slot: Buffer.from([92, 120, 48, 48, 48, 50]),
      },
      {
        consensusTimestamp: 2,
        contractId: 5,
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
