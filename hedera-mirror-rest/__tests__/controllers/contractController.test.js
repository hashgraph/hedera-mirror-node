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
const {FileDataService} = require('../../service');
const Bound = require('../../controllers/bound');

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

const timestampEq1002Filter = {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.eq, value: '1002'};
const timestampGt1002Filter = {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.gt, value: '1002'};
const timestampGte1002Filter = {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.gte, value: '1002'};
const timestampEq1005Filter = {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.eq, value: '1005'};
const timestampLt1005Filter = {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.lt, value: '1005'};
const timestampLte1005Filter = {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.lte, value: '1005'};

const indexEq2Filter = {key: constants.filterKeys.INDEX, operator: utils.opsMap.eq, value: '2'};
const indexGt2Filter = {key: constants.filterKeys.INDEX, operator: utils.opsMap.gt, value: '2'};
const indexGte2Filter = {key: constants.filterKeys.INDEX, operator: utils.opsMap.gte, value: '2'};
const indexEq5Filter = {key: constants.filterKeys.INDEX, operator: utils.opsMap.eq, value: '5'};
const indexLt5Filter = {key: constants.filterKeys.INDEX, operator: utils.opsMap.lt, value: '5'};
const indexLte5Filter = {key: constants.filterKeys.INDEX, operator: utils.opsMap.lte, value: '5'};

