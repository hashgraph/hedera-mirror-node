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

const utils = require('../utils.js');
const config = require('../config.js');
const constants = require('../constants.js');
const {InvalidArgumentError} = require('../errors/invalidArgumentError');
const {InvalidClauseError} = require('../errors/invalidClauseError');
const {TransactionType} = require('../model');
const {getLimitParamValue} = require('../utils');
const {keyTypes} = require('../constants');

const ecdsaKey = '02b5ffadf88d625cd9074fa01e5280b773a60ed2de55b0d6f94460c0b5a001a258';
const ed25519Key = '7a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc';
const ed25519Der = `302a300506032b6570032100${ed25519Key}`;
const responseLimit = config.response.limit;

describe('Utils getNullableNumber tests', () => {
  test('Verify getNullableNumber returns correct result for 0', () => {
    const val = utils.getNullableNumber(0);
    expect(val).toBe('0');
  });

  test('Verify getNullableNumber returns correct result for null', () => {
    const val = utils.getNullableNumber(null);
    expect(val).toBe(null);
  });

  test('Verify getNullableNumber returns correct result for undefined', () => {
    const val = utils.getNullableNumber(undefined);
    expect(val).toBe(null);
  });

  test('Verify getNullableNumber returns correct result for valid number', () => {
    const validNumber = 10;
    const val = utils.getNullableNumber(validNumber);
    expect(val).toBe(validNumber.toString());
  });
});

describe('Utils mergeParams tests', () => {
  test('one params array', () => {
    expect(utils.mergeParams([1, 2])).toEqual([1, 2]);
  });

  test('two params arrays', () => {
    expect(utils.mergeParams([1, 2], ['a', 'b'])).toEqual([1, 2, 'a', 'b']);
  });

  test('with initial []', () => {
    const params1 = [1, 2];
    const params2 = ['a', 'b'];
    expect(utils.mergeParams([], params1, params2)).toEqual([1, 2, 'a', 'b']);
    expect(params1).toEqual([1, 2]); // assert params1 isn't changed
    expect(params2).toEqual(['a', 'b']); // assert param2 isn't changed
  });
});

describe('Utils nsToSecNs tests', () => {
  const validStartNs = '9223372036854775837';
  test('Verify nsToSecNs returns correct result for valid validStartNs', () => {
    const val = utils.nsToSecNs(validStartNs);
    expect(val).toBe('9223372036.854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 validStartNs', () => {
    const val = utils.nsToSecNs(0);
    expect(val).toBe('0.000000000');
  });

  test('Verify nsToSecNs returns correct result for null validStartNs', () => {
    const val = utils.nsToSecNs(null);
    expect(val).toBe(null);
  });

  test('Verify nsToSecNsWithHyphen returns correct result for valid validStartNs', () => {
    const val = utils.nsToSecNsWithHyphen(validStartNs);
    expect(val).toBe('9223372036-854775837');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for 0 validStartNs', () => {
    const val = utils.nsToSecNsWithHyphen(0);
    expect(val).toBe('0-000000000');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for null validStartNs', () => {
    const val = utils.nsToSecNsWithHyphen(null);
    expect(val).toBe(null);
  });
});

describe('Utils createTransactionId tests', () => {
  test('Verify createTransactionId returns correct result for valid inputs', () => {
    expect(utils.createTransactionId('1.2.995', '9223372036854775837')).toEqual('1.2.995-9223372036-854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 inputs', () => {
    expect(utils.createTransactionId('0.0.0', 0)).toEqual('0.0.0-0-000000000');
  });
});

describe('Utils encodeKey', () => {
  test('Null', () => expect(utils.encodeKey(null)).toBe(null));
  [
    {
      name: 'Empty',
      input: '',
      output: '',
      signatureType: keyTypes.PROTOBUF,
    },
    {
      name: 'Protobuf',
      input: 'abcdef',
      output: 'abcdef',
      signatureType: keyTypes.PROTOBUF,
    },
    {
      name: 'ECDSA(secp256k1) primitive',
      input: `3a20${ecdsaKey}`,
      output: ecdsaKey,
      signatureType: keyTypes.ECDSA_SECP256K1,
    },
    {
      name: 'ECDSA(secp256k1) keylist',
      input: `32240a223a20${ecdsaKey}`,
      output: ecdsaKey,
      signatureType: keyTypes.ECDSA_SECP256K1,
    },
    {
      name: 'ECDSA(secp256k1) threshold',
      input: `2a28080112240a223a20${ecdsaKey}`,
      output: ecdsaKey,
      signatureType: keyTypes.ECDSA_SECP256K1,
    },
    {
      name: 'ED25519 primitive',
      input: `1220${ed25519Key}`,
      output: ed25519Key,
      signatureType: keyTypes.ED25519,
    },
    {
      name: 'ED25519 keylist',
      input: `32240a221220${ed25519Key}`,
      output: ed25519Key,
      signatureType: keyTypes.ED25519,
    },
    {
      name: 'ED25519 threshold',
      input: `2a28080112240a221220${ed25519Key}`,
      output: ed25519Key,
      signatureType: keyTypes.ED25519,
    },
  ].forEach((spec) => {
    const buffer = Buffer.from(spec.input, 'hex');
    const bytes = [...buffer];
    test(spec.name, () => expect(utils.encodeKey(bytes)).toStrictEqual({_type: spec.signatureType, key: spec.output}));
  });
});

describe('Utils isValidPublicKeyQuery', () => {
  test('Null', () => expect(utils.isValidPublicKeyQuery(null)).toBe(false));
  test('Empty', () => expect(utils.isValidPublicKeyQuery('')).toBe(false));
  test('Valid ECDSA(secp256k1)', () => expect(utils.isValidPublicKeyQuery(ecdsaKey)).toBe(true));
  test('Valid ED25519', () => expect(utils.isValidPublicKeyQuery(ed25519Key)).toBe(true));
  test('Valid ED25519 DER', () => expect(utils.isValidPublicKeyQuery(ed25519Der)).toBe(true));
  test('0x ECDSA', () => expect(utils.isValidPublicKeyQuery(`0x${ed25519Key}`)).toBe(true));
  test('0x ED25519', () => expect(utils.isValidPublicKeyQuery(`0x${ecdsaKey}`)).toBe(true));
  test('Invalid', () => expect(utils.isValidPublicKeyQuery(`${ed25519Key}F`)).toBe(false));
});

describe('Utils isValidTimestampParam tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidTimestampParam(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidTimestampParam('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidTimestampParam('0.0.1')).toBe(false);
  });
  test('Verify invalid for invalid seconds', () => {
    expect(utils.isValidTimestampParam('12345678901')).toBe(false);
  });
  test('Verify invalid for invalid nanoseconds', () => {
    expect(utils.isValidTimestampParam('1234567890.0000000012')).toBe(false);
  });
  test('Verify valid for seconds only', () => {
    expect(utils.isValidTimestampParam('1234567890')).toBe(true);
  });
  test('Verify valid for seconds and nanoseconds', () => {
    expect(utils.isValidTimestampParam('1234567890.000000001')).toBe(true);
  });
});

