/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import request from 'supertest';

import * as constants from '../constants';
import server from '../server';
import * as testutils from './testutils';
import subject from '../transactions';
import * as utils from '../utils';

const {
  buildWhereClause,
  convertStakingRewardTransfers,
  createAssessedCustomFeeList,
  createCryptoTransferList,
  createNftTransferList,
  createTransferLists,
  extractSqlFromTransactionsByIdOrHashRequest,
  getStakingRewardTimestamps,
  isValidTransactionHash,
} = subject;

const timeNow = Math.floor(new Date().getTime() / 1000);
const timeOneHourAgo = timeNow - 60 * 60;

// Validation functions
/**
 * Validate length of the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function (transactions, len) {
  return transactions.length === len;
};

/**
 * Validate the range of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} low Expected low limit of the timestamps
 * @param {Number} high Expected high limit of the timestamps
 * @return {Boolean}  Result of the check
 */
const validateTsRange = function (transactions, low, high) {
  let ret = true;
  let offender = null;
  for (const tx of transactions) {
    if (tx.consensus_timestamp < low || tx.consensus_timestamp > high) {
      offender = tx;
      ret = false;
    }
  }
  if (!ret) {
    logger.warn(`validateTsRange check failed: ${offender.consensus_timestamp} is not between ${low} and  ${high}`);
  }
  return ret;
};

/**
 * Validate the range of account ids in the transactions returned by the api
 * At least one transfer in a transaction should match the expected range
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function (transactions, low, high) {
  let ret = false;
  for (const tx of transactions) {
    for (const xfer of tx.transfers) {
      const accNum = xfer.account.split('.')[2];
      if (accNum >= low && accNum <= high) {
        // if at least one transfer is valid move to next transaction
        ret = true;
        break;
      }
    }

    if (!ret) {
      logger.warn(
        `validateAccNumRange check failed: No transfer with account between ${low} and ${high} was found in transaction : ${utils.JSONStringify(
          tx
        )}`
      );
      return false;
    }

    // reset ret
    ret = false;
  }

  return true;
};

/**
 * Validate that account ids in the transactions returned by the api are in the list of valid account ids
 * At least one transfer in a transaction should be the expected list of values
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Array} list of valid account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumInArray = function (transactions, ...potentialValues) {
  for (const tx of transactions) {
    if (!testutils.validateAccNumInArray(tx.transfers, potentialValues)) {
      return false;
    }
  }
  return true;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} transactions Array of transactions returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function (transactions) {
  const errors = [];
  const transaction = transactions[0];

  // Assert that all mandatory fields are present in the response
  [
    'consensus_timestamp',
    'charged_tx_fee',
    'entity_id',
    'max_fee',
    'memo_base64',
    'name',
    'node',
    'result',
    'scheduled',
    'transaction_hash',
    'transaction_id',
    'transfers',
    'valid_duration_seconds',
    'valid_start_timestamp',
  ].forEach((field) => {
    if (!(field in transaction)) {
      errors.push(`missing field "${field}"`);
    }
  });

  if (Array.isArray(transaction.transfers)) {
    ['account', 'amount'].forEach((field) => {
      if (!(field in transaction.transfers[0])) {
        errors.push(`missing field "${field}" in transfers[0]`);
      }
    });
  } else {
    errors.push('field "transfers" is not an array');
  }

  if (errors.length !== 0) {
    logger.warn(`validateFields check failed: ${errors.join(',\n\t')}`);
  }

  return errors.length === 0;
};

/**
 * Validate the order of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function (transactions, order) {
  let ret = true;
  let offenderTx = null;
  let offenderVal = null;
  const direction = order === 'desc' ? -1 : 1;
  let val = transactions[0].consensus_timestamp - direction;
  for (const tx of transactions) {
    if (val * direction > tx.consensus_timestamp * direction) {
      offenderTx = tx;
      offenderVal = val;
      ret = false;
    }
    val = tx.consensus_timestamp;
  }
  if (!ret) {
    logger.warn(
      `validateOrder check failed: ${offenderTx.consensus_timestamp} - previous timestamp ${offenderVal} Order  ${order}`
    );
  }
  return ret;
};

/**
 * This is the list of individual tests. Each test validates one query parameter
 * such as timestamp=1234 or account.id=gt:5678.
 * Definition of each test consists of the url string that is used in the query, and an
 * array of checks to be performed on the resultant SQL query.
 * These individual tests can be combined to form complex combinations as shown in the
 * definition of combinedTests below.
 * NOTE: To add more tests, just give it a unique name, specify the url query string, and
 * a set of checks you would like to perform on the resultant SQL query.
 */
