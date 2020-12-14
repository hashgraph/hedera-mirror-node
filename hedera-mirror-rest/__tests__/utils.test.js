/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

describe('Utils isValidEntityNum tests', () => {
  test('Verify invalid for null', () => {
    expect(utils.isValidEntityNum(null)).toBe(false);
  });
  test('Verify invalid for empty input', () => {
    expect(utils.isValidEntityNum('')).toBe(false);
  });
  test('Verify invalid for invalid input', () => {
    expect(utils.isValidEntityNum('1234567890.000000001')).toBe(false);
  });
  test('Verify invalid for negative shard', () => {
    expect(utils.isValidEntityNum('-1.0.1')).toBe(false);
  });
  test('Verify invalid for negative realm', () => {
    expect(utils.isValidEntityNum('0.-1.1')).toBe(false);
  });
  test('Verify invalid for negative entity_num', () => {
    expect(utils.isValidEntityNum('0.0.-1')).toBe(false);
  });
  test('Verify invalid for negative num', () => {
    expect(utils.isValidEntityNum('-1')).toBe(false);
  });
  test('Verify valid for entity_num only', () => {
    expect(utils.isValidEntityNum('3')).toBe(true);
  });
  test('Verify valid for full entity', () => {
    expect(utils.isValidEntityNum('1.2.3')).toBe(true);
  });
  test('Verify valid for full entity 2', () => {
    expect(utils.isValidEntityNum('0.2.3')).toBe(true);
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
