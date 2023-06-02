/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';

import AccountAlias from '../accountAlias';
import {getAllAccountAliases, invalidBase32Strs} from './testutils';

describe('AccountAlias', () => {
  describe('fromString', () => {
    describe('valid', () => {
      const testSpecs = [
        {
          input: 'AABBCC22',
          expected: new AccountAlias(null, null, 'AABBCC22'),
        },
        {
          input: '0.AABBCC22',
          expected: new AccountAlias(null, '0', 'AABBCC22'),
        },
        {
          input: '0.1.AABBCC22',
          expected: new AccountAlias('0', '1', 'AABBCC22'),
        },
        {
          input: '99999.99999.AABBCC22',
          expected: new AccountAlias('99999', '99999', 'AABBCC22'),
        },
      ];

      testSpecs.forEach((spec) => {
        test(spec.input, () => {
          expect(AccountAlias.fromString(spec.input)).toEqual(spec.expected);
        });
      });
    });

    describe('invalid', () => {
      const inputs = _.flattenDeep([
        null,
        undefined,
        invalidBase32Strs.map((alias) => getAllAccountAliases(alias)),
        '100000.100000.AABBCC22',
        '0.0.0.AABBCC22',
      ]);

      inputs.forEach((input) => {
        test(`${input}`, () => {
          expect(() => AccountAlias.fromString(input)).toThrowErrorMatchingSnapshot();
        });
      });
    });
  });

  describe('isValid', () => {
    const shardRealmInputs = _.flattenDeep([
      {alias: '', noShardRealm: false, expected: false},
      {alias: undefined, noShardRealm: false, expected: false},
      {alias: null, noShardRealm: false, expected: false},
      {alias: '99999.99999.AABBCC22', noShardRealm: false, expected: true},
      {alias: '99999.99999.AABBCC22', noShardRealm: true, expected: false},
      {alias: 'AABBCC22', noShardRealm: true, expected: true},
      {alias: 'AABBCC22', noShardRealm: false, expected: true},
    ]);

    shardRealmInputs.forEach((input) => {
      test(`${input}`, () => {
        expect(AccountAlias.isValid(input.alias, input.noShardRealm)).toBe(input.expected);
      });
    });
  });

  describe('toString', () => {
    test('only alias', () => {
      const accountAlias = new AccountAlias(null, null, 'AABBCC22');
      expect(accountAlias.toString()).toBe('AABBCC22');
    });
    test('realm and alias', () => {
      const accountAlias = new AccountAlias(null, '0', 'AABBCC22');
      expect(accountAlias.toString()).toBe('0.AABBCC22');
    });
    test('shard, realm and alias', () => {
      const accountAlias = new AccountAlias('0', '1', 'AABBCC22');
      expect(accountAlias.toString()).toBe('0.1.AABBCC22');
    });
  });
});
