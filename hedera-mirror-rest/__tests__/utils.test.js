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
import {proto} from '@hashgraph/proto';
import * as utils from '../utils';
import config from '../config';
import * as constants from '../constants';
import {InvalidArgumentError, InvalidClauseError} from '../errors';

const ecdsaKey = '02b5ffadf88d625cd9074fa01e5280b773a60ed2de55b0d6f94460c0b5a001a258';
const ecdsaProtoKey = {ECDSASecp256k1: Buffer.from(ecdsaKey, 'hex')};
const ed25519Key = '7a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc';
const ed25519ProtoKey = {ed25519: Buffer.from(ed25519Key, 'hex')};
const ed25519Der = `302a300506032b6570032100${ed25519Key}`;
const responseLimit = config.response.limit;

describe('Utils asNullIfDefault', () => {
  test('number with default', () => {
    expect(utils.asNullIfDefault(-1, -1)).toBeNull();
    expect(utils.asNullIfDefault(0, -1)).toEqual(0);
    expect(utils.asNullIfDefault(null, -1)).toBeNull();
    expect(utils.asNullIfDefault(undefined, -1)).toBeUndefined();
  });

  test('string with default', () => {
    expect(utils.asNullIfDefault('default', 'default')).toBeNull();
    expect(utils.asNullIfDefault('foobar', 'default')).toEqual('foobar');
    expect(utils.asNullIfDefault(null, 'default')).toBeNull();
    expect(utils.asNullIfDefault(undefined, 'default')).toBeUndefined();
  });
});

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

describe('Utils incrementTimestampByOneDay tests', () => {
  test('Verify incrementTimestampByOneDay adds a day to the timestamp and rounds', () => {
    const val = utils.incrementTimestampByOneDay(1655164799999999999n);
    expect(val).toBe('1655251199.999999999');
  });

  test('Verify incrementTimestampByOneDay adds a day to the timestamp and rounds', () => {
    const val = utils.incrementTimestampByOneDay(BigInt('1655164799999999999'));
    expect(val).toBe('1655251199.999999999');
  });

  test('Verify incrementTimestampByOneDay with null', () => {
    const val = utils.incrementTimestampByOneDay(null);
    expect(val).toBeNil();
  });

  test('Verify incrementTimestampByOneDay with 0', () => {
    const val = utils.incrementTimestampByOneDay(0);
    expect(val).toBe('86400.000000000');
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
  const getPrimitiveKeyBytes = (protoKey) => {
    return proto.Key.encode(protoKey).finish();
  };

  const getKeyListBytes = (protoKey) => {
    const key = {keyList: proto.KeyList.create({keys: [protoKey]})};
    return getPrimitiveKeyBytes(key);
  };

  const getThresholdKeyBytes = (protoKey) => {
    const key = {thresholdKey: proto.ThresholdKey.create({keys: {keys: [protoKey]}, threshold: 1})};
    return getPrimitiveKeyBytes(key);
  };

  test('Null', () => expect(utils.encodeKey(null)).toBe(null));

  [
    {
      name: 'Empty',
      input: [],
      expected: {
        _type: constants.keyTypes.PROTOBUF,
        key: '',
      },
    },
    {
      name: 'Protobuf',
      input: Buffer.from('abcdef', 'hex'),
      expected: {
        _type: constants.keyTypes.PROTOBUF,
        key: 'abcdef',
      },
    },
    {
      name: 'ECDSA(secp256k1) primitive',
      input: getPrimitiveKeyBytes(ecdsaProtoKey),
      expected: {
        _type: constants.keyTypes.ECDSA_SECP256K1,
        key: ecdsaKey,
      },
    },
    {
      name: 'ECDSA(secp256k1) keylist',
      input: getKeyListBytes(ecdsaProtoKey),
      expected: {
        _type: constants.keyTypes.ECDSA_SECP256K1,
        key: ecdsaKey,
      },
    },
    {
      name: 'ECDSA(secp256k1) threshold',
      input: getThresholdKeyBytes(ecdsaProtoKey),
      expected: {
        _type: constants.keyTypes.ECDSA_SECP256K1,
        key: ecdsaKey,
      },
    },
    {
      name: 'ED25519 primitive',
      input: getPrimitiveKeyBytes(ed25519ProtoKey),
      expected: {
        _type: constants.keyTypes.ED25519,
        key: ed25519Key,
      },
    },
    {
      name: 'ED25519 keylist',
      input: getKeyListBytes(ed25519ProtoKey),
      expected: {
        _type: constants.keyTypes.ED25519,
        key: ed25519Key,
      },
    },
    {
      name: 'ED25519 threshold',
      input: getThresholdKeyBytes(ed25519ProtoKey),
      expected: {
        _type: constants.keyTypes.ED25519,
        key: ed25519Key,
      },
    },
  ].forEach((spec) => {
    test(spec.name, () => expect(utils.encodeKey(spec.input)).toStrictEqual(spec.expected));
  });
});

describe('Utils isValidPublicKeyQuery', () => {
  test('Null', () => expect(utils.isValidPublicKeyQuery(null)).toBeFalse());
  test('Empty', () => expect(utils.isValidPublicKeyQuery('')).toBeFalse());
  test('Valid ECDSA(secp256k1)', () => expect(utils.isValidPublicKeyQuery(ecdsaKey)).toBeTrue());
  test('Valid ED25519', () => expect(utils.isValidPublicKeyQuery(ed25519Key)).toBeTrue());
  test('Valid ED25519 DER', () => expect(utils.isValidPublicKeyQuery(ed25519Der)).toBeTrue());
  test('0x ECDSA', () => expect(utils.isValidPublicKeyQuery(`0x${ed25519Key}`)).toBeTrue());
  test('0x ED25519', () => expect(utils.isValidPublicKeyQuery(`0x${ecdsaKey}`)).toBeTrue());
  test('Invalid', () => expect(utils.isValidPublicKeyQuery(`${ed25519Key}F`)).toBeFalse());
});

describe('Utils isValidTimestampParam tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidTimestampParam(null)).toBeFalse();
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidTimestampParam('')).toBeFalse();
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidTimestampParam('0.0.1')).toBeFalse();
  });
  test('Verify invalid for invalid seconds', () => {
    expect(utils.isValidTimestampParam('12345678901')).toBeFalse();
  });
  test('Verify invalid for invalid nanoseconds', () => {
    expect(utils.isValidTimestampParam('1234567890.0000000012')).toBeFalse();
  });
  test('Verify valid for seconds only', () => {
    expect(utils.isValidTimestampParam('1234567890')).toBeTrue();
  });
  test('Verify valid for seconds and nanoseconds', () => {
    expect(utils.isValidTimestampParam('1234567890.000000001')).toBeTrue();
  });
});