const emptyFilterString = 'empty filters';
const primaryContractFilter = 'cr.contract_id = $1';

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
        params: [1000, 1001, 1002, defaultLimit],
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
    auto_renew_account_id: 1005,
    auto_renew_period: 1000n,
    created_timestamp: 999123456789n,
    deleted: false,
    evm_address: null,
    expiration_timestamp: 99999999000000000n,
    file_id: 2800,
    id: 3001,
    key: Buffer.from([0xaa, 0xbb, 0xcc, 0x77]),
    max_automatic_token_associations: 0,
    memo: 'sample contract',
    obtainer_id: 2005,
    permanent_removal: null,
    proxy_account_id: 2002,
    timestamp_range: Range('1000123456789', '2000123456789', '[)'),
  };
  const expected = {
    admin_key: {
      _type: 'ProtobufEncoded',
      key: 'aabbcc77',
    },
    auto_renew_account: '0.0.1005',
    auto_renew_period: 1000n,
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
      expectedParams: [1234, 5678, 2],
      expectedQuery: (columnName) => `
        with contract as (
          ${queryForTable({table: 'contract', columnName})}
        ), contract_file as (
            ${FileDataService.getContractInitCodeFiledataQuery()}
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
      expectedParams: [
        5678,
        1234,
        Buffer.from([112, 242, 178, 145, 74, 42, 75, 120, 63, 174, 251, 117, 244, 89, 165, 128, 97, 111, 203, 94]),
      ],
      expectedQuery: (columnName) => `
        with contract as (
            ${queryForTable({table: 'contract', extraConditions: timestampConditions, columnName})}
            union
            ${queryForTable({table: 'contract_history', extraConditions: timestampConditions, columnName})}
            order by timestamp_range desc
            limit 1
        ), contract_file as (
            ${FileDataService.getContractInitCodeFiledataQuery()}
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
      expectedParams: [
        1234,
        5678,
        Buffer.from([112, 242, 178, 145, 74, 42, 75, 120, 63, 174, 251, 117, 244, 89, 165, 128, 97, 111, 203, 94]),
      ],
      expectedQuery: (columnName) => `
        with contract as (
          ${queryForTable({table: 'contract', columnName})}
        ), contract_file as (
            ${FileDataService.getContractInitCodeFiledataQuery()}
        )
        ${mainQuery}`,
    },
    {
      name: 'historical',
      isCreate2Test: false,
      input: {timestampConditions, timestampParams: [5678, 1234], contractIdParam: '0.0.1'},
      expectedParams: [5678, 1234, 1],
      expectedQuery: (columnName) => `
        with contract as (
            ${queryForTable({table: 'contract', extraConditions: timestampConditions, columnName})}
            union
            ${queryForTable({table: 'contract_history', extraConditions: timestampConditions, columnName})}
            order by timestamp_range desc
            limit 1
        ), contract_file as (
            ${FileDataService.getContractInitCodeFiledataQuery()}
        )
        ${mainQuery}`,
    },
    {
      name: 'latest',
      isCreate2Test: false,
      input: {timestampConditions: [], timestampParams: [1234, 5678], contractIdParam: '0.0.924569'},
      expectedParams: [1234, 5678, 924569],
      expectedQuery: (columnName) => `
        with contract as (
          ${queryForTable({table: 'contract', columnName})}
          ), contract_file as (
          ${FileDataService.getContractInitCodeFiledataQuery()}
          )
          ${mainQuery}`,
    },
    {
      name: 'latest',
      isCreate2Test: false,
      input: {timestampConditions: [], timestampParams: [1234, 5678], contractIdParam: '1.1.924569'},
      expectedParams: [1234, 5678, 281479272602521],
      expectedQuery: (columnName) => `
        with contract as (
          ${queryForTable({table: 'contract', columnName})}
        ), contract_file as (
            ${FileDataService.getContractInitCodeFiledataQuery()}
        )
        ${mainQuery}`,
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      const actualContract = contracts.getContractByIdOrAddressQuery(spec.input);
      const actualQuery = actualContract.query;
      const expectedQuery = spec.expectedQuery(spec.isCreate2Test ? Contract.EVM_ADDRESS : Contract.ID);
      assertSqlQueryEqual(actualQuery, expectedQuery);
      expect(actualContract.params).toEqual(spec.expectedParams);
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
  const defaultExpected = {
    conditions: [primaryContractFilter, 't.nonce = $2'],
    params: [defaultContractId, 0],
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
        conditions: [
          primaryContractFilter,
          'cr.payer_account_id > $2',
          't.nonce = $3',
          'cr.payer_account_id in ($4,$5)',
        ],
        params: [defaultContractId, '1000', 0, '1001', '1002'],
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
          't.nonce = $3',
          'cr.consensus_timestamp in ($4,$5)',
        ],
        params: [defaultContractId, '1000', 0, '1001', '1002'],
      },
    },
  ];

  specs.forEach((spec) => {
    test(`${spec.name}`, async () => {
      expect(await contracts.extractContractResultsByIdQuery(spec.input.filter, spec.input.contractId)).toEqual(
        spec.expected
      );
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

const defaultContractLogCondition = 'cl.contract_id = $1';
describe('extractContractLogsMultiUnionQuery - positive', () => {
  const defaultContractId = 1;
  const defaultExpected = {
    bounds: {
      secondary: new Bound(constants.filterKeys.INDEX),
      primary: new Bound(constants.filterKeys.TIMESTAMP),
    },
    lower: [],
    inner: [],
    upper: [],
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
      name: 'no index & timestamp =',
      input: {
        filter: [timestampEq1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            equal: timestampEq1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter],
      },
    },
    {
      name: 'no index & timestamp >',
      input: {
        filter: [timestampGt1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            lower: timestampGt1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampGt1002Filter],
      },
    },
    {
      name: 'no index & timestamp >=',
      input: {
        filter: [timestampGte1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            lower: timestampGte1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampGte1002Filter],
      },
    },
    {
      name: 'no index & timestamp <',
      input: {
        filter: [timestampLt1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            upper: timestampLt1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampLt1005Filter],
      },
    },
    {
      name: 'no index & timestamp <=',
      input: {
        filter: [timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampLte1005Filter],
      },
    },
    {
      name: 'no index & timestamp > & timestamp <',
      input: {
        filter: [timestampGt1002Filter, timestampLt1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            lower: timestampGt1002Filter,
            upper: timestampLt1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampGt1002Filter, timestampLt1005Filter],
      },
    },
    {
      name: 'no index & timestamp >= & timestamp <=',
      input: {
        filter: [timestampGte1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          ...defaultExpected.bounds,
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampGte1002Filter, timestampLte1005Filter],
      },
    },
    {
      name: 'index = & timestamp =',
      input: {
        filter: [indexEq2Filter, timestampEq1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            equal: indexEq2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            equal: timestampEq1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexEq2Filter],
      },
    },
    {
      name: 'index > & timestamp =',
      input: {
        filter: [indexGt2Filter, timestampEq1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGt2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            equal: timestampEq1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGt2Filter],
      },
    },
    {
      name: 'index >= & timestamp =',
      input: {
        filter: [indexGte2Filter, timestampEq1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGte2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            equal: timestampEq1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGte2Filter],
      },
    },
    {
      name: 'index < & timestamp =',
      input: {
        filter: [indexLt5Filter, timestampEq1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLt5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            equal: timestampEq1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexLt5Filter],
      },
    },
    {
      name: 'index <= & timestamp =',
      input: {
        filter: [indexLte5Filter, timestampEq1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLte5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            equal: timestampEq1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexLte5Filter],
      },
    },
    {
      name: 'index > & timestamp =>',
      input: {
        filter: [indexGt2Filter, timestampGte1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGt2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGt2Filter],
        inner: [timestampGt1002Filter],
      },
    },
    {
      name: 'index >= & timestamp =>',
      input: {
        filter: [indexGte2Filter, timestampGte1002Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGte2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGte2Filter],
        inner: [timestampGt1002Filter],
      },
    },
    {
      name: 'index < & timestamp <=',
      input: {
        filter: [indexLt5Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLt5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        upper: [timestampEq1005Filter, indexLt5Filter],
        inner: [timestampLt1005Filter],
      },
    },
    {
      name: 'index <= & timestamp <=',
      input: {
        filter: [indexLte5Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLte5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        upper: [timestampEq1005Filter, indexLte5Filter],
        inner: [timestampLt1005Filter],
      },
    },
    {
      name: 'index > & timestamp >= & timestamp <',
      input: {
        filter: [indexGt2Filter, timestampGte1002Filter, timestampLt1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGt2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLt1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGt2Filter],
        inner: [timestampGt1002Filter, timestampLt1005Filter],
      },
    },
    {
      name: 'index > & timestamp >= & timestamp <=',
      input: {
        filter: [indexGt2Filter, timestampGte1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGt2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGt2Filter],
        inner: [timestampGt1002Filter, timestampLte1005Filter],
      },
    },
    {
      name: 'index >= & timestamp >= & timestamp <',
      input: {
        filter: [indexGte2Filter, timestampGte1002Filter, timestampLt1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGte2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLt1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGte2Filter],
        inner: [timestampGt1002Filter, timestampLt1005Filter],
      },
    },

    {
      name: 'index >= & timestamp >= & timestamp <=',
      input: {
        filter: [indexGte2Filter, timestampGte1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGte2Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGte2Filter],
        inner: [timestampGt1002Filter, timestampLte1005Filter],
      },
    },

    {
      name: 'index < & timestamp > & timestamp <=',
      input: {
        filter: [indexLt5Filter, timestampGt1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLt5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGt1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        inner: [timestampGt1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLt5Filter],
      },
    },
    {
      name: 'index <= & timestamp > & timestamp <=',
      input: {
        filter: [indexLte5Filter, timestampGt1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLte5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGt1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        inner: [timestampGt1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLte5Filter],
      },
    },
    {
      name: 'index < & timestamp >= & timestamp <=',
      input: {
        filter: [indexLt5Filter, timestampGte1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLt5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        inner: [timestampGte1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLt5Filter],
      },
    },
    {
      name: 'index <= & timestamp >= & timestamp <=',
      input: {
        filter: [indexLte5Filter, timestampGte1002Filter, timestampLte1005Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            upper: indexLte5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        inner: [timestampGte1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLte5Filter],
      },
    },
    {
      name: 'index > & timestamp >= & timestamp <= & index <',
      input: {
        filter: [indexGt2Filter, timestampGte1002Filter, timestampLte1005Filter, indexLt5Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGt2Filter,
            upper: indexLt5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGt2Filter],
        inner: [timestampGt1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLt5Filter],
      },
    },

    {
      name: 'index > & timestamp >= & timestamp <= & index <=',
      input: {
        filter: [indexGt2Filter, timestampGte1002Filter, timestampLte1005Filter, indexLte5Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGt2Filter,
            upper: indexLte5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGt2Filter],
        inner: [timestampGt1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLte5Filter],
      },
    },
    {
      name: 'index >= & timestamp >= & timestamp <= & index <',
      input: {
        filter: [indexGte2Filter, timestampGte1002Filter, timestampLte1005Filter, indexLt5Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGte2Filter,
            upper: indexLt5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGte2Filter],
        inner: [timestampGt1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLt5Filter],
      },
    },
    {
      name: 'index >= & timestamp >= & timestamp <= & index <=',
      input: {
        filter: [indexGte2Filter, timestampGte1002Filter, timestampLte1005Filter, indexLte5Filter],
        contractId: defaultContractId,
      },
      expected: {
        ...defaultExpected,
        conditions: [defaultContractLogCondition],
        params: [defaultContractId],
        bounds: {
          secondary: Bound.create({
            lower: indexGte2Filter,
            upper: indexLte5Filter,
            filterKey: constants.filterKeys.INDEX,
            viewModelKey: constants.filterKeys.INDEX,
          }),
          primary: Bound.create({
            lower: timestampGte1002Filter,
            upper: timestampLte1005Filter,
            filterKey: constants.filterKeys.TIMESTAMP,
            viewModelKey: constants.filterKeys.TIMESTAMP,
          }),
        },
        lower: [timestampEq1002Filter, indexGte2Filter],
        inner: [timestampGt1002Filter, timestampLt1005Filter],
        upper: [timestampEq1005Filter, indexLte5Filter],
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
  specs.forEach((spec) => {
    test(`${spec.name}`, () => {
      expect(contracts.extractContractLogsMultiUnionQuery(spec.input.filter, spec.input.contractId)).toEqual(
        spec.expected
      );
    });
  });
});

describe('extractContractLogsMultiUnionQuery - negative', () => {
  const specs = [
    {
      name: 'index & no timestamp',
      input: {
        filter: [indexGt2Filter],
      },
    },
    {
      name: 'index > & timestamp <',
      input: {
        filter: [indexGt2Filter, timestampLt1005Filter],
      },
    },
    {
      name: 'index > & timestamp <=',
      input: {
        filter: [indexGt2Filter, timestampLte1005Filter],
      },
    },
    {
      name: 'index > & timestamp >',
      input: {
        filter: [indexGt2Filter, timestampGt1002Filter],
      },
    },
    {
      name: 'index >= & timestamp <',
      input: {
        filter: [indexGte2Filter, timestampLt1005Filter],
      },
    },
    {
      name: 'index >= & timestamp <=',
      input: {
        filter: [indexGte2Filter, timestampLte1005Filter],
      },
    },
    {
      name: 'index >= & timestamp >',
      input: {
        filter: [indexGte2Filter, timestampGt1002Filter],
      },
    },

    {
      name: 'index < & timestamp >',
      input: {
        filter: [indexLt5Filter, timestampGt1002Filter],
      },
    },
    {
      name: 'index < & timestamp >=',
      input: {
        filter: [indexLt5Filter, timestampGte1002Filter],
      },
    },
    {
      name: 'index < & timestamp <',
      input: {
        filter: [
          indexLt5Filter,
          {
            key: constants.filterKeys.TIMESTAMP,
            operator: utils.opsMap.lt,
            value: '1002',
          },
        ],
      },
    },
    {
      name: 'index <= & timestamp <',
      input: {
        filter: [indexLte5Filter, timestampLt1005Filter],
      },
    },
    {
      name: 'index <= & timestamp >=',
      input: {
        filter: [indexLte5Filter, timestampGte1002Filter],
      },
    },
    {
      name: 'index <= & timestamp >',
      input: {
        filter: [indexLte5Filter, timestampGt1002Filter],
      },
    },
    {
      name: 'index = & timestamp not =',
      input: {
        filter: [indexEq2Filter, timestampGt1002Filter],
      },
    },
    {
      name: 'index > & index < & timestamp <=',
      input: {
        filter: [indexGt2Filter, indexLt5Filter, timestampLte1005Filter],
      },
    },
    {
      name: 'timestamp = & timestamp =',
      input: {
        filter: [timestampEq1005Filter, timestampEq1002Filter],
      },
    },
  ];
  specs.forEach((spec) => {
    test(`error - ${spec.name}`, () => {
      expect(() => contracts.extractContractLogsMultiUnionQuery(spec.input.filter, spec.input.contractId)).toThrow();
    });
  });
});

describe('alterTimestampRangeInReq', () => {
  const inputQuery = {
    limit: '1',
    index: '10',
  };
  const expectedQuery = {...inputQuery};
  const timestamp34 = '1651061427.731522534';
  const timestamp99 = '1651061427.731522599';
  const specs = [
    {
      name: 'no timestamp',
      input: {
        req: {query: inputQuery},
      },
      expected: {query: expectedQuery},
    },
    {
      name: 'timestamp = 34',
      input: {
        req: {query: {...inputQuery, timestamp: timestamp34}},
      },
      expected: {query: {...expectedQuery, timestamp: timestamp34}},
    },
    {
      name: 'timestamp => 34 & timestamp < 34',
      input: {
        req: {query: {...inputQuery, timestamp: [`gte:${timestamp34}`, `lt:${timestamp34}`]}},
      },
      expected: {query: {...expectedQuery, timestamp: [`gte:${timestamp34}`, `lt:${timestamp34}`]}},
    },
    {
      name: 'timestamp > 34 & timestamp < 34',
      input: {
        req: {query: {...inputQuery, timestamp: [`gt:${timestamp34}`, `lt:${timestamp34}`]}},
      },
      expected: {query: {...expectedQuery, timestamp: [`gt:${timestamp34}`, `lt:${timestamp34}`]}},
    },
    {
      name: 'timestamp => 34 & timestamp <= 99',
      input: {
        req: {query: {...inputQuery, timestamp: [`gte:${timestamp34}`, `lte:${timestamp99}`]}},
      },
      expected: {query: {...expectedQuery, timestamp: [`gte:${timestamp34}`, `lte:${timestamp99}`]}},
    },
    {
      name: 'timestamp => 99 & timestamp <= 34',
      input: {
        req: {query: {...inputQuery, timestamp: [`gte:${timestamp99}`, `lte:${timestamp34}`]}},
      },
      expected: {query: {...expectedQuery, timestamp: [`gte:${timestamp99}`, `lte:${timestamp34}`]}},
    },
    {
      name: 'timestamp => 34 & timestamp <= 34',
      input: {
        req: {query: {...inputQuery, timestamp: [`gte:${timestamp34}`, `lte:${timestamp34}`]}},
      },
      expected: {query: {...expectedQuery, timestamp: timestamp34}},
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      contracts.alterTimestampRangeInReq(spec.input.req);
      expect(spec.input.req).toEqual(spec.expected);
    });
  });
});