describe('Utils parseLimitAndOrderParams tests', () => {
  const defaultResult = {
    query: 'limit ? ',
    params: [responseLimit.default],
    order: constants.orderFilterValues.DESC,
    limit: responseLimit.default,
  };

  test('no query params', () => {
    expect(utils.parseLimitAndOrderParams({query: {}})).toEqual(defaultResult);
  });

  test('default order asc', () => {
    expect(utils.parseLimitAndOrderParams({query: {}}, constants.orderFilterValues.ASC)).toEqual({
      ...defaultResult,
      order: constants.orderFilterValues.ASC,
    });
  });

  test('both limit and order params', () => {
    const query = {
      limit: '20',
      order: constants.orderFilterValues.ASC,
    };
    expect(utils.parseLimitAndOrderParams({query})).toEqual({
      ...defaultResult,
      params: [20],
      order: constants.orderFilterValues.ASC,
      limit: 20,
    });
  });

  test('limit capped at max', () => {
    const query = {
      limit: `${responseLimit.max + 1}`,
    };
    expect(utils.parseLimitAndOrderParams({query})).toEqual({
      ...defaultResult,
      params: [responseLimit.max],
      limit: responseLimit.max,
    });
  });

  test('limit array', () => {
    const query = {
      limit: [1, 15],
    };
    expect(utils.parseLimitAndOrderParams({query})).toEqual({
      ...defaultResult,
      params: [15],
      limit: 15,
    });
  });
});

describe('Utils parseTimestampParam tests', () => {
  test('Verify empty response for null', () => {
    expect(utils.parseTimestampParam(null)).toBe('');
  });
  test('Verify empty response for empty input', () => {
    expect(utils.parseTimestampParam('')).toBe('');
  });
  test('Verify empty response for invalid input', () => {
    expect(utils.parseTimestampParam('0.0.1')).toBe('');
  });
  test('Verify valid response for seconds only', () => {
    expect(utils.parseTimestampParam('1234567890')).toBe('1234567890000000000');
  });
  test('Verify valid response for seconds and nanoseconds', () => {
    expect(utils.parseTimestampParam('1234567890.000000001')).toBe('1234567890000000001');
  });
});

describe('Utils isNonNegativeInt32', () => {
  describe('true', () => {
    const values = ['0', '1', '2147483647'];
    for (const value of values) {
      test(value, () => {
        expect(utils.isNonNegativeInt32(value)).toBe(true);
      });
    }
  });

  describe('false', () => {
    const values = ['a', '-1', '1.1', '2147483648'];
    for (const value of values) {
      test(value, () => {
        expect(utils.isNonNegativeInt32(value)).toBe(false);
      });
    }
  });
});