describe('isValidSlot', () => {
  describe('valid', () => {
    test.each([
      '1',
      '01',
      'ab',
      'AB',
      '0xab',
      '0xAB',
      '0000000000000000000000000000000000000000000000000000000000000001',
      '0x0000000000000000000000000000000000000000000000000000000000000001',
    ])('%s', (slot) => expect(utils.isValidSlot(slot)).toBeTrue());
  });

  describe('invalid', () => {
    test.each([
      null,
      '',
      '1g',
      '00000000000000000000000000000000000000000000000000000000000000011',
      '0x00000000000000000000000000000000000000000000000000000000000000011',
    ])('%s', (slot) => expect(utils.isValidSlot(slot)).toBeFalse());
  });
});

describe('parseInteger', () => {
  [
    {input: '1', expected: 1},
    {input: `${Number.MAX_SAFE_INTEGER}`, expected: Number.MAX_SAFE_INTEGER},
    {input: `${2n ** 53n}`, expected: 2n ** 53n},
  ].forEach((spec) => {
    test(spec.input, () => {
      expect(utils.parseInteger(spec.input)).toBe(spec.expected);
    });
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
        expect(utils.isNonNegativeInt32(value)).toBeTrue();
      });
    }
  });

  describe('false', () => {
    const values = ['a', '-1', '1.1', '2147483648'];
    for (const value of values) {
      test(value, () => {
        expect(utils.isNonNegativeInt32(value)).toBeFalse();
      });
    }
  });
});

