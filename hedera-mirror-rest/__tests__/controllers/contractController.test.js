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

const {Range} = require('pg-range');

const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../../config');
const constants = require('../../constants');
const contracts = require('../../controllers/contractController');
const {assertSqlQueryEqual} = require('../testutils');
const utils = require('../../utils');
const {Contract} = require('../../model');

const contractFields = [
  Contract.AUTO_RENEW_ACCOUNT_ID,
  Contract.AUTO_RENEW_PERIOD,
  Contract.CREATED_TIMESTAMP,
  Contract.DELETED,
  Contract.EVM_ADDRESS,
  Contract.EXPIRATION_TIMESTAMP,
  Contract.FILE_ID,
  Contract.ID,
  Contract.KEY,
  Contract.MAX_AUTOMATIC_TOKEN_ASSOCIATIONS,
  Contract.MEMO,
  Contract.OBTAINER_ID,
  Contract.PERMANENT_REMOVAL,
  Contract.PROXY_ACCOUNT_ID,
  Contract.TIMESTAMP_RANGE,
].map((column) => Contract.getFullName(column));
const contractWithInitcodeFields = [...contractFields, Contract.getFullName(Contract.INITCODE)];

const emptyFilterString = 'empty filters';
const primaryContractFilter = 'cr.contract_id = $1';
const defaultInternalFilter = 't.nonce=$2';

describe('extractSqlFromContractFilters', () => {
  const defaultExpected = {
    filterQuery: '',
    params: [defaultLimit],
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
    limitQuery: 'limit $1',
  };

  const specs = [
    {
      name: emptyFilterString,
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
      name: 'order asc',
      input: [
        {
          key: constants.filterKeys.ORDER,
          operator: utils.opsMap.eq,
          value: constants.orderFilterValues.ASC,
        },
      ],
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.ASC,
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
        params: ['1000', '1001', '1002', defaultLimit],
        limitQuery: 'limit $4',
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, async () => {
      expect(await contracts.extractSqlFromContractFilters(spec.input)).toEqual(spec.expected);
    });
  });
});

describe('extractTimestampConditionsFromContractFilters', () => {
  const timestampKey = constants.filterKeys.TIMESTAMP;
  const timestampColumn = Contract.getFullName(Contract.TIMESTAMP_RANGE);
  const specs = [
    {
      name: emptyFilterString,
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
          `${timestampColumn} && $1`,
          `${timestampColumn} && $2`,
          `${timestampColumn} && $3`,
          `${timestampColumn} && $4`,
          `${timestampColumn} && $5`,
          `not ${timestampColumn} @> $6`,
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
    auto_renew_account_id: '1005',
    auto_renew_period: '1000',
    created_timestamp: '999123456789',
    deleted: false,
    evm_address: null,
    expiration_timestamp: '99999999000000000',
    file_id: '2800',
    id: '3001',
    key: Buffer.from([0xaa, 0xbb, 0xcc, 0x77]),
    max_automatic_token_associations: 0,
    memo: 'sample contract',
    obtainer_id: '2005',
    permanent_removal: null,
    proxy_account_id: '2002',
    timestamp_range: Range('1000123456789', '2000123456789', '[)'),
  };
  const expected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: 'aabbcc77',
    },
    auto_renew_account: '0.0.1005',
    auto_renew_period: 1000,
    contract_id: '0.0.3001',
    created_timestamp: '999.123456789',
    deleted: false,
    evm_address: '0x0000000000000000000000000000000000000bb9',
    expiration_timestamp: '99999999.000000000',
    file_id: '0.0.2800',
    max_automatic_token_associations: 0,
    memo: 'sample contract',
    obtainer_id: '0.0.2005',
    permanent_removal: null,
    proxy_account_id: '0.0.2002',
    timestamp: {
      from: '1000.123456789',
      to: '2000.123456789',
    },
  };

  test('verify', () => {
    expect(contracts.formatContractRow(input)).toEqual(expected);
  });
});

