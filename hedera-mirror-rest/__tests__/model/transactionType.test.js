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

// models
const {TransactionType} = require('../../model');
const {InvalidArgumentError} = require('../../errors/invalidArgumentError');

describe('getTransactionResultName', () => {
  test('Return valid name', () => {
    expect(TransactionType.getTransactionTypeName(11)).toEqual('CRYPTOCREATEACCOUNT');
  });
  test('Return UNKNOWN for future proto id', () => {
    expect(TransactionType.getTransactionTypeName(9999999)).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for invalid proto id', () => {
    expect(TransactionType.getTransactionTypeName('')).toEqual('UNKNOWN');
  });
});

describe('getTransactionTypeProtoId', () => {
  test('Return valid proto id', () => {
    expect(TransactionType.getTransactionTypeProtoId('CRYPTOCREATEACCOUNT')).toEqual('11');
  });
  test('Return valid proto id for camel case', () => {
    expect(TransactionType.getTransactionTypeProtoId('cryptoCreateAccount')).toEqual('11');
  });
  test('Throw error for invalid name', () => {
    expect(() => {
      TransactionType.getTransactionTypeProtoId(22);
    }).toThrowError(InvalidArgumentError);
  });
  test('Throw error for unknown name', () => {
    expect(() => {
      TransactionType.getTransactionTypeProtoId('UNKNOWN');
    }).toThrowError(InvalidArgumentError);
  });

  describe('isValidTransactionType', () => {
    test('Return valid proto id', () => {
      expect(TransactionType.isValidTransactionType('CRYPTOCREATEACCOUNT')).toBeTruthy();
    });
    test('Return valid proto id for camel case', () => {
      expect(TransactionType.isValidTransactionType('cryptoCreateAccount')).toBeTruthy();
    });
    test('Throw error for invalid name', () => {
      expect(TransactionType.isValidTransactionType(22)).toBeFalsy();
    });
    test('Throw error for unknown name', () => {
      expect(TransactionType.isValidTransactionType('')).toBeFalsy();
    });
  });
});