describe('Utils isPositiveLong', () => {
  test('Verify invalid for null', () => {
    expect(utils.isPositiveLong(null)).toBeFalse();
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isPositiveLong('')).toBeFalse();
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isPositiveLong('1234567890.000000001')).toBeFalse();
  });
  test('Verify invalid for entity format shard', () => {
    expect(utils.isPositiveLong('1.0.1')).toBeFalse();
  });
  test('Verify invalid for negative num', () => {
    expect(utils.isPositiveLong(-1)).toBeFalse();
  });
  test('Verify invalid for 0', () => {
    expect(utils.isPositiveLong(0)).toBeFalse();
  });
  test(`Verify invalid for unsigned long 9223372036854775808`, () => {
    expect(utils.isPositiveLong('9223372036854775808')).toBeFalse();
  });
  test('Verify invalid for 0 with allowZero=true', () => {
    expect(utils.isPositiveLong(0, true)).toBeTrue();
  });
  test('Verify valid for valid number string', () => {
    expect(utils.isPositiveLong('123')).toBeTrue();
  });
  test('Verify valid for valid number', () => {
    expect(utils.isPositiveLong(123)).toBeTrue();
  });
  test(`Verify valid for max unsigned long: 9223372036854775807`, () => {
    expect(utils.isPositiveLong('9223372036854775807')).toBeTrue();
  });
});

describe('Utils isValidEthHash', () => {
  test('Verify invalid for empty input', () => {
    expect(utils.isValidEthHash()).toBeFalse();
  });

  test('Verify invalid for empty string', () => {
    expect(utils.isValidEthHash('')).toBeFalse();
  });

  test('Verify invalid for incorrect data types', () => {
    expect(utils.isValidEthHash({})).toBeFalse();
    expect(utils.isValidEthHash([1, 2, 3])).toBeFalse();
    expect(utils.isValidEthHash(100)).toBeFalse();
    expect(utils.isValidEthHash(true)).toBeFalse();
  });

  test('Verify invalid for hex with incorrect length', () => {
    expect(utils.isValidEthHash('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb639')).toBeFalse();
    expect(utils.isValidEthHash('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb63923')).toBeFalse();
    expect(utils.isValidEthHash('4a56')).toBeFalse();
    expect(utils.isValidEthHash('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb63922acd')).toBeFalse();
  });

  test('Verify invalid for 64 char long random string', () => {
    expect(utils.isValidEthHash('qwert1234qwert1234qwert1234qwert1234qwert1234qwert1234qwert1234a')).toBeFalse();
  });

  test('Verify invalid for 64 char long random string with 0x prefix', () => {
    expect(utils.isValidEthHash('qwert1234qwert1234qwert1234qwert1234qwert1234qwert1234qwert1234a')).toBeFalse();
    expect(utils.isValidEthHash('0xqwert1234qwert1234qwert1234qwert1234qwert1234qwert1234qwert1234a')).toBeFalse();
  });

  test('Verify valid for 32 byte hash', () => {
    expect(utils.isValidEthHash('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392')).toBeTrue();
  });

  test('Verify valid for 32 byte hash with 0x prefix', () => {
    expect(utils.isValidEthHash('0x4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392')).toBeTrue();
  });
});

