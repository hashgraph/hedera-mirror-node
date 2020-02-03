/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

describe('utils.validateReq', () => {
  test('transfers=raw', () => {
    var input = {query: {transfers: 'raw'}};
    var val = utils.validateReq(input);
    expect(val.isValid).toBeTruthy();
  });

  test('transfers=invalid', () => {
    var input = {query: {transfers: 'invalid'}};
    var val = utils.validateReq(input);
    expect(val.isValid).toBeFalsy();
  });
});

describe('utils.compareTransfers', () => {
  [
    // aAccount, aAmount, bAccount, bAmount, shouldReturn
    ['0.1.2', 34, '0.1.2', 34, 0],
    ['1.1.2', 34, '0.1.2', 34, 1],
    ['0.2.2', 34, '0.1.2', 34, 1],
    ['0.1.3', 34, '0.1.2', 34, 1],
    ['0.1.2', 34, '0.1.2', 33, 1]
  ].forEach(args => {
    test(args.slice(0, -1).join(' , ') + ' => ' + args.slice(-1)[0], () => {
      var a = {account: args[0], amount: args[1]};
      var b = {account: args[2], amount: args[3]};
      expect(utils.compareTransfers(a, b)).toBe(args[4]); // a, b is expected
      expect(utils.compareTransfers(b, a)).toBe(0 - args[4]); // b, a is the opposite.
    });
  });
});