describe('getContractByIdOrAddressQuery', () => {
  const mainQuery = `select ${[
    ...contractFields,
    "coalesce(encode(c.initcode, 'hex')::bytea, cf.bytecode) as bytecode",
  ]}
    from contract c, contract_file cf`;

  const queryForTable = ({table, extraConditions, columnName}) => {
    return `select ${contractWithInitcodeFields}
      from ${table} c
      where ${(extraConditions && extraConditions.join(' and ') + ' and ') || ''} c.${columnName} = $3`;
  };

  const timestampConditions = ['c.timestamp_range && $1', 'c.timestamp_range && $2'];

  const specs = [
    {
      name: 'latest',
      isCreate2Test: false,
      input: {timestampConditions: [], timestampParams: [1234, 5678], contractIdParam: '0.0.2'},
      expected: (columnName) => `
        with contract as (
          ${queryForTable({table: 'contract', columnName})}
        ), contract_file as (
            ${contracts.fileDataQuery}
        )
        ${mainQuery}`,
    },
    {
      name: 'historical',
      isCreate2Test: true,
      input: {
        timestampConditions,
        timestampParams: [5678, 1234],
        contractIdParam: '70f2b2914a2a4b783faefb75f459a580616fcb5e',
      },
      expected: (columnName) => `
        with contract as (
            ${queryForTable({table: 'contract', extraConditions: timestampConditions, columnName})}
            union
            ${queryForTable({table: 'contract_history', extraConditions: timestampConditions, columnName})}
            order by timestamp_range desc
            limit 1
        ), contract_file as (
            ${contracts.fileDataQuery}
        )
        ${mainQuery}`,
    },
    {
      name: 'latest',
      isCreate2Test: true,
      input: {
        timestampConditions: [],
        timestampParams: [1234, 5678],
        contractIdParam: '70f2b2914a2a4b783faefb75f459a580616fcb5e',
      },
      expected: (columnName) => `
        with contract as (
          ${queryForTable({table: 'contract', columnName})}
        ), contract_file as (
            ${contracts.fileDataQuery}
        )
        ${mainQuery}`,
    },
    {
      name: 'historical',
      isCreate2Test: false,
      input: {timestampConditions, timestampParams: [5678, 1234], contractIdParam: '0.0.1'},
      expected: (columnName) => `
        with contract as (
            ${queryForTable({table: 'contract', extraConditions: timestampConditions, columnName})}
            union
            ${queryForTable({table: 'contract_history', extraConditions: timestampConditions, columnName})}
            order by timestamp_range desc
            limit 1
        ), contract_file as (
            ${contracts.fileDataQuery}
        )
        ${mainQuery}`,
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      const actualQuery = contracts.getContractByIdOrAddressQuery(spec.input).query;
      const expectedQuery = spec.expected(spec.isCreate2Test ? Contract.EVM_ADDRESS : Contract.ID);
      assertSqlQueryEqual(actualQuery, expectedQuery);
    });
  });
});