describe('Utils isValidValueIgnoreCase tokenTypeFilter tests', () => {
  const tokenTypeObjectValues = Object.values(constants.tokenTypeFilter);

  test('Verify invalid for empty input', () => {
    expect(utils.isValidValueIgnoreCase('', tokenTypeObjectValues)).toBeFalse();
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidValueIgnoreCase('1234567890.000000001', tokenTypeObjectValues)).toBeFalse();
  });
  test('Verify invalid for entity format shard', () => {
    expect(utils.isValidValueIgnoreCase('1.0.1', tokenTypeObjectValues)).toBeFalse();
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.ALL}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.ALL, tokenTypeObjectValues)).toBeTrue();
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.FUNGIBLE_COMMON}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.FUNGIBLE_COMMON, tokenTypeObjectValues)).toBeTrue();
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE, tokenTypeObjectValues)).toBe(
      true
    );
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.ALL.toUpperCase()}`, () => {
    expect(utils.isValidValueIgnoreCase(constants.tokenTypeFilter.ALL.toUpperCase(), tokenTypeObjectValues)).toBeTrue();
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.FUNGIBLE_COMMON.toUpperCase()}`, () => {
    expect(
      utils.isValidValueIgnoreCase(constants.tokenTypeFilter.FUNGIBLE_COMMON.toUpperCase(), tokenTypeObjectValues)
    ).toBeTrue();
  });
  test(`Verify valid for tokenType: ${constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE.toUpperCase()}`, () => {
    expect(
      utils.isValidValueIgnoreCase(constants.tokenTypeFilter.NON_FUNGIBLE_UNIQUE.toUpperCase(), tokenTypeObjectValues)
    ).toBeTrue();
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

const parseQueryParamTest = (testSpecs, parseParam) => {
  testSpecs.forEach((testSpec) => {
    test(testSpec.name, () => {
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
  parseQueryParamTest(testSpecs, (spec) =>
    utils.parseParams(
      spec.parsedQueryParams,
      (value) => value,
      (op, paramValue) => [`column${op}?`, paramValue],
      false
    )
  );

  test('Invalid clause', () => {
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
      expectedValues: [3],
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
      expectedValues: [3, 5, 2],
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        'account.id': '0.0.3',
        timestamp: '2000',
      },
      expectedClause: 'account.id IN (?)',
      expectedValues: [3],
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {'account.id': ['0.0.3', '4']},
      expectedClause: 'account.id IN (?, ?)',
      expectedValues: [3, 4],
    },
  ];
  parseQueryParamTest(testSpecs, (spec) => utils.parseAccountIdQueryParam(spec.parsedQueryParams, 'account.id'));
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
  parseQueryParamTest(testSpecs, (spec) =>
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
  parseQueryParamTest(testSpecs, (spec) => utils.parseBalanceQueryParam(spec.parsedQueryParams, 'account.balance'));
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
  parseQueryParamTest(testSpecs, (spec) => utils.parsePublicKeyQueryParam(spec.parsedQueryParams, 'account.publickey'));
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
  parseQueryParamTest(testSpecs, (spec) => utils.parseCreditDebitParams(spec.parsedQueryParams, 'type'));
});

describe('utils isRepeatedQueryParameterValidLength', () => {
  const {maxRepeatedQueryParameters} = config.query;
  test(`verify account.id with valid amount ${maxRepeatedQueryParameters - 1}`, () => {
    expect(utils.isRepeatedQueryParameterValidLength(Array(maxRepeatedQueryParameters - 1).fill('0.0.3'))).toBeTrue();
  });
  test(`verify account.id with invalid amount ${maxRepeatedQueryParameters + 1}`, () => {
    expect(utils.isRepeatedQueryParameterValidLength(Array(maxRepeatedQueryParameters + 1).fill('0.0.3'))).toBeFalse();
  });
  test(`verify account.id with valid amount ${maxRepeatedQueryParameters}`, () => {
    expect(utils.isRepeatedQueryParameterValidLength(Array(maxRepeatedQueryParameters).fill('0.0.3'))).toBeTrue();
  });
});

describe('utils validateReq', () => {
  const specs = [
    {
      name: 'Too many parameters',
      req: {
        query: {
          timestamp: Array(config.query.maxRepeatedQueryParameters + 1).fill('123'),
        },
      },
      acceptedParameters: new Set(['timestamp']),
    },
    {
      name: 'Invalid account.id',
      req: {
        query: {
          'account.id': 'x',
        },
      },
      acceptedParameters: new Set(['account.id']),
    },
    {
      name: 'Invalid account.id and timestamp',
      req: {
        query: {
          'account.id': 'x',
          timestamp: 'x',
        },
      },
      acceptedParameters: new Set(['account.id', 'timestamp']),
    },
    {
      name: 'Invalid account.id array',
      req: {
        query: {
          'account.id': ['0.0.3', 'x'],
        },
      },
      acceptedParameters: new Set(['account.id']),
    },
    {
      name: 'Invalid parameter key',
      req: {
        query: {
          'account.id': ['0.0.3'],
        },
      },
      acceptedParameters: new Set(['timestamp']),
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(() => utils.validateReq(spec.req, spec.acceptedParameters)).toThrow(InvalidArgumentError);
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

const hexPrefix = '0x';
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
    {
      name: 'empty array',
      args: [[], true, 2],
      expected: '0x',
    },
    {
      name: 'null array',
      args: [null, true, 2],
      expected: hexPrefix,
    },
    {
      name: 'undefined array',
      args: [undefined, true, 2],
      expected: hexPrefix,
    },
    {
      name: 'empty array no prefix',
      args: [[], false, 2],
      expected: hexPrefix,
    },
    {
      name: 'null array no prefix',
      args: [null, false, 2],
      expected: hexPrefix,
    },
    {
      name: 'undefined array no prefix',
      args: [undefined, false, 2],
      expected: hexPrefix,
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(utils.toHexString(...spec.args)).toEqual(spec.expected);
    });
  });
});

describe('Utils toHexStringNonQuantity and toHexStringQuantity tests', () => {
  const specs = [
    {
      name: '0',
      args: [[0]],
      expected: '0x00',
    },
    {
      name: '1',
      args: [[1]],
      expected: '0x01',
    },
    {
      name: '65',
      args: [[65]],
      expected: '0x41',
    },
    {
      name: '1024',
      args: [[4, 0]],
      expected: '0x0400',
    },
    {
      name: 'byteArray',
      args: [[1, 2, 0xab]],
      expected: '0x0102ab',
    },
    {
      name: 'empty array',
      args: [[]],
      expected: hexPrefix,
    },
    {
      name: 'null',
      args: [null],
      expected: hexPrefix,
    },
    {
      name: 'undefined',
      args: [undefined],
      expected: hexPrefix,
    },
    {
      name: 'hash',
      args: [Buffer.from('4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392', 'hex')],
      expected: '0x4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392',
    },
  ];

  describe('toHexStringNonQuantity', () => {
    specs.forEach((spec) => {
      test(spec.name, () => {
        expect(utils.toHexStringNonQuantity(...spec.args)).toEqual(spec.expected);
      });
    });
  });

  describe('toHexStringQuantity', () => {
    const overwriteExpected = [
      {expected: '0x0'},
      {expected: '0x1'},
      {expected: '0x41'},
      {expected: '0x400'},
      {expected: '0x102ab'},
      {expected: hexPrefix},
      {expected: hexPrefix},
      {expected: hexPrefix},
      {expected: '0x4a563af33c4871b51a8b108aa2fe1dd5280a30dfb7236170ae5e5e7957eb6392'},
    ];

    const overwritedSpecs = specs.map((spec, i) => {
      return {...spec, ...overwriteExpected[i]};
    });

    overwritedSpecs.forEach((spec) => {
      test(spec.name, () => {
        expect(utils.toHexStringQuantity(...spec.args)).toEqual(spec.expected);
      });
    });
  });
});

describe('Utils getLimitParamValue', () => {
  test('undefined', () => {
    expect(utils.getLimitParamValue(undefined)).toEqual(responseLimit.default);
  });

  test('larger than max', () => {
    expect(utils.getLimitParamValue(`${responseLimit.max + 1}`)).toEqual(responseLimit.max);
  });

  test('max signed long', () => {
    expect(utils.getLimitParamValue('9223372036854775807')).toEqual(responseLimit.max);
  });

  test('values array', () => {
    expect(utils.getLimitParamValue(['1', '50'])).toEqual(50);
  });
});

describe('Utils test - utils.parseTransactionTypeParam', () => {
  test('Verify null query params', () => {
    expect(utils.parseTransactionTypeParam(null)).toBe('');
  });
  test('Verify undefined query params', () => {
    expect(utils.parseTransactionTypeParam(undefined)).toBe('');
  });
  test('Verify empty query params', () => {
    expect(utils.parseTransactionTypeParam({})).toBe('');
  });
  test('Verify empty transaction type query', () => {
    expect(() => utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: ''})).toThrowError(
      InvalidArgumentError
    );
  });
  test('Verify non applicable transaction type query', () => {
    expect(() =>
      utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'newtransaction'})
    ).toThrowError(InvalidArgumentError);
  });
  test('Verify applicable TOKENCREATION transaction type query', () => {
    expect(utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'TOKENCREATION'})).toBe(
      `type in (29)`
    );
  });
  test('Verify applicable TOKENASSOCIATE transaction type query', () => {
    expect(utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'TOKENASSOCIATE'})).toBe(
      `type in (40)`
    );
  });
  test('Verify applicable consensussubmitmessage transaction type query', () => {
    expect(utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: 'consensussubmitmessage'})).toBe(
      `type in (27)`
    );
  });
  test('Verify multiple transactiontype query params', () => {
    expect(
      utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: ['TOKENCREATION', 'TOKENASSOCIATE']})
    ).toBe(`type in (29,40)`);
  });
  test('Verify multiple transactiontype query params reversed', () => {
    expect(
      utils.parseTransactionTypeParam({[constants.filterKeys.TRANSACTION_TYPE]: ['TOKENASSOCIATE', 'TOKENCREATION']})
    ).toBe('type in (40,29)');
  });
  test('Verify multiple transactiontype query params with duplicates', () => {
    expect(
      utils.parseTransactionTypeParam({
        [constants.filterKeys.TRANSACTION_TYPE]: ['TOKENASSOCIATE', 'TOKENCREATION', 'TOKENASSOCIATE', 'TOKENCREATION'],
      })
    ).toBe('type in (40,29)');
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

describe('Utils getNextParamQueries', () => {
  const testSpecs = [
    {
      name: 'limit (eq) only with ASC',
      args: [
        constants.orderFilterValues.ASC,
        {
          [constants.filterKeys.LIMIT]: 10,
        },
        {
          [constants.filterKeys.ACCOUNT_ID]: 3,
        },
        {},
      ],
      expected: '?limit=10&account.id=gt:3',
    },
    {
      name: 'limit (eq) with DESC',
      args: [
        constants.orderFilterValues.DESC,
        {
          [constants.filterKeys.LIMIT]: 10,
          [constants.filterKeys.ORDER]: 'desc',
        },
        {
          [constants.filterKeys.ACCOUNT_ID]: 3,
        },
        {},
      ],
      expected: '?limit=10&order=desc&account.id=lt:3',
    },
    {
      name: 'order only with DESC',
      args: [
        constants.orderFilterValues.DESC,
        {
          [constants.filterKeys.ORDER]: 'desc',
        },
        {
          [constants.filterKeys.TOKEN_ID]: 3,
        },
        {},
      ],
      expected: '?order=desc&token.id=lt:3',
    },
    {
      name: 'tokenId (gt) only with ASC',
      args: [
        constants.orderFilterValues.ASC,
        {},
        {
          [constants.filterKeys.TOKEN_ID]: 3,
        },
        {},
      ],
      expected: '?token.id=gt:3',
    },
    {
      name: 'tokenId (lte) only with DESC',
      args: [
        constants.orderFilterValues.DESC,
        {
          [constants.filterKeys.ORDER]: 'desc',
        },
        {
          [constants.filterKeys.TOKEN_ID]: 3,
        },
        {},
      ],
      expected: '?order=desc&token.id=lt:3',
    },
    {
      name: 'tokenId (eq) and serial (gt) combo with ASC',
      args: [
        constants.orderFilterValues.ASC,
        {
          [constants.filterKeys.TOKEN_ID]: 2,
          [constants.filterKeys.SERIAL_NUMBER]: 'gt:1',
        },
        {
          [constants.filterKeys.TOKEN_ID]: 2,
          [constants.filterKeys.SERIAL_NUMBER]: 4,
        },
        {},
      ],
      expected: '?token.id=2&serialnumber=gt:4',
    },
    {
      name: 'tokenId (lte) and serial (gte) combo with ASC',
      args: [
        constants.orderFilterValues.ASC,
        {
          [constants.filterKeys.TOKEN_ID]: 'lte:5',
          [constants.filterKeys.SERIAL_NUMBER]: 'gte:1',
        },
        {
          [constants.filterKeys.TOKEN_ID]: 2,
          [constants.filterKeys.SERIAL_NUMBER]: 4,
        },
        {},
      ],
      expected: '?token.id=lte:5&token.id=gt:2&serialnumber=gt:4',
    },
    {
      name: 'tokenId (lte) and serial (gte) combo with DESC',
      args: [
        constants.orderFilterValues.DESC,
        {
          [constants.filterKeys.TOKEN_ID]: 'lte:5',
          [constants.filterKeys.SERIAL_NUMBER]: 'gte:1',
        },
        {
          [constants.filterKeys.TOKEN_ID]: 2,
          [constants.filterKeys.SERIAL_NUMBER]: 4,
        },
        {},
      ],
      expected: '?serialnumber=gte:1&serialnumber=lt:4&token.id=lt:2',
    },
    {
      name: 'serialnumber (gt) and serial (lt) with DESC',
      args: [
        constants.orderFilterValues.DESC,
        {
          [constants.filterKeys.SERIAL_NUMBER]: 'gt:1',
          [constants.filterKeys.ACCOUNT_ID]: 1001,
          [constants.filterKeys.ORDER]: 'desc',
          [constants.filterKeys.LIMIT]: 2,
        },
        {
          [constants.filterKeys.SERIAL_NUMBER]: 3,
        },
        {},
      ],
      expected: '?serialnumber=gt:1&serialnumber=lt:3&account.id=1001&order=desc&limit=2',
    },
    {
      name: 'serialnumber (gt) and serial (lt) with DESC',
      args: [
        constants.orderFilterValues.ASC,
        {
          [constants.filterKeys.ACCOUNT_ID]: ['gte:0.0.18', 'lt:0.0.21'],
          [constants.filterKeys.LIMIT]: 2,
        },
        {
          [constants.filterKeys.ACCOUNT_ID]: '0.0.19',
        },
        {},
      ],
      expected: '?account.id=lt:0.0.21&account.id=gt:0.0.19&limit=2',
    },
    {
      name: 'serialnumber (gte) and serial (lte) with ASC and inclusive',
      args: [
        constants.orderFilterValues.ASC,
        {
          [constants.filterKeys.SERIAL_NUMBER]: 'gte:2',
          [constants.filterKeys.TOKEN_ID]: 'gte:100',
          [constants.filterKeys.ORDER]: 'asc',
          [constants.filterKeys.LIMIT]: 2,
        },
        {
          [constants.filterKeys.SERIAL_NUMBER]: 3,
          [constants.filterKeys.TOKEN_ID]: {value: 100, inclusive: true},
        },
      ],
      expected: '?order=asc&limit=2&serialnumber=gt:3&token.id=gte:100',
    },
  ];

  testSpecs.forEach((spec) => {
    test(spec.name, () => {
      expect(utils.getNextParamQueries(...spec.args)).toEqual(spec.expected);
    });
  });
});

describe('Utils addHexPrefix tests', () => {
  const specs = [
    {
      name: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      args: ['4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f'],
      expected: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    },
    {
      name: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
      args: ['0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f'],
      expected: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    },
    {
      name: 'Buffer from string',
      args: [Buffer.from('4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f', 'utf8')],
      expected: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    },
    {
      name: 'Buffer from string with prefix',
      args: [Buffer.from('0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f', 'utf8')],
      expected: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    },
    {
      name: 'Ascii byte array without prefix',
      args: [[0x61, 0x62]],
      expected: '0xab',
    },
    {
      name: 'Ascii byte array with prefix',
      args: [[0x30, 0x78, 0x61, 0x62]],
      expected: '0xab',
    },
    {
      name: 'Byte array without prefix',
      args: [[97, 98]],
      expected: '0xab',
    },
    {
      name: 'Byte array with prefix',
      args: [[48, 120, 97, 98]],
      expected: '0xab',
    },
    {
      name: '""',
      args: [''],
      expected: hexPrefix,
    },
    {
      name: 'null',
      args: [null],
      expected: hexPrefix,
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(utils.addHexPrefix(...spec.args)).toEqual(spec.expected);
    });
  });
});

