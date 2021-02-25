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

describe('Utils getNullableNumber tests', () => {
  test('Verify getNullableNumber returns correct result for 0', () => {
    var val = utils.getNullableNumber(0);
    expect(val).toBe('0');
  });

  test('Verify getNullableNumber returns correct result for null', () => {
    var val = utils.getNullableNumber(null);
    expect(val).toBe(null);
  });

  test('Verify getNullableNumber returns correct result for undefined', () => {
    var val = utils.getNullableNumber(undefined);
    expect(val).toBe(null);
  });

  test('Verify getNullableNumber returns correct result for valid number', () => {
    var validNumber = 10;
    var val = utils.getNullableNumber(validNumber);
    expect(val).toBe(validNumber.toString());
  });
});

describe('Utils nsToSecNs tests', () => {
  var validStartNs = '9223372036854775837';
  test('Verify nsToSecNs returns correct result for valid validStartNs', () => {
    var val = utils.nsToSecNs(validStartNs);
    expect(val).toBe('9223372036.854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 validStartNs', () => {
    var val = utils.nsToSecNs(0);
    expect(val).toBe('0.000000000');
  });

  test('Verify nsToSecNs returns correct result for null validStartNs', () => {
    var val = utils.nsToSecNs(null);
    expect(val).toBe('0.000000000');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for valid validStartNs', () => {
    var val = utils.nsToSecNsWithHyphen(validStartNs);
    expect(val).toBe('9223372036-854775837');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for 0 validStartNs', () => {
    var val = utils.nsToSecNsWithHyphen(0);
    expect(val).toBe('0-000000000');
  });

  test('Verify nsToSecNsWithHyphen returns correct result for null validStartNs', () => {
    var val = utils.nsToSecNsWithHyphen(null);
    expect(val).toBe('0-000000000');
  });
});

describe('Utils createTransactionId tests', () => {
  test('Verify createTransactionId returns correct result for valid inputs', () => {
    expect(utils.createTransactionId('1.2.995', '9223372036854775837')).toEqual('1.2.995-9223372036-854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 inputs', () => {
    expect(utils.createTransactionId('0.0.0', 0)).toEqual('0.0.0-0-000000000');
  });

  test('Verify nsToSecNs returns correct result for null inputs', () => {
    expect(utils.createTransactionId('0.0.0', null)).toEqual('0.0.0-0-000000000');
  });
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

describe('Utils isValidLimitNum tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidLimitNum(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidLimitNum('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidLimitNum('1234567890.000000001')).toBe(false);
  });
  test('Verify invalid for entity format shard', () => {
    expect(utils.isValidLimitNum('1.0.1')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(utils.isValidLimitNum('-1')).toBe(false);
  });
  test('Verify invalid above max limit', () => {
    expect(utils.isValidLimitNum(config.maxLimit + 1)).toBe(false);
  });
  test('Verify invalid for 0', () => {
    expect(utils.isValidLimitNum(0)).toBe(false);
  });
  test('Verify valid for valid number', () => {
    expect(utils.isValidLimitNum(123)).toBe(true);
  });
  test(`Verify valid for max limit or ${config.maxLimit}`, () => {
    expect(utils.isValidLimitNum(config.maxLimit)).toBe(true);
  });
});

describe('Utils isValidNum tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidNum(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidNum('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidNum('1234567890.000000001')).toBe(false);
  });
  test('Verify invalid for entity format shard', () => {
    expect(utils.isValidNum('1.0.1')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(utils.isValidNum(-1)).toBe(false);
  });
  test('Verify invalid for 0', () => {
    expect(utils.isValidNum(0)).toBe(false);
  });
  test('Verify valid for valid number', () => {
    expect(utils.isValidNum(123)).toBe(true);
  });
  test('Verify valid above max limit', () => {
    expect(utils.isValidNum(12345678901)).toBe(true);
  });
  test(`Verify valid for Number.MAX_SAFE_INTEGER: ${Number.MAX_SAFE_INTEGER}`, () => {
    expect(utils.isValidNum(Number.MAX_SAFE_INTEGER)).toBe(true);
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

  describe('utils parsePublicKey tests', () => {
    test(`Verify parsePublicKey on null publickey`, () => {
      expect(utils.parsePublicKey(null)).toBe(null);
    });

    test(`Verify parsePublicKey on invalid decode publickey`, () => {
      const key = '2b60955bcbf0cf5e9ea880b52e5b63f664b08edf6ed15e301049517438d61864;';
      expect(utils.parsePublicKey(key)).toBe(key);
    });

    test(`Verify parsePublicKey on valid decode publickey`, () => {
      const validDer = '302a300506032b65700321007a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc';
      const validDecoded = '7A3C5477BDF4A63742647D7CFC4544ACC1899D07141CAF4CD9FEA2F75B28A5CC';
      expect(utils.parsePublicKey(validDer)).toBe(validDecoded);
    });
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
  test('Negative', () => {
    const val = utils.randomString(-4);
    expect(val).toMatch(/^[0-9a-z]{2}$/);
  });
  test('Zero', () => {
    const val = utils.randomString(0);
    expect(val).toMatch(/^[0-9a-z]{2}$/);
  });
  test('Positive', () => {
    const val = utils.randomString(8);
    expect(val).toMatch(/^[0-9a-z]{8}$/);
  });
});

const parseQueryParamTest = (testPrefix, testSpecs, parseParam) => {
  testSpecs.forEach((testSpec) => {
    test(`Utils parseAccountIdQueryParam - ${testSpec.name}`, () => {
      const val = parseParam(testSpec);
      expect(val[0]).toEqual(testSpec.expectedClause);
      expect(val[1]).toEqual(testSpec.expectedValues);
    });
  });
};

//Common test names for parse*QueryParam tests
const singleParamTestName = "Single parameter";
const noParamTestName = "No parameters";
const multipleParamsTestName = "Multiple parameters different ops";
const extraParamTestName = "Extra useless parameter";
const multipleEqualsTestName = "Multiple =";
const duplicateParamsTestName = "Duplicate parameters"

describe('Utils parseAccountIdQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {"account.id": "gte:0.0.3"},
      expectedClause: "account.id >= ?",
      expectedValues: ["3"]
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: "",
      expectedValues: []
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {"account.id": ["gte:0.0.3", "lt:0.0.5", "2"]},
      expectedClause: "account.id >= ? and account.id < ? and account.id IN (?)",
      expectedValues: ["3", "5", "2"]
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        "account.id": "0.0.3",
        "timestamp": "2000"
      },
      expectedClause: "account.id IN (?)",
      expectedValues: ["3"]
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {"account.id": ["0.0.3", "4"]},
      expectedClause: "account.id IN (?, ?)",
      expectedValues: ["3", "4"]
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {"account.id": ["0.0.5", "5", "eq:0.0.5", "lte:0.0.3", "lte:0.0.3", "gte:0.0.3", "gte:0.0.4"]},
      expectedClause: "account.id <= ? and account.id >= ? and account.id >= ? and account.id IN (?)",
      expectedValues: ["3", "3", "4", "5"]
    },

  ];
  parseQueryParamTest('Utils parseAccountIdQueryParam - ', testSpecs, (spec) => utils.parseAccountIdQueryParam(spec.parsedQueryParams, "account.id"));
});