describe('Utils isPositiveLong', () => {
  test('Verify invalid for null', () => {
    expect(utils.isPositiveLong(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isPositiveLong('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isPositiveLong('1234567890.000000001')).toBe(false);
  });
  test('Verify invalid for entity format shard', () => {
    expect(utils.isPositiveLong('1.0.1')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(utils.isPositiveLong(-1)).toBe(false);
  });
  test('Verify invalid for 0', () => {
    expect(utils.isPositiveLong(0)).toBe(false);
  });
  test(`Verify invalid for unsigned long 9223372036854775808`, () => {
    expect(utils.isPositiveLong('9223372036854775808')).toBe(false);
  });
  test('Verify invalid for 0 with allowZero=true', () => {
    expect(utils.isPositiveLong(0, true)).toBe(true);
  });
  test('Verify valid for valid number string', () => {
    expect(utils.isPositiveLong('123')).toBe(true);
  });
  test('Verify valid for valid number', () => {
    expect(utils.isPositiveLong(123)).toBe(true);
  });
  test(`Verify valid for max unsigned long: 9223372036854775807`, () => {
    expect(utils.isPositiveLong('9223372036854775807')).toBe(true);
  });
});

describe('Utils isValidValueIgnoreCase tokenTypeFilter tests', () => {
  const tokenTypeObjectValues = Object.values(constants.tokenTypeFilter);

  test('Verify invalid for empty input', () => {
    expect(utils.isValidValueIgnoreCase('', tokenTypeObjectValues)).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidValueIgnoreCase('1234567890.000000001', tokenTypeObjectValues)).toBe(false);
  });
  test('Verify invalid for entity format shard', () => {
    expect(utils.isValidValueIgnoreCase('1.0.1', tokenTypeObjectValues)).toBe(false);
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.ALL}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.ALL, tokenTypeObjectValues)).toBe(true);
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.FUNGIBLE_COMMON}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.FUNGIBLE_COMMON, tokenTypeObjectValues)).toBe(true);
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE, tokenTypeObjectValues)).toBe(
      true
    );
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.ALL.toUpperCase()}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.ALL.toUpperCase(), tokenTypeObjectValues)).toBe(true);
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.FUNGIBLE_COMMON.toUpperCase()}`, () => {
    expect(
      utils.isValidValueIgnoreCase(constants.tokenTypeFilter.FUNGIBLE_COMMON.toUpperCase(), tokenTypeObjectValues)
    ).toBe(true);
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE.toUpperCase()}`, () => {
    expect(
      utils.isValidValueIgnoreCase(constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE.toUpperCase(), tokenTypeObjectValues)
    ).toBe(true);
  });
});

describe('utils encodeMessage tests', () => {
  const inputMessage = Buffer.from([104, 101, 100, 101, 114, 97, 32, 104, 97, 115, 104, 103, 114, 97, 112, 104]);
  const base64Message = 'aGVkZXJhIGhhc2hncmFwaA==';
  const utf8Message = 'hedera hashgraph';

  test(`Verify encodeBinary on null character encoding`, () => {
    expect(utils.encodeBinary(inputMessage, null)).toBe(base64Message);
  });

  test(`Verify encodeBinary on empty character encoding`, () => {
    expect(utils.encodeBinary(inputMessage, '')).toBe(base64Message);
  });

  test(`Verify encodeBinary on hex character encoding`, () => {
    expect(utils.encodeBinary(inputMessage, 'hex')).toBe(base64Message);
  });

  // base64 test
  test(`Verify encodeBinary on base64 character encoding`, () => {
    expect(utils.encodeBinary(inputMessage, constants.characterEncoding.BASE64)).toBe(base64Message);
  });

  // utf-8 test
  test(`Verify encodeBinary on utf-8 character encoding`, () => {
    expect(utils.encodeBinary(inputMessage, constants.characterEncoding.UTF8)).toBe(utf8Message);
  });

  // utf8 test
  test(`Verify encodeBinary on utf8 character encoding`, () => {
    expect(utils.encodeBinary(inputMessage, 'utf8')).toBe(utf8Message);
  });
});

describe('Utils convertMySqlStyleQueryToPostgres tests', () => {
  const testSpecs = [
    {
      sqlQuery: '',
      expected: '',
    },
    {
      sqlQuery: 'select * from t limit 10',
      expected: 'select * from t limit 10',
    },
    {
      sqlQuery: 'select * from t where a = ? and b <> ?',
      expected: 'select * from t where a = $1 and b <> $2',
    },
    {
      sqlQuery: 'select * from t where a = ?a0 and b > ?a0 and c = ? and d < ?d0 and e > ?d0 and f <> ?',
      expected: 'select * from t where a = $1 and b > $1 and c = $2 and d < $3 and e > $3 and f <> $4',
    },
  ];

  testSpecs.forEach((testSpec) => {
    const {sqlQuery, expected} = testSpec;
    test(sqlQuery, () => {
      expect(utils.convertMySqlStyleQueryToPostgres(sqlQuery)).toEqual(expected);
    });
  });
});