describe('Utils convertGasPriceToTinyBars tests', () => {
  const defaultGasPrice = 11566419;
  const defaultHbars = 30000;
  const defaultCents = 285576;
  const specs = [
    {
      name: 'no args',
      args: [],
      expected: null,
    },
    {
      name: 'has undefined arg',
      args: [defaultGasPrice, defaultHbars, undefined],
      expected: null,
    },
    {
      name: 'should return estimated tinybars',
      args: [defaultGasPrice, defaultHbars, defaultCents],
      expected: 1215,
    },
    {
      name: 'should return the minimum tinybars',
      args: [1, defaultHbars, defaultCents],
      expected: 1,
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(utils.convertGasPriceToTinyBars(...spec.args)).toEqual(spec.expected);
    });
  });
});

describe('Utils getStakingPeriod tests', () => {
  const specs = [
    {
      name: 'null',
      args: null,
      expected: null,
    },
    {
      name: 'staking period',
      args: 1654991999999999999n,
      expected: {
        from: '1654992000.000000000',
        to: '1655078400.000000000',
      },
    },
  ];

  specs.forEach((spec) => {
    test(spec.name, () => {
      expect(utils.getStakingPeriod(spec.args)).toEqual(spec.expected);
    });
  });
});

describe('stripHexPrefix', () => {
  test.each([
    ['', ''],
    ['1', '1'],
    ['0x1', '1'],
    [1, 1],
    [null, null],
    [undefined, undefined],
  ])('%s expect %s', (input, expected) => {
    expect(utils.stripHexPrefix(input)).toEqual(expected);
  });
});