describe('Utils parseTimestampQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {"timestamp": "1000"},
      expectedClause: "timestamp = ?",
      expectedValues: ["1000000000000"]
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: "",
      expectedValues: []
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {"timestamp": ["gte:1000", "lt:2000.222", "3000.333333333"]},
      expectedClause: "timestamp >= ? and timestamp < ? and timestamp = ?",
      expectedValues: ["1000000000000", "2000222000000", "3000333333333"]
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        "timestamp": "1000",
        "fake.id": "2000"
      },
      expectedClause: "timestamp = ?",
      expectedValues: ["1000000000000"]
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {"timestamp": ["1000", "4000"]},
      expectedClause: "timestamp = ? and timestamp = ?",
      expectedValues: ["1000000000000", "4000000000000"]
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {"timestamp": ["5000", "5000", "lte:1000", "lte:1000", "gte:1000", "gte:2000"]},
      expectedClause: "timestamp = ? and timestamp <= ? and timestamp >= ? and timestamp >= ?",
      expectedValues: ["5000000000000", "1000000000000", "1000000000000", "2000000000000"]
    },
    {
      name: "Single parameter with OpOverride",
      parsedQueryParams: {"timestamp": "1000"},
      expectedClause: "timestamp <= ?",
      expectedValues: ["1000000000000"],
      opOverride: {
        [utils.opsMap.eq]: utils.opsMap.lte
      }
    },
  ];
  parseQueryParamTest('Utils parseTimestampQueryParam - ', testSpecs, (spec) => utils.parseTimestampQueryParam(spec.parsedQueryParams, "timestamp", spec.opOverride));
});