describe('Utils randomString tests', () => {
  test('Negative', async () => {
    const val = await utils.randomString(-4);
    expect(val).toMatch(/^[0-9a-z]{2}$/);
  });
  test('Zero', async () => {
    const val = await utils.randomString(0);
    expect(val).toMatch(/^[0-9a-z]{2}$/);
  });
  test('Positive', async () => {
    const val = await utils.randomString(8);
    expect(val).toMatch(/^[0-9a-z]{8}$/);
  });
});

const parseQueryParamTest = (testPrefix, testSpecs, parseParam) => {
  testSpecs.forEach((testSpec) => {
    test(`Utils parseAccountIdQueryParam - ${testSpec.name}`, () => {
      const clauseAndValues = parseParam(testSpec);
      expect(clauseAndValues[0]).toEqual(testSpec.expectedClause);
      expect(clauseAndValues[1]).toEqual(testSpec.expectedValues);
      expect((clauseAndValues[0].match(/\?/g) || []).length).toEqual(testSpec.expectedValues.length);
    });
  });
};

// Common test names for parse*QueryParam tests
const singleParamTestName = 'Single parameter';
const noParamTestName = 'No parameters';
const multipleParamsTestName = 'Multiple parameters different ops';
const extraParamTestName = 'Extra useless parameter';
const multipleEqualsTestName = 'Multiple =';
const duplicateParamsTestName = 'Duplicate parameters';

describe('Utils parseParams tests', () => {
  const testSpecs = [
    {
      name: 'Undefined parameters array',
      parsedQueryParams: undefined,
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: noParamTestName,
      parsedQueryParams: [],
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: singleParamTestName,
      parsedQueryParams: 'gte:1',
      expectedClause: 'column >= ?',
      expectedValues: ['1'],
    },
  ];
  parseQueryParamTest('Utils parseParams - ', testSpecs, (spec) =>
    utils.parseParams(
      spec.parsedQueryParams,
      (value) => value,
      (op, paramValue) => [`column${op}?`, paramValue],
      false
    )
  );

  test('Utils parseParams - Invalid clause', () => {
    expect(() =>
      utils.parseParams(
        'gte:1',
        (value) => value,
        (op, paramValue) => [`column${op}`, paramValue],
        false
      )
    ).toThrow(InvalidClauseError);
    expect(() =>
      utils.parseParams(
        'gte:1',
        (value) => value,
        (op, paramValue) => [`column${op}??`],
        false
      )
    ).toThrow(InvalidClauseError);
    expect(() =>
      utils.parseParams(
        'gte:1',
        (value) => value,
        (op, paramValue) => [`column${op}?`, []],
        false
      )
    ).toThrow(InvalidClauseError);
    expect(() =>
      utils.parseParams(
        'gte:1',
        (value) => value,
        (op, paramValue) => [`column${op}?`, [paramValue, paramValue]],
        true
      )
    ).toThrow(InvalidClauseError);
  });
});

describe('Utils parseAccountIdQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {'account.id': 'gte:0.0.3'},
      expectedClause: 'account.id >= ?',
      expectedValues: ['3'],
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {'account.id': ['gte:0.0.3', 'lt:0.0.5', '2']},
      expectedClause: 'account.id >= ? and account.id < ? and account.id IN (?)',
      expectedValues: ['3', '5', '2'],
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        'account.id': '0.0.3',
        timestamp: '2000',
      },
      expectedClause: 'account.id IN (?)',
      expectedValues: ['3'],
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {'account.id': ['0.0.3', '4']},
      expectedClause: 'account.id IN (?, ?)',
      expectedValues: ['3', '4'],
    },
  ];
  parseQueryParamTest('Utils parseAccountIdQueryParam - ', testSpecs, (spec) =>
    utils.parseAccountIdQueryParam(spec.parsedQueryParams, 'account.id')
  );
});

describe('utils parsePublicKey tests', () => {
  test(`Verify parsePublicKey on null publickey`, () => {
    expect(utils.parsePublicKey(null)).toBe(null);
  });

  test(`Verify parsePublicKey on hex prefix`, () => {
    expect(utils.parsePublicKey(`0x${ed25519Key}`)).toBe(ed25519Key);
  });

  test(`Verify parsePublicKey on ECDSA secp256k1`, () => {
    expect(utils.parsePublicKey(ecdsaKey)).toBe(ecdsaKey);
  });

  test(`Verify parsePublicKey on invalid decode publickey`, () => {
    const key = '2b60955bcbf0cf5e9ea880b52e5b63f664b08edf6ed15e301049517438d61864;';
    expect(utils.parsePublicKey(key)).toBe(key);
  });

  test(`Verify parsePublicKey on valid decode publickey`, () => {
    expect(utils.parsePublicKey(ed25519Der)).toBe(ed25519Key);
  });
});