const singleTests = {
  timestamp_lowerlimit: {
    urlparam: `timestamp=gte:${timeOneHourAgo}`,
    checks: [{field: 'consensus_timestamp', operator: '>=', value: `${timeOneHourAgo}000000000`}],
    checkFunctions: [
      {func: validateTsRange, args: [timeOneHourAgo, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  timestamp_higherlimit: {
    urlparam: `timestamp=lt:${timeNow}`,
    checks: [{field: 'consensus_timestamp', operator: '<', value: `${timeNow}000000000`}],
    checkFunctions: [
      {func: validateTsRange, args: [0, timeNow]},
      {func: validateFields, args: []},
    ],
  },
  accountid_lowerlimit: {
    urlparam: 'account.id=gte:0.0.1111',
    checks: [{field: 'entity_id', operator: '>=', value: 1111}],
    checkFunctions: [
      {func: validateAccNumRange, args: [1111, Number.MAX_SAFE_INTEGER]},
      {func: validateFields, args: []},
    ],
  },
  accountid_higherlimit: {
    urlparam: 'account.id=lt:0.0.2222',
    checks: [{field: 'entity_id', operator: '<', value: 2222}],
    checkFunctions: [
      {func: validateAccNumRange, args: [0, 2222]},
      {func: validateFields, args: []},
    ],
  },
  accountid_equal: {
    urlparam: 'account.id=0.0.3333',
    checks: [{field: 'entity_id', operator: 'in', value: 3333}],
    checkFunctions: [{func: validateAccNumInArray, args: [3333]}],
  },
  accountid_multiple: {
    urlparam: 'account.id=0.0.3333&account.id=0.0.3334',
    checks: [
      {field: 'entity_id', operator: 'in', value: 3333},
      {field: 'entity_id', operator: 'in', value: 3334},
    ],
    checkFunctions: [{func: validateAccNumInArray, args: [3333, 3334]}],
  },
  limit: {
    urlparam: 'limit=99',
    checks: [{field: 'limit', operator: '=', value: 99}],
    checkFunctions: [
      {func: validateLen, args: [99]},
      {func: validateFields, args: []},
    ],
  },
  order_asc: {
    urlparam: 'order=asc',
    checks: [{field: 'order', operator: '=', value: 'asc'}],
    checkFunctions: [{func: validateOrder, args: ['asc']}],
  },
  order_desc: {
    urlparam: 'order=desc',
    checks: [{field: 'order', operator: '=', value: 'desc'}],
    checkFunctions: [{func: validateOrder, args: ['desc']}],
  },
  result_fail: {
    urlparam: 'result=fail',
    checks: [{field: 'result', operator: '!=', value: `${utils.resultSuccess}`}],
  },
  result_success: {
    urlparam: 'result=success',
    checks: [{field: 'result', operator: '=', value: `${utils.resultSuccess}`}],
  },
};

/**
 * This list allows creation of combinations of individual tests to exercise presence
 * of multiple query parameters. The combined query string is created by adding the query
 * strings of each of the individual tests, and all checks from all of the individual tests
 * are performed on the resultant SQL query
 * NOTE: To add more combined tests, just add an entry to following array using the
 * individual (single) tests in the object above.
 */
const combinedTests = [
  ['timestamp_lowerlimit', 'timestamp_higherlimit'],
  ['accountid_lowerlimit', 'accountid_higherlimit'],
  ['timestamp_lowerlimit', 'timestamp_higherlimit', 'accountid-lowerlimit', 'accountid_higherlimit'],
  ['timestamp_lowerlimit', 'accountid_higherlimit', 'limit'],
  ['timestamp_higherlimit', 'accountid_lowerlimit', 'result_fail'],
  ['limit', 'result_success', 'order_asc'],
];

// Start of tests
describe('Transaction tests', () => {
  const api = '/api/v1/transactions';

  // First, execute the single tests
  for (const [name, item] of Object.entries(singleTests)) {
    test(`Transactions single test: ${name} - URL: ${item.urlparam}`, async () => {
      const response = await request(server).get([api, item.urlparam].join('?'));

      expect(response.status).toEqual(200);
      const {transactions} = JSON.parse(response.text);
      const parsedParams = JSON.parse(response.text).sqlQuery.parsedparams;

      // Verify the sql query against each of the specified checks
      expect(parsedParams).toEqual(expect.arrayContaining(item.checks));

      // Execute the specified functions to validate the output from the REST API
      let check = true;
      if (item.hasOwnProperty('checkFunctions')) {
        for (const cf of item.checkFunctions) {
          check = check && cf.func.apply(null, [transactions].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // And now, execute the combined tests
  for (const combination of combinedTests) {
    // Combine the individual (single) checks as specified in the combinedTests array
    const combtest = {urls: [], checks: [], checkFunctions: [], names: ''};
    for (const testname of combination) {
      if (testname in singleTests) {
        combtest.names += `${testname} `;
        combtest.urls.push(singleTests[testname].urlparam);
        combtest.checks = combtest.checks.concat(singleTests[testname].checks);
        combtest.checkFunctions = combtest.checkFunctions.concat(
          singleTests[testname].hasOwnProperty('checkFunctions') ? singleTests[testname].checkFunctions : []
        );
      }
    }
    const comburl = combtest.urls.join('&');

    test(`Transactions combination test: ${combtest.names} - URL: ${comburl}`, async () => {
      const response = await request(server).get([api, comburl].join('?'));
      expect(response.status).toEqual(200);
      const parsedParams = JSON.parse(response.text).sqlQuery.parsedparams;
      const {transactions} = JSON.parse(response.text);

      // Verify the sql query against each of the specified checks
      expect(parsedParams).toEqual(expect.arrayContaining(combtest.checks));

      // Execute the specified functions to validate the output from the REST API
      let check = true;
      if (combtest.hasOwnProperty('checkFunctions')) {
        for (const cf of combtest.checkFunctions) {
          check = check && cf.func.apply(null, [transactions].concat(cf.args));
        }
      }
      expect(check).toBeTruthy();
    });
  }

  // Negative testing
  testutils.testBadParams(request, server, api, 'timestamp', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'account.id', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'limit', testutils.badParamsList());
  testutils.testBadParams(request, server, api, 'order', testutils.badParamsList());
});

describe('buildWhereClause', () => {
  const testSpecs = [
    {
      conditions: [],
      expected: '',
    },
    {
      conditions: [''],
      expected: '',
    },
    {
      conditions: ['a>?'],
      expected: 'where a>?',
    },
    {
      conditions: ['a>?', 'b<?', 'c<>?'],
      expected: 'where a>? and b<? and c<>?',
    },
    {
      conditions: ['a>?', '', 'b<?', 'c<>?', ''],
      expected: 'where a>? and b<? and c<>?',
    },
  ];

  testSpecs.forEach((testSpec) => {
    const {conditions, expected} = testSpec;
    test(utils.JSONStringify(conditions), () => {
      const whereClause = buildWhereClause(...conditions);
      expect(whereClause.toLowerCase()).toEqual(expected);
    });
  });
});

describe('createAssessedCustomFeeList', () => {
  test('From null', () => {
    expect(createAssessedCustomFeeList(null)).toEqual(undefined);
  });

  test('From undefined', () => {
    expect(createAssessedCustomFeeList(undefined)).toEqual(undefined);
  });

  test('Simple createAssessedCustomFeeList', () => {
    const rowsFromDb = [
      {
        amount: 8,
        collector_account_id: 9901,
        effective_payer_account_ids: [],
        token_id: null,
      },
      {
        amount: 9,
        collector_account_id: 9901,
        effective_payer_account_ids: [7000],
        token_id: 10001,
      },
      {
        amount: 29,
        collector_account_id: 9902,
        effective_payer_account_ids: [7000, 7001],
        token_id: 10002,
      },
    ];
    const expected = [
      {
        amount: 8,
        collector_account_id: '0.0.9901',
        effective_payer_account_ids: [],
        token_id: null,
      },
      {
        amount: 9,
        collector_account_id: '0.0.9901',
        effective_payer_account_ids: ['0.0.7000'],
        token_id: '0.0.10001',
      },
      {
        amount: 29,
        collector_account_id: '0.0.9902',
        effective_payer_account_ids: ['0.0.7000', '0.0.7001'],
        token_id: '0.0.10002',
      },
    ];

    expect(createAssessedCustomFeeList(rowsFromDb)).toEqual(expected);
  });
});

describe('createCryptoTransferList', () => {
  test('From null', () => {
    expect(createCryptoTransferList(null)).toEqual([]);
  });

  test('From undefined', () => {
    expect(createCryptoTransferList(undefined)).toEqual([]);
  });

  test('Simple createCryptoTransferList', () => {
    const rowsFromDb = [
      {
        amount: 8,
        entity_id: 3,
        is_approval: null,
      },
      {
        amount: -27,
        entity_id: 9001,
        is_approval: true,
      },
      {
        amount: 19,
        entity_id: 98,
        is_approval: true,
      },
    ];
    const expected = [
      {
        account: '0.0.3',
        amount: 8,
        is_approval: false,
      },
      {
        account: '0.0.9001',
        amount: -27,
        is_approval: true,
      },
      {
        account: '0.0.98',
        amount: 19,
        is_approval: true,
      },
    ];

    expect(createCryptoTransferList(rowsFromDb)).toEqual(expected);
  });
});

describe('createNftTransferList', () => {
  test('From null', () => {
    expect(createNftTransferList(null)).toEqual([]);
  });

  test('From undefined', () => {
    expect(createNftTransferList(undefined)).toEqual([]);
  });

  test('Simple createNftTransferList', () => {
    const rowsFromDb = [
      {
        consensus_timestamp: 1,
        receiver_account_id: 1000,
        sender_account_id: 98,
        serial_number: 1,
        token_id: 2000,
        is_approval: null,
      },
      {
        consensus_timestamp: 10,
        receiver_account_id: 1005,
        sender_account_id: 98,
        serial_number: 2,
        token_id: 2000,
        is_approval: false,
      },
      {
        consensus_timestamp: 100,
        receiver_account_id: 98,
        sender_account_id: 1005,
        serial_number: 2,
        token_id: 2000,
        is_approval: true,
      },
    ];

    const expectedFormat = [
      {
        receiver_account_id: '0.0.1000',
        sender_account_id: '0.0.98',
        serial_number: 1,
        token_id: '0.0.2000',
        is_approval: false,
      },
      {
        receiver_account_id: '0.0.1005',
        sender_account_id: '0.0.98',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: false,
      },
      {
        receiver_account_id: '0.0.98',
        sender_account_id: '0.0.1005',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: true,
      },
    ];

    expect(createNftTransferList(rowsFromDb)).toEqual(expectedFormat);
  });
});

describe('create transferLists', () => {
  test('Simple nftTransferList', async () => {
    const nftTransfersFromDb = [
      {
        consensus_timestamp: 1,
        receiver_account_id: 1000,
        sender_account_id: 98,
        serial_number: 1,
        token_id: 2000,
        is_approval: null,
      },
      {
        consensus_timestamp: 10,
        receiver_account_id: 1005,
        sender_account_id: 98,
        serial_number: 2,
        token_id: 2000,
        is_approval: true,
      },
      {
        consensus_timestamp: 100,
        receiver_account_id: 98,
        sender_account_id: 1005,
        serial_number: 2,
        token_id: 2000,
        is_approval: true,
      },
    ];

    const transactionsFromDb = [
      {
        consensus_timestamp: 1,
        entity_id: 98,
        memo: null,
        charged_tx_fee: 5,
        max_fee: 33,
        non_fee_transfers: [],
        nonce: 0,
        parent_consensus_timestamp: null,
        token_transfers: [],
        transfers: [],
        result: 22,
        scheduled: false,
        transaction_hash: 'hash',
        type: 14,
        valid_start_ns: 1623787159737799966n,
        transaction_bytes: 'bytes',
        node_account_id: 2,
        payer_account_id: 3,
        crypto_transfer_list: [{amount: 100, entity_id: 98, is_approval: true}],
        nft_transfer: nftTransfersFromDb,
      },
      {
        consensus_timestamp: 2,
        entity_id: 100,
        memo: null,
        charged_tx_fee: 5,
        max_fee: 33,
        non_fee_transfers: [],
        nonce: 1,
        parent_consensus_timestamp: 1,
        token_transfers: [],
        transfers: [],
        result: 22,
        scheduled: false,
        transaction_hash: 'hash',
        type: 14,
        valid_start_ns: 1623787159737799966n,
        transaction_bytes: 'bytes',
        node_account_id: 2,
        payer_account_id: 3,
        crypto_transfer_list: [{amount: 100, entity_id: 100, is_approval: true}],
        nft_transfer: [],
      },
    ];

    const expectedNftTransfersList = [
      {
        receiver_account_id: '0.0.1000',
        sender_account_id: '0.0.98',
        serial_number: 1,
        token_id: '0.0.2000',
        is_approval: false,
      },
      {
        receiver_account_id: '0.0.1005',
        sender_account_id: '0.0.98',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: true,
      },
      {
        receiver_account_id: '0.0.98',
        sender_account_id: '0.0.1005',
        serial_number: 2,
        token_id: '0.0.2000',
        is_approval: true,
      },
    ];

    const expectedFormat = [
      {
        assessed_custom_fees: undefined,
        bytes: 'bytes',
        consensus_timestamp: '0.000000001',
        charged_tx_fee: 5,
        entity_id: '0.0.98',
        max_fee: '33',
        memo_base64: null,
        name: 'CRYPTOTRANSFER',
        nft_transfers: expectedNftTransfersList,
        node: '0.0.2',
        nonce: 0,
        parent_consensus_timestamp: null,
        result: 'SUCCESS',
        scheduled: false,
        staking_reward_transfers: [],
        token_transfers: [],
        transaction_hash: 'hash',
        transaction_id: '0.0.3-1623787159-737799966',
        transfers: [
          {
            account: '0.0.98',
            amount: 100,
            is_approval: true,
          },
        ],
        valid_duration_seconds: null,
        valid_start_timestamp: '1623787159.737799966',
      },
      {
        assessed_custom_fees: undefined,
        bytes: 'bytes',
        consensus_timestamp: '0.000000002',
        charged_tx_fee: 5,
        entity_id: '0.0.100',
        max_fee: '33',
        memo_base64: null,
        name: 'CRYPTOTRANSFER',
        nft_transfers: [],
        node: '0.0.2',
        nonce: 1,
        parent_consensus_timestamp: '0.000000001',
        result: 'SUCCESS',
        scheduled: false,
        staking_reward_transfers: [],
        token_transfers: [],
        transaction_hash: 'hash',
        transaction_id: '0.0.3-1623787159-737799966',
        transfers: [
          {
            account: '0.0.100',
            amount: 100,
            is_approval: true,
          },
        ],
        valid_duration_seconds: null,
        valid_start_timestamp: '1623787159.737799966',
      },
    ];
    expect((await createTransferLists(transactionsFromDb)).transactions).toEqual(expectedFormat);
  });
});

describe('extractSqlFromTransactionsByIdOrHashRequest', () => {
  describe('success', () => {
    const defaultTransactionIdStr = '0.0.200-123456789-987654321';
    const defaultParams = [200, '123456789987654321', 123458889987654321n];

    const getTransactionIdQuery = (extraConditions) => `
    select
      t.charged_tx_fee,
      t.consensus_timestamp,
      t.entity_id,
      t.max_fee,
      t.memo,
      t.nft_transfer,
      t.node_account_id,
      t.nonce,
      t.parent_consensus_timestamp,
      t.payer_account_id,
      t.result,
      t.scheduled,
      t.transaction_bytes,
      t.transaction_hash,
      t.type,
      t.valid_duration_seconds,
      t.valid_start_ns,
      t.index,
      (
          select jsonb_agg(jsonb_build_object('amount', amount, 'entity_id', ctr.entity_id, 'is_approval', is_approval) order by ctr.entity_id, amount)
          from crypto_transfer ctr
          where consensus_timestamp = t.consensus_timestamp and payer_account_id = $1 and consensus_timestamp >= $2 and consensus_timestamp <= $3
      ) as crypto_transfer_list,
      (
          select jsonb_agg(
              jsonb_build_object('account_id', account_id, 'amount', amount, 'token_id', token_id, 'is_approval', is_approval)
              order by token_id, account_id)
          from token_transfer tk_tr
          where consensus_timestamp = t.consensus_timestamp and payer_account_id = $1 and consensus_timestamp >= $2 and consensus_timestamp <= $3
      ) as token_transfer_list,
      (
          select jsonb_agg(
              jsonb_build_object(
                  'amount', amount, 'collector_account_id', collector_account_id,
                  'effective_payer_account_ids', effective_payer_account_ids,
                  'token_id', token_id) order by collector_account_id, amount)
          from assessed_custom_fee acf
          where consensus_timestamp = t.consensus_timestamp and payer_account_id = $1 and consensus_timestamp >= $2 and consensus_timestamp <= $3
      ) as assessed_custom_fees
    from transaction t
    where payer_account_id = $1 and consensus_timestamp >= $2 and consensus_timestamp <= $3 and valid_start_ns = $2
      ${(extraConditions && 'and ' + extraConditions) || ''}
    order by consensus_timestamp`;

    const testSpecs = [
      {
        name: 'emptyFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [],
        },
        expected: {
          query: getTransactionIdQuery(),
          params: defaultParams,
        },
      },
      {
        name: 'nonceFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [{key: constants.filterKeys.NONCE, op: 'eq', value: 1}],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $4'),
          params: [...defaultParams, 1],
        },
      },
      {
        name: 'repeatedNonceFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 2},
          ],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $4'),
          params: [...defaultParams, 2],
        },
      },
      {
        name: 'scheduledFilter',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [{key: constants.filterKeys.SCHEDULED, op: 'eq', value: true}],
        },
        expected: {
          query: getTransactionIdQuery('scheduled = $4'),
          params: [...defaultParams, true],
        },
      },
      {
        name: 'repeatedScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
          ],
        },
        expected: {
          query: getTransactionIdQuery('scheduled = $4'),
          params: [...defaultParams, false],
        },
      },
      {
        name: 'nonceAndScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
          ],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $4 and scheduled = $5'),
          params: [...defaultParams, 1, true],
        },
      },
      {
        name: 'repeatedNonceAndScheduledFilters',
        input: {
          transactionIdOrHash: defaultTransactionIdStr,
          filters: [
            {key: constants.filterKeys.NONCE, op: 'eq', value: 1},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: true},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 2},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
            {key: constants.filterKeys.NONCE, op: 'eq', value: 3},
            {key: constants.filterKeys.SCHEDULED, op: 'eq', value: false},
          ],
        },
        expected: {
          query: getTransactionIdQuery('nonce = $4 and scheduled = $5'),
          params: [...defaultParams, 3, false],
        },
      },
    ];

    for (const testSpec of testSpecs) {
      test(testSpec.name, async () => {
        const actual = await extractSqlFromTransactionsByIdOrHashRequest(
          testSpec.input.transactionIdOrHash,
          testSpec.input.filters
        );

        testutils.assertSqlQueryEqual(actual.query, testSpec.expected.query);
        expect(actual.params).toStrictEqual(testSpec.expected.params);
      });
    }
  });

  describe('invalidTransactionIdOrHash', () => {
    [
      '0.1.x-1235234-5334',
      'izUDXqZ8gOhKlL5vbFInnw2VObTXzNWEH2QOg7XOUQwl9Mp2SVil8lufZIU6xJEE====',
      '0xab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849',
      'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849',
    ].forEach((transactionIdOrHash) => {
      test(transactionIdOrHash, async () => {
        await expect(
          extractSqlFromTransactionsByIdOrHashRequest(transactionIdOrHash, [])
        ).rejects.toThrowErrorMatchingSnapshot();
      });
    });
  });
});

