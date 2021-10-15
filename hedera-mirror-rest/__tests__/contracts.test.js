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

const {Range} = require('pg-range');

const config = require('../config');
const constants = require('../constants');
const contracts = require('../contracts');
const {formatSqlQueryString} = require('./testutils');
const utils = require('../utils');

const contractFields = [
  'auto_renew_period',
  'created_timestamp',
  'deleted',
  'expiration_timestamp',
  'file_id',
  'id',
  'key',
  'memo',
  'obtainer_id',
  'proxy_account_id',
  'timestamp_range',
].map((f) => `c.${f}`);

describe('extractSqlFromContractFilters', () => {
  const defaultExpected = {
    filterQuery: '',
    params: [config.maxLimit],
    order: constants.orderFilterValues.ASC,
    limit: config.maxLimit,
    limitQuery: 'limit $1',
  };

  const specs = [
    {
      name: 'empty filters',
      input: [],
      expected: defaultExpected,
    },
    {
      name: 'limit',
      input: [
        {
          key: constants.filterKeys.LIMIT,
          operator: utils.opsMap.eq,
          value: 20,
        },
      ],
      expected: {
        ...defaultExpected,
        params: [20],
        limit: 20,
      },
    },
    {
      name: 'order',
      input: [
        {
          key: constants.filterKeys.ORDER,
          operator: utils.opsMap.eq,
          value: constants.orderFilterValues.DESC,
        },
      ],
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.DESC,
      },
    },
    {
      name: 'contract.id',
      input: [
        {
          key: constants.filterKeys.CONTRACT_ID,
          operator: utils.opsMap.eq,
          value: '1001',
        },
        {
          key: constants.filterKeys.CONTRACT_ID,
          operator: utils.opsMap.eq,
          value: '1002',
        },
        {
          key: constants.filterKeys.CONTRACT_ID,
          operator: utils.opsMap.gt,
          value: '1000',
        },
      ],
      expected: {
        ...defaultExpected,
        filterQuery: 'where c.id > $1 and c.id in ($2,$3)',
        params: ['1000', '1001', '1002', config.maxLimit],
        limitQuery: 'limit $4',
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(contracts.extractSqlFromContractFilters(spec.input)).toEqual(spec.expected);
    });
  });
});

describe('extractTimestampConditionsFromContractFilters', () => {
  const timestampKey = constants.filterKeys.TIMESTAMP;
  const timestampColumn = 'c.timestamp_range';
  const specs = [
    {
      name: 'empty filters',
      input: [],
      expected: {
        conditions: [],
        params: [],
      },
    },
    {
      name: 'no timestamp filters',
      input: [
        {
          key: constants.filterKeys.ORDER,
          operator: utils.opsMap.eq,
          value: constants.orderFilterValues.ASC,
        },
      ],
      expected: {
        conditions: [],
        params: [],
      },
    },
    {
      name: 'timestamp filters',
      input: [
        {
          key: timestampKey,
          operator: utils.opsMap.eq, // will be converted to lte
          value: '200',
        },
        {
          key: timestampKey,
          operator: utils.opsMap.gt,
          value: '201',
        },
        {
          key: timestampKey,
          operator: utils.opsMap.gte,
          value: '202',
        },
        {
          key: timestampKey,
          operator: utils.opsMap.lt,
          value: '203',
        },
        {
          key: timestampKey,
          operator: utils.opsMap.lte,
          value: '204',
        },
        {
          key: timestampKey,
          operator: utils.opsMap.ne,
          value: '205',
        },
      ],
      expected: {
        conditions: [
          `${timestampColumn} && $2`,
          `${timestampColumn} && $3`,
          `${timestampColumn} && $4`,
          `${timestampColumn} && $5`,
          `${timestampColumn} && $6`,
          `not ${timestampColumn} @> $7`,
        ],
        params: [
          Range(null, '200', '(]'),
          Range('201', null, '()'),
          Range('202', null, '[)'),
          Range(null, '203', '()'),
          Range(null, '204', '(]'),
          Range('205', '205', '[]'),
        ],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(contracts.extractTimestampConditionsFromContractFilters(spec.input)).toEqual(spec.expected);
    });
  });
});

describe('formatContractRow', () => {
  const input = {
    auto_renew_period: '1000',
    created_timestamp: '999123456789',
    deleted: false,
    expiration_timestamp: '99999999000000000',
    file_id: '2800',
    id: '3001',
    key: Buffer.from([0xaa, 0xbb, 0xcc, 0x77]),
    memo: 'sample contract',
    obtainer_id: '2005',
    proxy_account_id: '2002',
    timestamp_range: Range('1000123456789', '2000123456789', '[)'),
  };
  const expected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: 'aabbcc77',
    },
    auto_renew_period: 1000,
    contract_id: '0.0.3001',
    created_timestamp: '999.123456789',
    deleted: false,
    expiration_timestamp: '99999999.000000000',
    file_id: '0.0.2800',
    memo: 'sample contract',
    obtainer_id: '0.0.2005',
    proxy_account_id: '0.0.2002',
    solidity_address: '0x00000000000000000000000000000bb900000000',
    timestamp: {
      from: '1000.123456789',
      to: '2000.123456789',
    },
  };

  test('verify', () => {
    expect(contracts.formatContractRow(input)).toEqual(expected);
  });
});

describe('getContractByIdQuery', () => {
  const mainQuery = `select ${[...contractFields, 'f.file_data bytecode']}
    from contract c, file_data f
    where c.file_id = f.entity_id and f.consensus_timestamp <= c.created_timestamp`;
  const queryForTable = (table, extraConditions) => {
    return `select ${contractFields.join(',')}
      from ${table} c
      where c.id = $1 ${(extraConditions && ' and ' + extraConditions.join(' and ')) || ''}`;
  };
  const timestampConditions = ['c.timestamp_range && $2', 'c.timestamp_range && $3'];

  const specs = [
    {
      name: 'latest',
      input: [],
      expected: `with contract as (
        ${queryForTable('contract')}
      )
      ${mainQuery}`,
    },
    {
      name: 'historical',
      input: timestampConditions,
      expected: `with contract as (
        ${queryForTable('contract', timestampConditions)}
        union
        ${queryForTable('contract_history', timestampConditions)}
        order by timestamp_range desc
        limit 1
	    )
	    ${mainQuery}`,
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      const actual = formatSqlQueryString(contracts.getContractByIdQuery(spec.input));
      expect(actual).toEqual(formatSqlQueryString(spec.expected));
    });
  });
});

describe('getContractsQuery', () => {
  const defaultInput = {
    whereQuery: '',
    limitQuery: 'limit $1',
    order: 'asc',
  };

  const specs = [
    {
      name: 'empty whereQuery',
      input: {
        whereQuery: '',
        limitQuery: 'limit $1',
        order: 'asc',
      },
      expected: `select ${contractFields.join(',')}
        from contract c
        order by c.id asc
        limit $1`,
    },
    {
      name: 'non-empty whereQuery',
      input: {
        whereQuery: 'where c.id <= $1',
        limitQuery: 'limit $2',
        order: 'desc',
      },
      expected: `select ${contractFields.join(',')}
        from contract c
        where c.id <= $1
        order by c.id desc
        limit $2`,
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      const actual = formatSqlQueryString(
        contracts.getContractsQuery(spec.input.whereQuery, spec.input.limitQuery, spec.input.order)
      );
      expect(actual).toEqual(formatSqlQueryString(spec.expected));
    });
  });
});