describe('Utils parseTimestampQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {timestamp: '1000'},
      expectedClause: 'timestamp = ?',
      expectedValues: ['1000000000000'],
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {timestamp: ['gte:1000', 'lt:2000.222', '3000.333333333']},
      expectedClause: 'timestamp >= ? and timestamp < ? and timestamp = ?',
      expectedValues: ['1000000000000', '2000222000000', '3000333333333'],
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        timestamp: '1000',
        'fake.id': '2000',
      },
      expectedClause: 'timestamp = ?',
      expectedValues: ['1000000000000'],
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {timestamp: ['1000', '4000']},
      expectedClause: 'timestamp = ? and timestamp = ?',
      expectedValues: ['1000000000000', '4000000000000'],
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {timestamp: ['5000', '5000', 'lte:1000', 'lte:1000', 'gte:1000', 'gte:2000']},
      expectedClause: 'timestamp = ? and timestamp <= ? and timestamp >= ? and timestamp >= ?',
      expectedValues: ['5000000000000', '1000000000000', '1000000000000', '2000000000000'],
    },
    {
      name: 'Single parameter with OpOverride',
      parsedQueryParams: {timestamp: '1000'},
      expectedClause: 'timestamp <= ?',
      expectedValues: ['1000000000000'],
      opOverride: {
        [utils.opsMap.eq]: utils.opsMap.lte,
      },
    },
  ];
  parseQueryParamTest('Utils parseTimestampQueryParam - ', testSpecs, (spec) =>
    utils.parseTimestampQueryParam(spec.parsedQueryParams, 'timestamp', spec.opOverride)
  );
});

describe('Utils parseTokenBalances', () => {
  const input = [
    {
      token_id: '1005',
      balance: '7500',
    },
    {
      token_id: '4294967396',
      balance: '12000',
    },
  ];
  const expected = [
    {
      token_id: '0.0.1005',
      balance: '7500',
    },
    {
      token_id: '0.1.100',
      balance: '12000',
    },
  ];

  test('success', () => {
    expect(utils.parseTokenBalances(input)).toEqual(expected);
  });

  test('success with null token_id', () => {
    expect(
      utils.parseTokenBalances([
        ...input,
        {
          token_id: null,
        },
      ])
    ).toEqual(expected);
  });

  test('null tokenBalances', () => {
    expect(utils.parseTokenBalances(null)).toEqual([]);
  });

  test('undefined tokenBalances', () => {
    expect(utils.parseTokenBalances(undefined)).toEqual([]);
  });
});

describe('Utils parseBalanceQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {'account.balance': 'gte:1000'},
      expectedClause: 'account.balance >= ?',
      expectedValues: ['1000'],
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {'account.balance': ['gte:1000', 'lt:2000.222', '4000.4444']},
      expectedClause: 'account.balance >= ? and account.balance < ? and account.balance = ?',
      expectedValues: ['1000', '2000.222', '4000.4444'],
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        'account.balance': '1000',
        'fake.id': '2000',
      },
      expectedClause: 'account.balance = ?',
      expectedValues: ['1000'],
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {'account.balance': ['1000', '4000']},
      expectedClause: 'account.balance = ? and account.balance = ?',
      expectedValues: ['1000', '4000'],
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {'account.balance': ['5000', '5000', 'lte:1000', 'lte:1000', 'gte:1000', 'gte:2000']},
      expectedClause: 'account.balance = ? and account.balance <= ? and account.balance >= ? and account.balance >= ?',
      expectedValues: ['5000', '1000', '1000', '2000'],
    },
    {
      name: 'Single parameter not numeric',
      parsedQueryParams: {'account.balance': 'gte:QQQ'},
      expectedClause: '',
      expectedValues: [],
    },
  ];
  parseQueryParamTest('Utils parseBalanceQueryParam - ', testSpecs, (spec) =>
    utils.parseBalanceQueryParam(spec.parsedQueryParams, 'account.balance')
  );
});

