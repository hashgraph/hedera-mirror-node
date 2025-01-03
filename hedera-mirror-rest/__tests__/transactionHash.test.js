/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import {isValidTransactionHash} from '../transactionHash';

describe('isValidTransactionHash', () => {
  describe('valid', () => {
    test.each`
      input
      ${'0xb185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53be33'}
      ${'b185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53be33'}
      ${'sYX3ZIyYosyCMIK8ih3ONC/tvI93buZzSY9BLyfr4C8K2Acga4UGofBS5C29U74z'}
    `('$input', ({input}) => {
      expect(isValidTransactionHash(input)).toBeTrue();
    });
  });

  describe('invalid', () => {
    test.each`
      input
      ${'0xb185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53'}
      ${'b185f7648c98a2cc823082bc8a1dce342fedbc8f776ee673498f412f27ebe02f0ad807206b8506a1f052e42dbd53'}
      ${'sYX3ZIyYosyCMIK8ih3ONC/tvI93buZzSY9BLyfr4C8K2Acga4UGofBS5C29'}
      ${'0x'}
      ${''}
    `('$input', ({input}) => {
      expect(isValidTransactionHash(input)).toBeFalse();
    });
  });
});