describe('isValidTransactionHash', () => {
  describe('base64', () => {
    describe('valid', () => {
      [
        'rovr8cn6DzCTVuSAV/YEevfN5jA30FCdFt3Dsg4IUVi/3xTRU0XBsYsZm3L+1Kxv',
        'rovr8cn6DzCTVuSAV_YEevfN5jA30FCdFt3Dsg4IUVi_3xTRU0XBsYsZm3L-1Kxv',
      ].forEach((hash) =>
        test(`'${hash}'`, () => {
          expect(isValidTransactionHash(hash)).toBeTrue();
        })
      );
    });

    describe('invalid', () => {
      [
        'rovr8cn6DzCTVuSAV/YEevfN5jA30FCdFt3Dsg4IUVi/3xTRU0XBsYsZm3L+1===',
        'q0r3hK5pyj4dF/74SR9/iaDoo7gK03SLhBEQ8DRa2lNFa8FLvp7m9EGCnChJ',
        'q0r3hK5pyj4dF/74SR9/iaDoo7gK03SLhBEQ8DRa2lNFa8FLvp7m9EGCnChJkzrEaaaa',
        'q0r3hK5pyj4dF/74SR9/iaDoo7gK03SLhBEQ8DRa2lNFa8FLvp7m9EGCnChJ????',
      ].forEach((hash) =>
        test(`'${hash}`, () => {
          expect(isValidTransactionHash(hash)).toBeFalse();
        })
      );
    });
  });

  describe('hex', () => {
    describe('valid', () => {
      [
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933ac4',
        'AB4AF784AE69CA3E1D17FEF8491F7F89A0E8A3B80AD3748B841110F0345ADA53456BC14BBE9EE6F441829C2849933AC4',
        '0xab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933ac4',
      ].forEach((hash) =>
        test(`'${hash}'`, () => {
          expect(isValidTransactionHash(hash)).toBeTrue();
        })
      );
    });

    describe('invalid', () => {
      [
        null,
        undefined,
        '',
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933ac4beef',
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c284993====',
        'ab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c28',
        '0xab4af784ae69ca3e1d17fef8491f7f89a0e8a3b80ad3748b841110f0345ada53456bc14bbe9ee6f441829c2849933a',
      ].forEach((hash) =>
        test(`'${hash}'`, () => {
          expect(isValidTransactionHash(hash)).toBeFalse();
        })
      );
    });
  });
});