describe('Utils parsePublicKeyQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {'account.publickey': 'gte:key'},
      expectedClause: 'account.publickey >= ?',
      expectedValues: ['key'],
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {'account.publickey': ['gte:key1', 'lt:key2', 'key3']},
      expectedClause: 'account.publickey >= ? and account.publickey < ? and account.publickey = ?',
      expectedValues: ['key1', 'key2', 'key3'],
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        'account.publickey': 'key',
        'fake.id': '2000',
      },
      expectedClause: 'account.publickey = ?',
      expectedValues: ['key'],
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {'account.publickey': ['key1', 'key2']},
      expectedClause: 'account.publickey = ? and account.publickey = ?',
      expectedValues: ['key1', 'key2'],
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {'account.publickey': ['key1', 'key1', 'lte:key2', 'lte:key2', 'gte:key2', 'gte:key3']},
      expectedClause:
        'account.publickey = ? and account.publickey <= ? and account.publickey >= ? and account.publickey >= ?',
      expectedValues: ['key1', 'key2', 'key2', 'key3'],
    },
    {
      name: 'Single parameter DER encoded',
      parsedQueryParams: {
        'account.publickey': `gte:${ed25519Der}`,
      },
      expectedClause: 'account.publickey >= ?',
      expectedValues: [ed25519Key],
    },
    {
      name: 'Single parameter ECDSA(secp256k1) encoded',
      parsedQueryParams: {
        'account.publickey': `gte:${ecdsaKey}`,
      },
      expectedClause: 'account.publickey >= ?',
      expectedValues: [ecdsaKey],
    },
  ];
  parseQueryParamTest('Utils parsePublicKeyQueryParam - ', testSpecs, (spec) =>
    utils.parsePublicKeyQueryParam(spec.parsedQueryParams, 'account.publickey')
  );
});

describe('Utils parseCreditDebitParams tests', () => {
  const testSpecs = [
    {
      name: 'Single parameter credit',
      parsedQueryParams: {type: 'credit'},
      expectedClause: 'type > ?',
      expectedValues: [0],
    },
    {
      name: 'Single parameter debit',
      parsedQueryParams: {type: 'debit'},
      expectedClause: 'type < ?',
      expectedValues: [0],
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: '',
      expectedValues: [],
    },
    {
      name: 'Multiple parameters both values',
      parsedQueryParams: {type: ['credit', 'debit']},
      expectedClause: 'type > ? and type < ?',
      expectedValues: [0, 0],
    },
    {
      name: 'Single parameter op ignored',
      parsedQueryParams: {type: ['gte:credit']},
      expectedClause: 'type > ?',
      expectedValues: [0],
    },
    {
      name: 'Single parameter invalid value',
      parsedQueryParams: {type: ['cash']},
      expectedClause: '',
      expectedValues: [],
    },
  ];
  parseQueryParamTest('Utils parseCreditDebitParams - ', testSpecs, (spec) =>
    utils.parseCreditDebitParams(spec.parsedQueryParams, 'type')
  );
});

describe('utils isRepeatedQueryParameterValidLength', () => {
  test(`utils isRepeatedQueryParameterValidLength verify account.id with valid amount ${
    config.maxRepeatedQueryParameters - 1
  } `, () => {
    expect(
      utils.isRepeatedQueryParameterValidLength(Array(config.maxRepeatedQueryParameters - 1).fill('0.0.3'))
    ).toBeTruthy();
  });
  test(`utils isRepeatedQueryParameterValidLength verify account.id with invalid amount ${
    config.maxRepeatedQueryParameters + 1
  }`, () => {
    expect(
      utils.isRepeatedQueryParameterValidLength(Array(config.maxRepeatedQueryParameters + 1).fill('0.0.3'))
    ).toBeFalsy();
  });
  test(`utils isRepeatedQueryParameterValidLength verify account.id with valid amount ${config.maxRepeatedQueryParameters} `, () => {
    expect(
      utils.isRepeatedQueryParameterValidLength(Array(config.maxRepeatedQueryParameters).fill('0.0.3'))
    ).toBeTruthy();
  });
});

describe('utils validateReq', () => {
  const specs = [
    {
      name: 'Too many parameters',
      req: {
        query: {
          timestamp: Array(config.maxRepeatedQueryParameters + 1).fill('123'),
        },
      },
    },
    {
      name: 'Invalid account.id',
      req: {
        query: {
          'account.id': 'x',
        },
      },
    },
    {
      name: 'Invalid account.id and timestamp',
      req: {
        query: {
          'account.id': 'x',
          timestamp: 'x',
        },
      },
    },
    {
      name: 'Invalid account.id array',
      req: {
        query: {
          'account.id': ['0.0.3', 'x'],
        },
      },
    },
  ];

  specs.forEach((spec) => {
    test(`utils validateReq - ${spec.name}`, () => {
      expect(() => utils.validateReq(spec.req)).toThrow(InvalidArgumentError);
    });
  });
});

