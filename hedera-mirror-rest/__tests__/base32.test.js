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

import base32 from '../base32';

describe('decode', () => {
  describe('valid', () => {
    test('32W35LY', () => {
      expect(base32.decode('32W353Y')).toEqual(Uint8Array.from(Buffer.from('deadbeef', 'hex')));
    });
    test('32W35366', () => {
      expect(base32.decode('32W35366')).toEqual(Uint8Array.from(Buffer.from('deadbeefde', 'hex')));
    });
    test('null', () => {
      expect(base32.decode(null)).toBeNull();
    });
  });

  describe('invalid', () => {
    const invalidBase32Strs = [
      // A base32 group without padding can have 2, 4, 5, 7 or 8 characters from its alphabet
      'A',
      'AAA',
      'AAAAAA',
      // non-base32 characters, note due to the loose option, 0, 1, and 8 will be auto corrected to O, L, and B
      '9',
    ];
    invalidBase32Strs.forEach((invalidBase32Str) => {
      test(`${invalidBase32Str}`, () => {
        expect(() => base32.decode(invalidBase32Str)).toThrowErrorMatchingSnapshot();
      });
    });
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
