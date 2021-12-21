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

const proto = require('@hashgraph/proto');

// models
const {TransactionResult} = require('../../model');

describe('transactionResult constants are up to date', () => {
  describe('Name to ID', () => {
    for (const [name, id] of Object.entries(proto.ResponseCodeEnum)) {
      test(name, () => {
        expect(TransactionResult.getProtoId(name)).toEqual(`${id}`);
      });
    }
  });

  describe('ID to Name', () => {
    for (const [name, id] of Object.entries(proto.ResponseCodeEnum)) {
      test(`${id}`, () => {
        expect(TransactionResult.getName(id)).toEqual(name);
      });
    }
  });
});

describe('transactionResults getters work as expected', () => {
  test('getName handles unknown', () => {
    expect(TransactionResult.getName(9999999)).toEqual('UNKNOWN');
  });

  test('getProtoId handles unknown', () => {
    expect(TransactionResult.getProtoId('XYZ')).toBeFalsy();
  });

  test('getSuccessProtoId', () => {
    expect(TransactionResult.getSuccessProtoId()).toEqual('22');
  });
});