describe('Utils ipMask tests', () => {
  test('Verify ipV4', () => {
    const maskedIp = utils.ipMask('12.214.31.144');
    expect(maskedIp).toStrictEqual('12.214.31.0');
  });
  test('Verify ipV6', () => {
    const maskedIp = utils.ipMask('2001:0db8:85a3:a13c:0000:8a2e:0370:7334');
    expect(maskedIp).toStrictEqual('2001:db8:85a3::');
  });
  test('Verify ipV6 short form back', () => {
    const maskedIp = utils.ipMask('1::');
    expect(maskedIp).toStrictEqual('1::');
  });
  test('Verify ipV6 short form front', () => {
    const maskedIp = utils.ipMask('::ffff');
    expect(maskedIp).toStrictEqual('::');
  });
  test('Verify ipV6 dual', () => {
    const maskedIp = utils.ipMask('2001:db8:3333:4444:5555:6666:1.2.3.4');
    expect(maskedIp).toStrictEqual('2001:db8:3333::0.0.0.0');
  });
});

describe('Utils toHexString tests', () => {
  const byteArray = [1, 2, 0xab];
  const specs = [
    {
      name: 'no prefix no padding',
      args: [byteArray],
      expected: '0102ab',
    },
    {
      name: 'explicit no prefix no padding',
      args: [byteArray, false],
      expected: '0102ab',
    },
    {
      name: 'add prefix no padding',
      args: [byteArray, true],
      expected: '0x0102ab',
    },
    {
      name: 'no prefix pad to 8',
      args: [byteArray, false, 8],
      expected: '000102ab',
    },
    {
      name: 'add prefix pad to 8',
      args: [byteArray, true, 8],
      expected: '0x000102ab',
    },
    {
      name: 'no prefix pad to 2',
      args: [byteArray, false, 2],
      expected: '0102ab',
    },
    {
      name: 'add prefix pad to 2',
      args: [byteArray, true, 2],
      expected: '0x0102ab',
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(utils.toHexString(...spec.args)).toEqual(spec.expected);
    });
  });
});

describe('Utils getLimitParamValue', () => {
  test('undefined', () => {
    expect(getLimitParamValue(undefined)).toEqual(responseLimit.default);
  });

  test('larger than max', () => {
    expect(getLimitParamValue(`${responseLimit.max + 1}`)).toEqual(responseLimit.max);
  });

  test('max signed long', () => {
    expect(getLimitParamValue('9223372036854775807')).toEqual(responseLimit.max);
  });

  test('values array', () => {
    expect(getLimitParamValue(['1', '50'])).toEqual(50);
  });
});

describe('Utils test - utils.parseTransactionTypeParam', () => {
  test('Utils test - utils.parseTransactionTypeParam - Verify null query params', () => {
    expect(utils.parseTransactionTypeParam(null)).toBe('');
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify undefined query params', () => {
    expect(utils.parseTransactionTypeParam(undefined)).toBe('');
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify empty query params', () => {
    expect(utils.parseTransactionTypeParam({})).toBe('');
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify empty transaction type query', () => {
    expect(() => utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: ''})).toThrowError(
      InvalidArgumentError
    );
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify non applicable transaction type query', () => {
    expect(() =>
      utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'newtransaction'})
    ).toThrowError(InvalidArgumentError);
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify applicable TOKENCREATION transaction type query', () => {
    expect(utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'TOKENCREATION'})).toBe(
      `type = ${TransactionType.getProtoId('TOKENCREATION')}`
    );
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify applicable TOKENASSOCIATE transaction type query', () => {
    expect(utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'TOKENASSOCIATE'})).toBe(
      `type = ${TransactionType.getProtoId('TOKENASSOCIATE')}`
    );
  });
  test('Utils test - utils.parseTransactionTypeParam - Verify applicable consensussubmitmessage transaction type query', () => {
    expect(utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'consensussubmitmessage'})).toBe(
      `type = ${TransactionType.getProtoId('CONSENSUSSUBMITMESSAGE')}`
    );
  });
});