describe('toUint256', () => {
  test('Verify null value', () => {
    expect(utils.toUint256(null)).toEqual(null);
  });
  test('Verify empty buffer', () => {
    expect(utils.toUint256(Buffer.from(''))).toEqual(constants.ZERO_UINT256);
  });
  test('Verify non-empty zero buffer', () => {
    expect(utils.toUint256(Buffer.from('0', 'hex'))).toEqual(constants.ZERO_UINT256);
  });
  test('Verify non-empty buffer', () => {
    expect(utils.toUint256(Buffer.from('1a72', 'hex'))).toEqual(
      '0x0000000000000000000000000000000000000000000000000000000000001a72'
    );
  });
});

describe('calculateExpiryTimestamp', () => {
  const autoRenewPeriodMax = 8_000_001; // ~92.5626 days as seconds
  const autoRenewPeriodMin = 6_999_999; // ~81.01 days as seconds
  test.each([
    ['', '', '', ''],
    [null, null, null, null],
    [undefined, undefined, undefined, undefined],
    [undefined, undefined, 123, 123],
    [undefined, 1, 10, 10],
    [1, undefined, 10, 10],
    [1, 1, 10000000001, 10000000001],
    [undefined, 1, undefined, undefined],
    [1, undefined, undefined, undefined],
    [1500, 987654111123456n, undefined, 989154111123456n],
    // Friday, February 13, 2009 11:31:30 PM GMT + 92.5626 days (8000001 seconds) -> Sunday, May 17, 2009 1:44:51 PM
    [autoRenewPeriodMax, 1234567890000000003n, undefined, 1242567891000000003n],
    // Monday, November 21, 2022 8:58:21 PM GMT + 81.01 days (6999999 seconds) -> Friday, February 10, 2023 9:25:00 PM
    [autoRenewPeriodMin, 1669064301000000001n, undefined, 1676064300000000001n],
  ])('%s expect %s', (autoRenewPeriod, createdTimestamp, expirationTimestamp, expected) => {
    expect(utils.calculateExpiryTimestamp(autoRenewPeriod, createdTimestamp, expirationTimestamp)).toEqual(expected);
    if (!_.isNil(autoRenewPeriod) && !_.isNil(createdTimestamp) && _.isNil(expirationTimestamp)) {
      const createdTimestampDate = utils.nsToSecNs(Number(createdTimestamp));
      const createdDate = new Date(Number(createdTimestampDate));
      const expiryDate = new Date(Number(utils.nsToSecNs(expected)));
      const difference = expiryDate.getTime() - createdDate.getTime();
      expect(difference).toEqual(autoRenewPeriod);
    }
  });
});

describe('Utils formatSlot tests', () => {
  test('Verify valid contract_state_change table format slot', () => {
    const slot = '0x0000000000000000000000000000000000000000000000000000000000000003';
    const formatedSlot = '03';
    expect(utils.formatSlot(slot, true)).toEqual(Buffer.from(formatedSlot, 'hex'));
  });

  test('Verify valid slot format if no table is provided', () => {
    const slot = '0x0000000000000000000000000000000000000000000000000000000000000001';
    const formatedSlot = '0000000000000000000000000000000000000000000000000000000000000001';
    expect(utils.formatSlot(slot)).toEqual(Buffer.from(formatedSlot, 'hex'));
  });
});