describe('convert staking reward transfers', () => {
  test('empty staking reward transfer', () => {
    const rows = [];
    const stakingRewardMap = convertStakingRewardTransfers(rows);
    expect(stakingRewardMap.get(1)).toEqual(undefined);
  });

  test('staking reward transfer', () => {
    const rows = [
      {
        consensus_timestamp: 1,
        staking_reward_transfers: [
          {account: 98, amount: 500},
          {account: 99, amount: 600},
        ],
      },
      {
        consensus_timestamp: 2,
        staking_reward_transfers: [{account: 100, amount: 0}],
      },
      {
        consensus_timestamp: 3,
        staking_reward_transfers: [],
      },
    ];

    const expected1 = [
      {
        account: '0.0.98',
        amount: 500,
      },
      {
        account: '0.0.99',
        amount: 600,
      },
    ];
    const expected2 = [
      {
        account: '0.0.100',
        amount: 0,
      },
    ];
    const expected3 = [];

    const stakingRewardMap = convertStakingRewardTransfers(rows);
    expect(stakingRewardMap.get(1)).toEqual(expected1);
    expect(stakingRewardMap.get(2)).toEqual(expected2);
    expect(stakingRewardMap.get(3)).toEqual(expected3);
  });
});

describe('getStakingRewardTimestamps', () => {
  [
    [
      {
        consensus_timestamp: 1565779604000000002,
        crypto_transfer_list: [
          {
            entity_id: 801,
          },
        ],
      },
    ],
    [
      {
        crypto_transfer_list: [
          {
            entity_id: 800,
          },
        ],
      },
    ],
    [],
  ].forEach((transactions) => {
    test(`'${transactions}'`, () => {
      expect(getStakingRewardTimestamps(transactions)).toEqual([]);
    });
  });

  test('get staking timestamps', () => {
    const transactions = [
      {
        consensus_timestamp: 1565779604000000002,
        crypto_transfer_list: [
          {
            entity_id: 800,
          },
        ],
      },
      {
        consensus_timestamp: 1565779602000000002,
        crypto_transfer_list: [
          {
            entity_id: 98,
          },
        ],
      },
      {
        consensus_timestamp: 1565779600000000002,
        crypto_transfer_list: [
          {
            entity_id: 800,
          },
        ],
      },
    ];

    const expected = [1565779604000000002, 1565779600000000002];
    expect(getStakingRewardTimestamps(transactions)).toEqual(expected);
  });
});