describe('Utils test - utils.checkTimestampRange', () => {
  const makeTimestampFilter = (operator, value) => {
    return {
      key: constants.filterKeys.TIMESTAMP,
      operator,
      value,
    };
  };

  describe('valid', () => {
    const testSpecs = [
      {
        name: 'one filter eq',
        filters: [makeTimestampFilter(utils.opsMap.eq, '1638921702000000000')],
      },
      {
        name: 'two filters gte and lte',
        filters: [
          makeTimestampFilter(utils.opsMap.gte, '1000000000'),
          makeTimestampFilter(utils.opsMap.lte, '2000000000'),
        ],
      },
      {
        name: 'two eq',
        filters: [
          makeTimestampFilter(utils.opsMap.eq, '1000000000'),
          makeTimestampFilter(utils.opsMap.eq, '1638921702000000000'),
        ],
      },
      {
        name: '1ns range with gt and lt',
        filters: [
          makeTimestampFilter(utils.opsMap.gt, '1000999999'),
          makeTimestampFilter(utils.opsMap.lt, '1001000001'),
        ],
      },
      {
        // [1000000, 604800001000000)
        name: 'max range with gte and lt',
        filters: [
          makeTimestampFilter(utils.opsMap.gte, '1000000'),
          makeTimestampFilter(utils.opsMap.lt, '604800001000000'),
        ],
      },
      {
        // effectively the same as [1000000, 604800001000000)
        name: 'max range with gt and lt',
        filters: [
          makeTimestampFilter(utils.opsMap.gt, '999999'),
          makeTimestampFilter(utils.opsMap.lt, '604800001000000'),
        ],
      },
      {
        // effectively the same as [1000000, 604800001000000)
        name: 'max range with gt and lte',
        filters: [
          makeTimestampFilter(utils.opsMap.gt, '1000000'),
          makeTimestampFilter(utils.opsMap.lte, '604800000999999'),
        ],
      },
      {
        // effectively the same as [1000000, 604800001000000)
        name: 'max range with gt and lt',
        filters: [
          makeTimestampFilter(utils.opsMap.gte, '1000000'),
          makeTimestampFilter(utils.opsMap.lte, '604800000999999'),
        ],
      },
    ];

    testSpecs.forEach((spec) =>
      test(spec.name, () => {
        expect(() => utils.checkTimestampRange(spec.filters)).not.toThrow();
      })
    );
  });

  describe('invalid', () => {
    const testSpecs = [
      {
        name: 'no filters',
        filters: [],
      },
      {
        name: 'one filter gt',
        filters: [makeTimestampFilter(utils.opsMap.gt, '1638921702000000000')],
      },
      {
        name: 'one filter ne',
        filters: [makeTimestampFilter(utils.opsMap.ne, '1638921702000000000')],
      },
      {
        name: 'two filters gt and eq',
        filters: [
          makeTimestampFilter(utils.opsMap.gt, '1638921702000'),
          makeTimestampFilter(utils.opsMap.eq, '1638921702000000000'),
        ],
      },
      {
        name: 'bad range lower bound > higher bound gte lte',
        filters: [makeTimestampFilter(utils.opsMap.gte, '1000'), makeTimestampFilter(utils.opsMap.lte, '999')],
      },
      {
        name: 'bad range lower bound > higher bound gte lt',
        filters: [makeTimestampFilter(utils.opsMap.gte, '1000'), makeTimestampFilter(utils.opsMap.lt, '1000')],
      },
      {
        name: 'bad range lower bound > higher bound gt lte',
        filters: [makeTimestampFilter(utils.opsMap.gt, '999'), makeTimestampFilter(utils.opsMap.lte, '999')],
      },
      {
        name: 'two filters gt and get',
        filters: [
          makeTimestampFilter(utils.opsMap.gte, '1000000000'),
          makeTimestampFilter(utils.opsMap.gt, '1638921702000000000'),
        ],
      },
      {
        name: 'two filters lt and lte',
        filters: [
          makeTimestampFilter(utils.opsMap.lt, '1000000000'),
          makeTimestampFilter(utils.opsMap.lte, '1638921702000000000'),
        ],
      },
      {
        name: 'three filters gt lte eq',
        filters: [
          makeTimestampFilter(utils.opsMap.lt, '1000000000'),
          makeTimestampFilter(utils.opsMap.gte, '2000000000'),
          makeTimestampFilter(utils.opsMap.eq, '1000000000'),
        ],
      },
      {
        name: 'bad range lower bound > higher bound gt lt',
        filters: [makeTimestampFilter(utils.opsMap.gt, '999'), makeTimestampFilter(utils.opsMap.lt, '1000')],
      },
      {
        // effectively [100, 604800000000101)
        name: 'range exceeds configured max gt and lt',
        filters: [makeTimestampFilter(utils.opsMap.gt, '99'), makeTimestampFilter(utils.opsMap.lt, '604800000000101')],
      },
      {
        // [100, 604800000000101)
        name: 'range exceeds configured max gte and lt',
        filters: [
          makeTimestampFilter(utils.opsMap.gte, '100'),
          makeTimestampFilter(utils.opsMap.lt, '604800000000101'),
        ],
      },
      {
        // effectively [100, 604800000000101)
        name: 'range exceeds configured max gt and lte',
        filters: [makeTimestampFilter(utils.opsMap.gt, '99'), makeTimestampFilter(utils.opsMap.lte, '604800000000100')],
      },
      {
        // [100, 604800000000101)
        name: 'range exceeds configured max gte and lte',
        filters: [
          makeTimestampFilter(utils.opsMap.gte, '100'),
          makeTimestampFilter(utils.opsMap.lte, '604800000000100'),
        ],
      },
    ];

    testSpecs.forEach((spec) =>
      test(spec.name, () => {
        expect(() => utils.checkTimestampRange(spec.filters)).toThrowErrorMatchingSnapshot();
      })
    );
  });
});
