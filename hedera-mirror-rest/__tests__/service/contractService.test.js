/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';

import {NotFoundError} from '../../errors';
import {ContractService} from '../../service';
import {assertSqlQueryEqual} from '../testutils';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import {TransactionResult, TransactionType} from '../../model';
import {orderFilterValues} from '../../constants';

setupIntegrationTest();

describe('ContractService.getContractResultsByIdAndFiltersQuery tests', () => {
  test('Verify simple query', async () => {
    const [query, params] = ContractService.getContractResultsByIdAndFiltersQuery(
      ['cr.contract_id = $1'],
      [2],
      'asc',
      5
    );
    const expected = `
        with entity as (select evm_address,id from entity)
        select
            cr.amount,
            cr.bloom,
            cr.call_result,
            cr.consensus_timestamp,
            cr.contract_id,
            cr.created_contract_ids,
            cr.error_message,
            cr.failed_initcode,
            cr.function_parameters,
            cr.gas_limit,
            cr.gas_used,
            cr.payer_account_id,
            cr.sender_id,
            cr.transaction_hash,
            cr.transaction_index,
            cr.transaction_result,
            coalesce(e.evm_address,'') as evm_address
        from contract_result cr
        left join entity e on e.id = cr.contract_id
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
    const expected = `
    with entity as (select evm_address,id from entity)
    select
        cr.amount,
        cr.bloom,
        cr.call_result,
        cr.consensus_timestamp,
        cr.contract_id,
        cr.created_contract_ids,
        cr.error_message,
        cr.failed_initcode,
        cr.function_parameters,
        cr.gas_limit,
        cr.gas_used,
        cr.payer_account_id,
        cr.sender_id,
        cr.transaction_hash,
        cr.transaction_index,
        cr.transaction_result,
        coalesce(e.evm_address,'') as evm_address
    from contract_result cr
    left join entity e on e.id = cr.contract_id
    where cr.contract_id = $1 and cr.consensus_timestamp > $2 and cr.payer_account_id = $3
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
    const expected = `
    with entity as (select evm_address,id from entity),
        t as (select consensus_timestamp, index, nonce from transaction where transaction.nonce = $2)
    select
        cr.amount,
        cr.bloom,
        cr.call_result,
        cr.consensus_timestamp,
        cr.contract_id,
        cr.created_contract_ids,
        cr.error_message,
        cr.failed_initcode,
        cr.function_parameters,
        cr.gas_limit,
        cr.gas_used,
        cr.payer_account_id,
        cr.sender_id,
        cr.transaction_hash,
        cr.transaction_index,
        cr.transaction_result,
        coalesce(e.evm_address,'') as evm_address
    from contract_result cr
    left join entity e on e.id = cr.contract_id
    join t on cr.consensus_timestamp = t.consensus_timestamp
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
    const expected = `
    with  entity as (select evm_address,id from entity),
        t as (select consensus_timestamp, index, nonce from transaction where transaction.index = $2)
    select
        cr.amount,
        cr.bloom,
        cr.call_result,
        cr.consensus_timestamp,
        cr.contract_id,
        cr.created_contract_ids,
        cr.error_message,
        cr.failed_initcode,
        cr.function_parameters,
        cr.gas_limit,
        cr.gas_used,
        cr.payer_account_id,
        cr.sender_id,
        cr.transaction_hash,
        cr.transaction_index,
        cr.transaction_result,
        coalesce(e.evm_address,'') as evm_address
    from contract_result cr
    left join entity e on e.id = cr.contract_id
    join t on cr.consensus_timestamp = t.consensus_timestamp
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
      `with record_file as (select consensus_end,hash,index from record_file), entity as (select evm_address, id from entity)
      select cl.bloom, cl.contract_id, cl.consensus_timestamp, cl.data, cl.index, cl.root_contract_id,
             cl.topic0, cl.topic1, cl.topic2, cl.topic3, cl.transaction_hash, cl.transaction_index,
             block_number,block_hash,evm_address
      from contract_log cl
      left join entity e on id = contract_id
      left join lateral (
        select index as block_number,hash as block_hash
        from record_file
        where consensus_end >= cl.consensus_timestamp
        order by consensus_end asc
        limit 1
      ) as block on true
      where cl.contract_id = $1
      order by cl.consensus_timestamp desc, cl.index asc
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
      `with record_file as (select consensus_end,hash,index from record_file), entity as (select evm_address, id from entity)
      select cl.bloom, cl.contract_id, cl.consensus_timestamp, cl.data, cl.index, cl.root_contract_id,
             cl.topic0, cl.topic1, cl.topic2, cl.topic3, cl.transaction_hash, cl.transaction_index,
             block_number, block_hash, evm_address
      from contract_log cl
      left join entity e on id = contract_id
      left join lateral (
        select index as block_number,hash as block_hash
        from record_file
        where consensus_end >= cl.consensus_timestamp
        order by consensus_end asc
        limit 1
      ) as block on true
      where cl.contract_id = $1 and cl.topic0 in ($2) and cl.topic1 in ($3) and cl.topic2 in ($4) and cl.topic3 in ($5)
      order by cl.consensus_timestamp desc, cl.index desc
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
      `(
        with record_file as (select consensus_end,hash,index from record_file), entity as (select evm_address, id from entity)
        select cl.bloom,cl.contract_id,cl.consensus_timestamp,cl.data,cl.index,cl.root_contract_id,cl.topic0,
          cl.topic1,cl.topic2,cl.topic3,cl.transaction_hash,cl.transaction_index,block_number,block_hash,evm_address
        from contract_log cl
        left join entity e on id = contract_id
        left join lateral (
          select index as block_number,hash as block_hash
          from record_file
          where consensus_end >= cl.consensus_timestamp
          order by consensus_end asc
          limit 1
        ) as block on true
        where cl.contract_id = $1 and cl.topic0 in ($2) and cl.index >= $4 and cl.consensus_timestamp = $5
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      ) union (
        with record_file as (select consensus_end,hash,index from record_file), entity as (select evm_address, id from entity)
        select cl.bloom,cl.contract_id,cl.consensus_timestamp,cl.data,cl.index,cl.root_contract_id,cl.topic0,
          cl.topic1,cl.topic2,cl.topic3,cl.transaction_hash,cl.transaction_index,block_number,block_hash,evm_address
        from contract_log cl
        left join entity e on id = contract_id
        left join lateral (
          select index as block_number,hash as block_hash
          from record_file
          where consensus_end >= cl.consensus_timestamp
          order by consensus_end asc
          limit 1
        ) as block on true
        where cl.contract_id = $1 and cl.topic0 in ($2) and cl.consensus_timestamp > $6
        order by cl.consensus_timestamp desc, cl.index desc
        limit $3
      )
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
        with record_file as (select  consensus_end, hash, index from record_file), entity as (select evm_address, id from entity)
        select
          cl.bloom,
          cl.contract_id,
          cl.consensus_timestamp,
          cl.data,
          cl.index,
          cl.root_contract_id,
          cl.topic0,
          cl.topic1,
          cl.topic2,
          cl.topic3,
          cl.transaction_hash,
          cl.transaction_index,
          block_number,
          block_hash,
          evm_address
        from
          contract_log cl
          left join entity e on id = contract_id
          left join lateral (
            select index as block_number, hash as block_hash
            from  record_file
            where consensus_end >= cl.consensus_timestamp
            order by consensus_end asc
            limit 1
          ) as block on true
        where  cl.contract_id = $1
          and cl.topic0 in ($2)
          and cl.index >= $4
          and cl.consensus_timestamp = $5
        order by
          cl.consensus_timestamp desc,
          cl.index desc
        limit $3
      ) union (
        with record_file as (select consensus_end, hash, index from record_file), entity as (select evm_address, id from entity)
        select
          cl.bloom,
          cl.contract_id,
          cl.consensus_timestamp,
          cl.data,
          cl.index,
          cl.root_contract_id,
          cl.topic0,
          cl.topic1,
          cl.topic2,
          cl.topic3,
          cl.transaction_hash,
          cl.transaction_index,
          block_number,
          block_hash,
          evm_address
        from
          contract_log cl
          left join entity e on id = contract_id
          left join lateral (
            select index as block_number, hash as block_hash
            from record_file
            where  consensus_end >= cl.consensus_timestamp
            order by consensus_end asc
            limit 1
          ) as block on true
        where
          cl.contract_id = $1
          and cl.topic0 in ($2)
          and cl.consensus_timestamp > $6
          and cl.consensus_timestamp < $7
        order by
          cl.consensus_timestamp desc,
          cl.index desc
        limit $3
      ) union (
        with record_file as (select consensus_end, hash, index from record_file), entity as (select evm_address, id from entity)
        select
          cl.bloom,
          cl.contract_id,
          cl.consensus_timestamp,
          cl.data,
          cl.index,
          cl.root_contract_id,
          cl.topic0,
          cl.topic1,
          cl.topic2,
          cl.topic3,
          cl.transaction_hash,
          cl.transaction_index,
          block_number,
          block_hash,
          evm_address
        from
          contract_log cl
          left join entity e on id = contract_id
          left join lateral (
            select index as block_number, hash as block_hash
            from  record_file
            where consensus_end >= cl.consensus_timestamp
            order by consensus_end asc
            limit 1
          ) as block on true
        where
          cl.contract_id = $1
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
        function_parameters: '0x0D',
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
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 123,
      },
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '0x0D',
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
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 123,
      },
      {
        contract_id: 2,
        consensus_timestamp: 2,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 123,
      },
      {
        contract_id: 3,
        consensus_timestamp: 3,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 124,
      },
      {
        contract_id: 3,
        consensus_timestamp: 4,
        function_parameters: '0x0D',
        amount: 10,
        payer_account_id: 124,
      },
      {
        contract_id: 3,
        consensus_timestamp: 5,
        function_parameters: '0x0D',
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
      data: '0x0012',
      index: 0,
      root_contract_id: 1,
      topic0: '0x000a',
    },
    {
      consensus_timestamp: 1,
      contract_id: 2,
      data: '0x0013',
      index: 1,
      root_contract_id: 1,
      topic0: '0x000b',
    },
    {
      consensus_timestamp: 2,
      contract_id: 1,
      data: '0x0014',
      index: 0,
      root_contract_id: 1,
      topic0: '0x000c',
    },
    {
      consensus_timestamp: 2,
      contract_id: 3,
      data: '0x0015',
      index: 1,
      root_contract_id: 1,
      topic0: '0x000d',
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
      function_parameters: '0x0D',
      amount: 10,
      payer_account_id: '5',
    },
    {
      contract_id: 3,
      consensus_timestamp: 6,
      function_parameters: '0x0D',
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
    const contractResults = await ContractService.getContractResultsByTimestamps('1');
    expect(contractResults).toHaveLength(0);
  });

  test('Sing row match single timestamp', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps(expected[0].consensusTimestamp);
    expect(pickContractResultFields(contractResults)).toIncludeSameMembers(expected.slice(0, 1));
  });

  test('Sing row match multiple timestamps', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps([
      expected[0].consensusTimestamp,
      '100',
    ]);
    expect(pickContractResultFields(contractResults)).toIncludeSameMembers(expected.slice(0, 1));
  });

  test('Multiple rows match multiple timestamps', async () => {
    const contractResults = await ContractService.getContractResultsByTimestamps(
      expected.map((e) => e.consensusTimestamp)
    );
    expect(pickContractResultFields(contractResults)).toIncludeSameMembers(expected);
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
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ea', 'hex'),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3eb', 'hex'),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ec', 'hex'),
        Buffer.from('ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ed', 'hex'),
      ],
      timestampOrder: 'desc',
      indexOrder: 'asc',
      limit: 25,
    });
    expect(response).toMatchObject(expectedContractLog);
  });
});

describe('ContractService.getContractStateChangesByTimestamps tests', () => {
  beforeEach(async () => {
    await integrationDomainOps.loadContractStateChanges([
      {
        consensus_timestamp: 1,
        contract_id: 3,
        slot: '01',
        value_read: '0101',
        value_written: 'a1a1',
      },
      {
        consensus_timestamp: 1,
        contract_id: 4,
        migration: true,
        slot: '02',
        value_read: '0202',
        value_written: 'a2a2',
      },
      {
        consensus_timestamp: 2,
        contract_id: 5,
        slot: '0a',
        value_read: '0303',
        value_written: 'a3a3',
      },
      {
        consensus_timestamp: 2,
        contract_id: 3,
        migration: true,
        slot: '0b',
        value_read: '0404',
        value_written: 'a4a4',
      },
      {
        consensus_timestamp: 3,
        contract_id: 4,
        slot: '0c',
        value_read: '0505',
        value_written: 'a5a5',
      },
      {
        consensus_timestamp: 4,
        contract_id: 5,
        slot: '0d',
        value_read: '0606',
        value_written: 'a6a6',
      },
    ]);
  });

  test('No match', async () => {
    const response = await ContractService.getContractStateChangesByTimestamps('10');
    expect(response).toBeEmpty();
  });

  test('Row match', async () => {
    const expected = [
      {
        consensusTimestamp: 1,
        contractId: 3,
        slot: Buffer.from([0x1]),
        valueRead: Buffer.from([0x1, 0x1]),
        valueWritten: Buffer.from([0xa1, 0xa1]),
      },
    ];
    const response = await ContractService.getContractStateChangesByTimestamps('1');
    expect(response).toMatchObject(expected);
  });

  test('Row match with contractId', async () => {
    const expected = [
      {
        consensusTimestamp: 1,
        contractId: 3,
        slot: Buffer.from([0x1]),
        valueRead: Buffer.from([0x1, 0x1]),
        valueWritten: Buffer.from([0xa1, 0xa1]),
      },
      {
        consensusTimestamp: 1,
        contractId: 4,
        slot: Buffer.from([0x2]),
        valueRead: Buffer.from([0x2, 0x2]),
        valueWritten: Buffer.from([0xa2, 0xa2]),
      },
    ];
    const response = await ContractService.getContractStateChangesByTimestamps('1', 4);
    expect(response).toMatchObject(expected);
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
        key: "E'\\0x326C0A221220F7ECD392568A9ECE84097C4B3C04C74AE52653D54398E132747B498B287245610A221220FA34ADAC826D3F878CCA5E4B074C7060DAE76259611543D6A876FF4E4B8B5C3A0A2212201ADBD17C33C48D59D0961356D5C0B19B391760A6504C3FC78D3094266FA290D2'",
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
        key: "E'\\0x326C0A2212203053E42F8D8978BC5999080C4A625BBB1BF96CBCA6BAD6A4796808A6812564490A221220CC16FF9223B2E8F8151E8EFB054203CEF5EE9AF6171D2649D46734ECDD7F5A280A22122097C6975280B82DC1969ABA4E7DDE4F478E872E04CD6E0FE3849EAFB5D86315F1'",
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
        key: "E'\\0x326C0A221220A13DDC50A38C7ED4A7F64CFD05E364746B8DABC3DAE8B2AFBE9A94FF2105AB1F0A2212202CECF7F1A3EADBBD678EC9D62EED162893A2069D456A4E5061E86F96C95F4FFF0A221220C6C448A8B628C11C55F773A3366D8B75E8188EEF46A50A2CCDDDA6B3B4EF55E3'",
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
        key: "E'\\0x326C0A221220F7ECD392568A9ECE84097C4B3C04C74AE52653D54398E132747B498B287245610A221220FA34ADAC826D3F878CCA5E4B074C7060DAE76259611543D6A876FF4E4B8B5C3A0A2212201ADBD17C33C48D59D0961356D5C0B19B391760A6504C3FC78D3094266FA290D2'",
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
        key: "E'\\0x326C0A2212203053E42F8D8978BC5999080C4A625BBB1BF96CBCA6BAD6A4796808A6812564490A221220CC16FF9223B2E8F8151E8EFB054203CEF5EE9AF6171D2649D46734ECDD7F5A280A22122097C6975280B82DC1969ABA4E7DDE4F478E872E04CD6E0FE3849EAFB5D86315F1'",
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
        key: "E'\\0x326C0A221220A13DDC50A38C7ED4A7F64CFD05E364746B8DABC3DAE8B2AFBE9A94FF2105AB1F0A2212202CECF7F1A3EADBBD678EC9D62EED162893A2069D456A4E5061E86F96C95F4FFF0A221220C6C448A8B628C11C55F773A3366D8B75E8188EEF46A50A2CCDDDA6B3B4EF55E3'",
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

describe('ContractService.getContractResultsByHash tests', () => {
  const ethereumTxHash = '4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392';
  const ethereumTxHashBuffer = Buffer.from(ethereumTxHash, 'hex');
  const ethereumTxType = TransactionType.getProtoId('ETHEREUMTRANSACTION');
  const contractCreateType = TransactionType.getProtoId('CONTRACTCREATEINSTANCE');
  const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
  const successTransactionResult = TransactionResult.getProtoId('SUCCESS');
  const wrongNonceTransactionResult = TransactionResult.getProtoId('WRONG_NONCE');

  const inputContractResults = [
    {
      consensus_timestamp: 1,
      payerAccountId: 10,
      type: ethereumTxType,
      transaction_result: successTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 2,
      payerAccountId: 10,
      type: ethereumTxType,
      transaction_result: duplicateTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 3,
      payerAccountId: 10,
      type: ethereumTxType,
      transaction_result: wrongNonceTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 4,
      payerAccountId: 10,
      type: ethereumTxType,
      transaction_result: successTransactionResult,
      transaction_index: 1,
      transaction_hash: ethereumTxHash,
      gasLimit: 1000,
    },
    {
      consensus_timestamp: 5,
      payerAccountId: 10,
      type: contractCreateType,
      transaction_hash: '96ecf2e0cf1c8f7e2294ec731b2ad1aff95d9736f4ba15b5bbace1ad2766cc1c',
      gasLimit: 1000,
    },
  ];

  const inputEthTransaction = [
    {
      consensus_timestamp: 1,
      hash: ethereumTxHash,
    },
    {
      consensus_timestamp: 2,
      hash: ethereumTxHash,
    },
    {
      consensus_timestamp: 3,
      hash: ethereumTxHash,
    },
    {
      consensus_timestamp: 4,
      hash: ethereumTxHash,
    },
  ];

  const expectedTransaction = {
    consensusTimestamp: 1,
    transactionHash: ethereumTxHash,
  };

  // pick the fields of interests, otherwise expect will fail since the Transaction object has other fields
  const pickTransactionFields = (transactions) => {
    return transactions
      .map((tx) => _.pick(tx, ['consensusTimestamp', 'transactionHash']))
      .map((tx) => ({...tx, transactionHash: Buffer.from(tx.transactionHash).toString('hex')}));
  };

  beforeEach(async () => {
    await integrationDomainOps.loadContractResults(inputContractResults);
    await integrationDomainOps.loadEthereumTransactions(inputEthTransaction);
  });

  test('No match', async () => {
    const contractResults = await ContractService.getContractResultsByHash(
      Buffer.from('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6393', 'hex')
    );

    expect(contractResults).toHaveLength(0);
  });

  test('Match all transactions by same hash', async () => {
    const contractResults = await ContractService.getContractResultsByHash(ethereumTxHashBuffer);
    expect(pickTransactionFields(contractResults)).toIncludeSameMembers([
      expectedTransaction,
      {consensusTimestamp: 2, transactionHash: ethereumTxHash},
      {consensusTimestamp: 3, transactionHash: ethereumTxHash},
      {consensusTimestamp: 4, transactionHash: ethereumTxHash},
    ]);
  });

  test('Match all transactions with no duplicates and wrong nonces', async () => {
    const contractResults = await ContractService.getContractResultsByHash(ethereumTxHashBuffer, [
      duplicateTransactionResult,
      wrongNonceTransactionResult,
    ]);
    expect(pickTransactionFields(contractResults)).toIncludeSameMembers([
      expectedTransaction,
      {consensusTimestamp: 4, transactionHash: ethereumTxHash},
    ]);
  });

  test('Match the oldest tx with no duplicates and wrong nonces', async () => {
    const contractResults = await ContractService.getContractResultsByHash(
      ethereumTxHashBuffer,
      [duplicateTransactionResult, wrongNonceTransactionResult],
      1
    );
    expect(pickTransactionFields(contractResults)).toIncludeSameMembers([expectedTransaction]);
  });

  test('Match hedera transactions by eth hash', async () => {
    const contractResults = await ContractService.getContractResultsByHash(
      Buffer.from('96ecf2e0cf1c8f7e2294ec731b2ad1aff95d9736f4ba15b5bbace1ad2766cc1c', 'hex'),
      [duplicateTransactionResult, wrongNonceTransactionResult],
      1
    );

    expect(pickTransactionFields(contractResults)).toIncludeSameMembers([
      {consensusTimestamp: 5, transactionHash: '96ecf2e0cf1c8f7e2294ec731b2ad1aff95d9736f4ba15b5bbace1ad2766cc1c'},
    ]);
  });
});

describe('ContractService.getContractActionsByConsensusTimestamp tests', () => {
  test('No match', async () => {
    const res = await ContractService.getContractActionsByConsensusTimestamp(
      '1676540001234390005',
      2000,
      [],
      orderFilterValues.ASC,
      100
    );
    expect(res.length).toEqual(0);
  });

  test('Multiple rows match', async () => {
    await integrationDomainOps.loadContractActions([
      {consensus_timestamp: '1676540001234390005', payer_account_id: 2000},
      {consensus_timestamp: '1676540001234390005', index: 2, payer_account_id: 2000},
    ]);
    const res = await ContractService.getContractActionsByConsensusTimestamp(
      '1676540001234390005',
      2000,
      [],
      orderFilterValues.ASC,
      100
    );
    expect(res.length).toEqual(2);
  });

  test('One row match', async () => {
    await integrationDomainOps.loadContractActions([
      {consensus_timestamp: '1676540001234390005', payer_account_id: 2000},
      {consensus_timestamp: '1676540001234390006', payer_account_id: 2000},
    ]);
    const res = await ContractService.getContractActionsByConsensusTimestamp(
      '1676540001234390005',
      2000,
      [],
      orderFilterValues.ASC,
      100
    );
    expect(res.length).toEqual(1);
  });
});

describe('ContractService.getContractStateByIdAndFilters tests', () => {
  const contractState = [
    {contract_id: 9999, slot: '01', value: 10},
    {contract_id: 9999, slot: '02', value: 20},
  ];

  const contractStateChanges = [
    {
      consensus_timestamp: 1,
      contract_id: 4,
      slot: Buffer.from([0x1]),
      value_read: '0101',
      value_written: 'a1a1',
    },
    {
      consensus_timestamp: 4,
      contract_id: 4,
      slot: Buffer.from([0x2]),
      value_read: '0202',
      value_written: 'a2a2',
    },
    {
      consensus_timestamp: 4,
      contract_id: 4,
      slot: Buffer.from([0x3]),
      value_read: '0202',
      value_written: 'a2a2',
    },
    {
      consensus_timestamp: 8,
      contract_id: 4,
      slot: Buffer.from([0x3]),
      value_read: 'a2a2',
      value_written: 'a222',
    },
  ];

  test('No match', async () => {
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 1000}]);

    expect(res.length).toEqual(0);
  });

  test('Multiple rows match', async () => {
    await integrationDomainOps.loadContractStates(contractState);
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 9999}]);

    expect(res.length).toEqual(2);
  });

  test('Multiple rows match with timestamp', async () => {
    await integrationDomainOps.loadContractStateChanges(contractStateChanges);
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 4},
      {query: 'consensus_timestamp <= ', param: 5}],
      orderFilterValues.ASC,
      100,
      true);

    expect(res.length).toEqual(3);
  });

  test('Multiple rows match with timestamp and slot', async () => {
    await integrationDomainOps.loadContractStateChanges(contractStateChanges);
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 4},
      {query: 'consensus_timestamp <= ', param: 5},
      {query: 'slot =', param: Buffer.from('03','hex')}],
      orderFilterValues.ASC,
      100,
      true);

    expect(res.length).toEqual(1);
  });

  test('One row match by contract_id', async () => {
    await integrationDomainOps.loadContractStates([
      {contract_id: 9999, slot: '01', value: 10},
      {contract_id: 9000, slot: '02', value: 20},
    ]);
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 9999}]);

    expect(res.length).toEqual(1);
  });

  test('Multiple rows match desc order', async () => {
    await integrationDomainOps.loadContractStates(contractState);
    const res = await ContractService.getContractStateByIdAndFilters([{query: 'contract_id =', param: 9999}]);

    expect(res[0].slot.readInt8()).toEqual(1);
    expect(res[1].slot.readInt8()).toEqual(2);
    expect(res.length).toEqual(2);
  });

  test('One row match (limit=1)', async () => {
    await integrationDomainOps.loadContractStates(contractState);
    const res = await ContractService.getContractStateByIdAndFilters(
      [{query: 'contract_id =', param: 9999}],
      orderFilterValues.ASC,
      1
    );

    expect(res.length).toEqual(1);
  });
});
