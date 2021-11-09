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

const {proto} = require('@hashgraph/proto/lib/proto');

// models
const {TransactionResult} = require('../../model');

describe('transactionResult constants are up to date', () => {
  test('transactionResult constants are up to date', () => {
    for (const responseCode in proto.ResponseCodeEnum) {
      if (isNaN(Number(responseCode))) {
        expect(TransactionResult.getProtoId(responseCode)).toBeTruthy();
      } else {
        expect(TransactionResult.getName(responseCode)).toBeTruthy();
      }
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

  test('getProtoId handles unknown', () => {
    expect(TransactionResult.getSuccessProtoId()).toEqual('22');
  });
});
