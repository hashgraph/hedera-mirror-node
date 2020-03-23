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

const request = require('supertest');
const utils = require('../utils.js');

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
  var validStartNs = '9223372036854775837';
  var shard = 1;
  var realm = 2;
  var num = 995;
  test('Verify createTransactionId returns correct result for valid inputs', () => {
    var val = utils.createTransactionId(shard, realm, num, validStartNs);
    expect(val).toBe(`${shard}.${realm}.${num}-` + '9223372036-854775837');
  });

  test('Verify nsToSecNs returns correct result for 0 inputs', () => {
    var val = utils.createTransactionId(0, 0, 0, 0);
    expect(val).toBe('0.0.0-0-000000000');
  });

  test('Verify nsToSecNs returns correct result for null inputs', () => {
    var val = utils.createTransactionId(0, 0, 0, null);
    expect(val).toBe('0.0.0-0-000000000');
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

describe('Utils makeValidationResponse tests', () => {
  test('Verify success response for null', () => {
    const val = utils.makeValidationResponse(null);
    expect(val).toStrictEqual({isValid: true, code: utils.httpStatusCodes.OK, contents: 'OK'});
  });
  test('Verify success response for empty array', () => {
    const val = utils.makeValidationResponse([]);
    expect(val).toStrictEqual({isValid: true, code: utils.httpStatusCodes.OK, contents: 'OK'});
  });
  test('Verify failure response for null', () => {
    const message = {message: 'Invalid parameter: consensusTimestamp'};
    const val = utils.makeValidationResponse(message);
    expect(val).toStrictEqual({
      isValid: false,
      code: utils.httpStatusCodes.BAD_REQUEST,
      contents: {_status: {messages: message}}
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
