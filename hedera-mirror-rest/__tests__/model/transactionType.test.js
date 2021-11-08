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
const {TransactionType, TransactionResult} = require('../../model');
const {InvalidArgumentError} = require('../../errors/invalidArgumentError');
const {proto} = require('@hashgraph/proto/lib/proto');

const hederaFunctionalityLength = 64;

describe('getName', () => {
  test('Return valid name', () => {
    expect(TransactionType.getName(11)).toEqual('CRYPTOCREATEACCOUNT');
  });
  test('Return UNKNOWN for future proto id', () => {
    expect(TransactionType.getName(9999999)).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for invalid proto id', () => {
    expect(TransactionType.getName('')).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for null proto id', () => {
    expect(TransactionType.getName(null)).toEqual('UNKNOWN');
  });
  test('Return UNKNOWN for undefined proto id', () => {
    expect(TransactionType.getName(undefined)).toEqual('UNKNOWN');
  });
});

describe('getProtoId', () => {
  test('Return valid proto id', () => {
    expect(TransactionType.getProtoId('CRYPTOCREATEACCOUNT')).toEqual('11');
  });
  test('Return valid proto id for camel case', () => {
    expect(TransactionType.getProtoId('cryptoCreateAccount')).toEqual('11');
  });
  test('Throw error for invalid name', () => {
    expect(() => {
      TransactionType.getProtoId(22);
    }).toThrowError(InvalidArgumentError);
  });
  test('Throw error for unknown name', () => {
    expect(() => {
      TransactionType.getProtoId('UNKNOWN');
    }).toThrowError(InvalidArgumentError);
  });
  test('Throw error for null name', () => {
    expect(() => {
      TransactionType.getProtoId(null);
    }).toThrowError(InvalidArgumentError);
  });
  test('Throw error for undefined name', () => {
    expect(() => {
      TransactionType.getProtoId(undefined);
    }).toThrowError(InvalidArgumentError);
  });
});

describe('isValid', () => {
  test('Return valid proto id', () => {
    expect(TransactionType.isValid('CRYPTOCREATEACCOUNT')).toBeTruthy();
  });
  test('Return valid proto id for camel case', () => {
    expect(TransactionType.isValid('cryptoCreateAccount')).toBeTruthy();
  });
  test('Throw error for invalid name', () => {
    expect(TransactionType.isValid(22)).toBeFalsy();
  });
  test('Throw error for unknown name', () => {
    expect(TransactionType.isValid('')).toBeFalsy();
  });
  test('Throw error for null name', () => {
    expect(TransactionType.isValid(null)).toBeFalsy();
  });
  test('Throw error for undefined name', () => {
    expect(TransactionType.isValid(undefined)).toBeFalsy();
  });
});
describe('transactionType constants are up to date', () => {
  //There isn't a dedicate enum for TransactionBody values, so just check that no new HederaFunctionality exists
  test('transactionType have new values been added', () => {
    //Last entry is TokenFeeScheduleUpdate: 77
    expect(Object.keys(proto.HederaFunctionality).length).toEqual(hederaFunctionalityLength);
  });
});