describe('Utils parseBalanceQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      parsedQueryParams: {"account.balance": "gte:1000"},
      expectedClause: "account.balance >= ?",
      expectedValues: ["1000"]
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: "",
      expectedValues: []
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {"account.balance": ["gte:1000", "lt:2000.222", "4000.4444"]},
      expectedClause: "account.balance >= ? and account.balance < ? and account.balance = ?",
      expectedValues: ["1000", "2000.222", "4000.4444"]
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        "account.balance": "1000",
        "fake.id": "2000"
      },
      expectedClause: "account.balance = ?",
      expectedValues: ["1000"]
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {"account.balance": ["1000", "4000"]},
      expectedClause: "account.balance = ? and account.balance = ?",
      expectedValues: ["1000", "4000"]
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {"account.balance": ["5000", "5000", "lte:1000", "lte:1000", "gte:1000", "gte:2000"]},
      expectedClause: "account.balance = ? and account.balance <= ? and account.balance >= ? and account.balance >= ?",
      expectedValues: ["5000", "1000", "1000", "2000"]
    },
    {
      name: "Single parameter not numeric",
      parsedQueryParams: {"account.balance": "gte:QQQ"},
      expectedClause: "",
      expectedValues: []
    },
  ];
  parseQueryParamTest('Utils parseBalanceQueryParam - ', testSpecs, (spec) => utils.parseBalanceQueryParam(spec.parsedQueryParams, "account.balance"));
});

describe('Utils parsePublicKeyQueryParam tests', () => {
  const testSpecs = [
    {
      name: singleParamTestName,
      //DER borrowed from ed25519.test.js
      parsedQueryParams: {"account.publickey": "gte:key"},
      expectedClause: "account.publickey >= ?",
      expectedValues: ["key"]
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: "",
      expectedValues: []
    },
    {
      name: multipleParamsTestName,
      parsedQueryParams: {"account.publickey": ["gte:key1", "lt:key2", "key3"]},
      expectedClause: "account.publickey >= ? and account.publickey < ? and account.publickey = ?",
      expectedValues: ["key1", "key2", "key3"]
    },
    {
      name: extraParamTestName,
      parsedQueryParams: {
        "account.publickey": "key",
        "fake.id": "2000"
      },
      expectedClause: "account.publickey = ?",
      expectedValues: ["key"]
    },
    {
      name: multipleEqualsTestName,
      parsedQueryParams: {"account.publickey": ["key1", "key2"]},
      expectedClause: "account.publickey = ? and account.publickey = ?",
      expectedValues: ["key1", "key2"]
    },
    {
      name: duplicateParamsTestName,
      parsedQueryParams: {"account.publickey": ["key1", "key1", "lte:key2", "lte:key2", "gte:key2", "gte:key3"]},
      expectedClause: "account.publickey = ? and account.publickey <= ? and account.publickey >= ? and account.publickey >= ?",
      expectedValues: ["key1", "key2", "key2", "key3"]
    },
    {
      name: "Single parameter DER encoded",
      parsedQueryParams: {"account.publickey": "gte:302a300506032b65700321007a3c5477bdf4a63742647d7cfc4544acc1899d07141caf4cd9fea2f75b28a5cc"},
      expectedClause: "account.publickey >= ?",
      expectedValues: ["7A3C5477BDF4A63742647D7CFC4544ACC1899D07141CAF4CD9FEA2F75B28A5CC"]
    },
  ];
  parseQueryParamTest('Utils parsePublicKeyQueryParam - ', testSpecs, (spec) => utils.parsePublicKeyQueryParam(spec.parsedQueryParams, "account.publickey"));
});

describe('Utils parseCreditDebitParams tests', () => {
  const testSpecs = [
    {
      name: "Single parameter credit",
      //DER borrowed from ed25519.test.js
      parsedQueryParams: {"type": "credit"},
      expectedClause: "type > 0",
      expectedValues: [],
    },
    {
      name: "Single parameter debit",
      //DER borrowed from ed25519.type.js
      parsedQueryParams: {"type": "debit"},
      expectedClause: "type < 0",
      expectedValues: [],
    },
    {
      name: noParamTestName,
      parsedQueryParams: {},
      expectedClause: "",
      expectedValues: [],
    },
    {
      name: "Multiple parameters both values",
      parsedQueryParams: {"type": ["credit", "debit"]},
      expectedClause: "type > 0 and type < 0",
      expectedValues: [],
    },
    {
      name: "Single parameter op ignored",
      parsedQueryParams: {"type": ["gte:credit"]},
      expectedClause: "type > 0",
      expectedValues: [],
    },
    {
      name: "Single parameter invalid value",
      parsedQueryParams: {"type": ["cash"]},
      expectedClause: "",
      expectedValues: [],
    },
  ];
  parseQueryParamTest('Utils parseCreditDebitParams - ', testSpecs, (spec) => utils.parseCreditDebitParams(spec.parsedQueryParams, "type"));
});
