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

const base32 = require('../base32');
const {invalidBase32Strs, validBase32Strs} = require('./testutils');

describe('decode', () => {
  test('32W35LY', () => {
    expect(base32.decode('32W353Y')).toEqual(Buffer.from('deadbeef', 'hex'));
  });
  test('32W35366', () => {
    expect(base32.decode('32W35366')).toEqual(Buffer.from('deadbeefde', 'hex'));
  });
  test('null', () => {
    expect(base32.decode(null)).toBeNull();
  });
});

describe('encode', () => {
  test('0xdeadbeef', () => {
    expect(base32.encode(Buffer.from('deadbeef', 'hex'))).toBe('32W353Y');
  });
  test('0xdeadbeefde', () => {
    expect(base32.encode(Buffer.from('deadbeefde', 'hex'))).toBe('32W35366');
  });
  test('null', () => {
    expect(base32.encode(null)).toBeNull();
  });
});

describe('isValidBase32Str', () => {
  describe('valid', () => {
    validBase32Strs.forEach((str) => {
      test(str, () => {
        expect(base32.isValidBase32Str(str)).toBeTrue();
      });
    });
  });

  describe('invalid', () => {
    const data = [...invalidBase32Strs, null, undefined];
    data.forEach((value) => {
      test(`${value}`, () => {
        expect(base32.isValidBase32Str(value)).toBeFalse();
      });
    });
  });
});
