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

const {ContractLogViewModel} = require('../../viewmodel');
const ContractViewModel = require('../../viewmodel/contractViewModel');
const {Range} = require('pg-range');

describe('ContractLogViewModel', () => {
  const hexArray = Array(18).fill(0x00).concat(0x12, 0x34);
  // bloomArray.push(0x12, 0x34);
  const defaultContractLog = {
    contractId: '1',
    //TODO update bloom
    bloom: Buffer.from(hexArray),
    data: Buffer.from(hexArray),
    consensusTimestamp: '99999999000000000',
    topics: [
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    ],
  };
  const defaultExpected = {
    address: '0x0000000000000000000000000000000000000001',
    bloom: '0x0000000000000000000000000000000000001234',
    data: '0x0000000000000000000000000000000000001234',
    timestamp: '99999999.000000000',
    topics: [
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
      'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    ],
  };

  test('default', () => {
    expect(new ContractLogViewModel(defaultContractLog)).toEqual(defaultExpected);
  });

  test('no topics', () => {
    expect(
      new ContractLogViewModel({
        ...defaultContractLog,
        topics: [],
      })
    ).toEqual({
      ...defaultExpected,
      topics: [],
    });
  });
});