describe('getContractsQuery', () => {
  const specs = [
    {
      name: 'empty whereQuery',
      input: {
        whereQuery: '',
        limitQuery: 'limit $1',
        order: 'asc',
      },
      expected: `select ${contractFields}
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
      expected: `select ${contractFields}
        from contract c
        where c.id <= $1
        order by c.id desc
        limit $2`,
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      assertSqlQueryEqual(
        contracts.getContractsQuery(spec.input.whereQuery, spec.input.limitQuery, spec.input.order),
        spec.expected
      );
    });
  });
});

describe('getLastNonceParamValue', () => {
  test('default', () => {
    expect(contracts.getLastNonceParamValue({})).toBe(0);
  });

  test('single', () => {
    expect(contracts.getLastNonceParamValue({[constants.filterKeys.NONCE]: 10})).toBe(10);
  });

  test('array', () => {
    expect(contracts.getLastNonceParamValue({[constants.filterKeys.NONCE]: [1, 2, 3]})).toBe(3);
  });
});

describe('extractContractResultsByIdQuery', () => {
  const defaultContractId = 1;
  const defaultNonce = 0;
  const defaultExpected = {
    conditions: [primaryContractFilter, defaultInternalFilter],
    params: [defaultContractId, defaultNonce],
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
  };

  const specs = [
    {
      name: emptyFilterString,
      input: {filter: [], contractId: defaultContractId},
      expected: defaultExpected,
    },
    {
      name: 'limit',
      input: {
        filter: [
          {
            key: constants.filterKeys.LIMIT,
            operator: utils.opsMap.eq,
            value: 20,
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        limit: 20,
      },
    },
    {
      name: 'order',
      input: {
        filter: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.ASC,
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        order: constants.orderFilterValues.ASC,
      },
    },
    {
      name: 'contractResult.from',
      input: {
        filter: [
          {
            key: constants.filterKeys.FROM,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.FROM,
            operator: utils.opsMap.eq,
            value: '1002',
          },
          {
            key: constants.filterKeys.FROM,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [primaryContractFilter, 'cr.payer_account_id > $2', 't.nonce=$3', 'cr.payer_account_id in ($4,$5)'],
        params: [defaultContractId, '1000', defaultNonce, '1001', '1002'],
      },
    },
    {
      name: 'contractResult.timestamp',
      input: {
        filter: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.eq,
            value: '1002',
          },
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [
          primaryContractFilter,
          'cr.consensus_timestamp > $2',
          't.nonce=$3',
          'cr.consensus_timestamp in ($4,$5)',
        ],
        params: [defaultContractId, '1000', defaultNonce, '1001', '1002'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, async () => {
      expect(
        await contracts.extractContractResultsByIdQuery(
          spec.input.filter,
          spec.input.contractId,
          contracts.contractResultsByIdParamSupportMap
        )
      ).toEqual(spec.expected);
    });
  });
});

describe('validateContractIdAndConsensusTimestampParam', () => {
  const validSpecs = [
    {
      contractId: '1',
      consensusTimestamp: '1',
    },
    {
      contractId: '0.1',
      consensusTimestamp: '1.1',
    },
    {
      contractId: '0.0.1',
      consensusTimestamp: '167654.000123456',
    },
  ];

  validSpecs.forEach((spec) => {
    test(`valid case - ${JSON.stringify(spec)}`, () => {
      expect(() =>
        contracts.validateContractIdAndConsensusTimestampParam(spec.consensusTimestamp, spec.contractId)
      ).not.toThrow();
    });
  });

  const inValidSpecs = [
    {
      contractId: '1',
      consensusTimestamp: 'y',
    },
    {
      contractId: 'x',
      consensusTimestamp: '1',
    },
    {
      contractId: 'x',
      consensusTimestamp: 'y',
    },
  ];

  inValidSpecs.forEach((spec) => {
    test(`invalid case - ${JSON.stringify(spec)}`, () => {
      expect(() =>
        contracts.validateContractIdAndConsensusTimestampParam(spec.consensusTimestamp, spec.contractId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('validateContractIdParam', () => {
  const validSpecs = [
    {
      contractId: '1',
    },
    {
      contractId: '0.1',
    },
    {
      contractId: '0.0.1',
    },
  ];

  validSpecs.forEach((spec) => {
    test(`valid contract ID - ${spec.contractId}`, () => {
      expect(() => contracts.validateContractIdParam(spec.contractId)).not.toThrow();
    });
  });

  const inValidSpecs = [
    {
      contractId: '-1',
    },
    {
      contractId: 'x',
    },
    {
      contractId: '0.1.2.3',
    },
  ];

  inValidSpecs.forEach((spec) => {
    test(`invalid contract ID - ${spec.contractId}`, () => {
      expect(() => contracts.validateContractIdParam(spec.contractId)).toThrowErrorMatchingSnapshot();
    });
  });
});

const defaultContractLogCondition = 'cl.contract_id = $1';
describe('extractContractLogsByIdQuery', () => {
  const defaultContractId = 1;
  const defaultExpected = {
    conditions: [defaultContractLogCondition],
    params: [defaultContractId],
    timestampOrder: constants.orderFilterValues.DESC,
    indexOrder: constants.orderFilterValues.DESC,
    limit: defaultLimit,
  };
  const specs = [
    {
      name: emptyFilterString,
      input: {filter: [], contractId: defaultContractId},
      expected: {
        ...defaultExpected,
      },
    },
    {
      name: 'index',
      input: {
        filter: [
          {
            key: constants.filterKeys.INDEX,
            operator: utils.opsMap.eq,
            value: '2',
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition, 'cl.index = $2'],
        params: [defaultContractId, '2'],
      },
    },
    {
      name: 'timestamp',
      input: {
        filter: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.eq,
            value: '1001',
          },
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.eq,
            value: '1002',
          },
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.gt,
            value: '1000',
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition, 'cl.consensus_timestamp > $2', 'cl.consensus_timestamp in ($3,$4)'],
        params: [defaultContractId, '1000', '1001', '1002'],
      },
    },
    {
      name: 'topics',
      input: {
        filter: [
          {
            key: constants.filterKeys.TOPIC0,
            operator: utils.opsMap.eq,
            value: '0x0011',
          },
          {
            key: constants.filterKeys.TOPIC0,
            operator: utils.opsMap.eq,
            value: '0x000013',
          },
          {
            key: constants.filterKeys.TOPIC2,
            operator: utils.opsMap.eq,
            value: '0x140',
          },
          {
            key: constants.filterKeys.TOPIC3,
            operator: utils.opsMap.eq,
            value: '0000150',
          },
          {
            key: constants.filterKeys.TOPIC3,
            operator: utils.opsMap.eq,
            value: '0000150',
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition, 'cl.topic0 in ($2,$3)', 'cl.topic2 in ($4)', 'cl.topic3 in ($5,$6)'],
        params: [
          defaultContractId,
          Buffer.from('11', 'hex'),
          Buffer.from('13', 'hex'),
          Buffer.from('0140', 'hex'),
          Buffer.from('0150', 'hex'),
          Buffer.from('0150', 'hex'),
        ],
      },
    },
    {
      name: 'limit',
      input: {
        filter: [
          {
            key: constants.filterKeys.LIMIT,
            operator: utils.opsMap.eq,
            value: 20,
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        limit: 20,
      },
    },
    {
      name: 'order asc',
      input: {
        filter: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.ASC,
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        timestampOrder: constants.orderFilterValues.ASC,
        indexOrder: constants.orderFilterValues.ASC,
      },
    },
    {
      name: 'order desc',
      input: {
        filter: [
          {
            key: constants.filterKeys.ORDER,
            operator: utils.opsMap.eq,
            value: constants.orderFilterValues.DESC,
          },
        ],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        timestampOrder: constants.orderFilterValues.DESC,
        indexOrder: constants.orderFilterValues.DESC,
      },
    },
  ];
  const errorSpecs = [
    {
      name: 'timestamp not equal operator',
      input: {
        filter: [
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.ne,
            value: constants.orderFilterValues.DESC,
          },
        ],
        contractId: defaultContractId,
      },
      errorMessage: 'Not equals operator not supported for timestamp param',
    },
    {
      name: 'multiple index',
      input: {
        filter: [
          {
            key: constants.filterKeys.INDEX,
            operator: utils.opsMap.lt,
            value: '1',
          },
          {
            key: constants.filterKeys.INDEX,
            operator: utils.opsMap.gt,
            value: '2',
          },
        ],
        contractId: defaultContractId,
      },
      errorMessage: 'Multiple params not allowed for index',
    },
  ];
  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(contracts.extractContractLogsByIdQuery(spec.input.filter, spec.input.contractId)).toEqual(spec.expected);
    });
  });

  errorSpecs.forEach((spec) => {
    test(`error - ${spec.name}`, () => {
      expect(() =>
        contracts.extractContractLogsByIdQuery(spec.input.filter, spec.input.contractId)
      ).toThrowErrorMatchingSnapshot();
    });
  });
});

describe('checkTimestampsForTopics', () => {
  test('no topic params', () => {
    expect(() => contracts.checkTimestampsForTopics([])).not.toThrow();
  });
  test('all topics valid timestamp', () => {
    const filters = [
      {
        key: constants.filterKeys.TOPIC0,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TOPIC1,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TOPIC2,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TOPIC3,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.eq,
        value: '123',
      },
    ];
    expect(() => contracts.checkTimestampsForTopics(filters)).not.toThrow();
  });
  test('valid timestamp no topics', () => {
    const filters = [
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.eq,
        value: '123',
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.gt,
        value: '111',
      },
    ];
    expect(() => contracts.checkTimestampsForTopics(filters)).not.toThrow();
  });
  test('topic0 param no timestamps', () => {
    const filters = [
      {
        key: constants.filterKeys.TOPIC0,
        operator: utils.opsMap.gte,
        value: '0x1234',
      },
    ];
    expect(() => contracts.checkTimestampsForTopics(filters)).toThrowErrorMatchingSnapshot();
  });
  test('topic1 param one timestamp gt', () => {
    const filters = [
      {
        key: constants.filterKeys.TOPIC1,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.gte,
        value: '123',
      },
    ];
    expect(() => contracts.checkTimestampsForTopics(filters)).toThrowErrorMatchingSnapshot();
  });
  test('topic2 param one timestamp lt', () => {
    const filters = [
      {
        key: constants.filterKeys.TOPIC2,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.lt,
        value: '123',
      },
    ];
    expect(() => contracts.checkTimestampsForTopics(filters)).toThrowErrorMatchingSnapshot();
  });
  test('topic0 param', () => {
    const filters = [
      {
        key: constants.filterKeys.TOPIC3,
        operator: utils.opsMap.eq,
        value: '0x1234',
      },
      {
        key: constants.filterKeys.TIMESTAMP,
        operator: utils.opsMap.ne,
        value: '123',
      },
    ];
    expect(() => contracts.checkTimestampsForTopics(filters)).toThrowErrorMatchingSnapshot();
  });
});
