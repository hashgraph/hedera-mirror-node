/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import {ContractLogViewModel} from '../../viewmodel';

describe('ContractLogViewModel', () => {
  const hexArray = Array(18).fill(0x00).concat(0x12, 0x34);
  const defaultContractLog = {
    bloom: Buffer.from([0xa, 0xb]),
    contractId: '1',
    data: Buffer.from(hexArray),
    consensusTimestamp: '99999999000000000',
    index: 6,
    rootContractId: '2',
    topic0: Buffer.from([0x01, 0x01]),
    topic1: Buffer.from([0x01, 0x02]),
    topic2: Buffer.from([0x01, 0x03]),
    topic3: Buffer.from([0x01, 0x04]),
  };
  const defaultExpected = {
    address: '0x0000000000000000000000000000000000000001',
    bloom: '0x0a0b',
    contract_id: '0.0.1',
    data: '0x0000000000000000000000000000000000001234',
    index: 6,
    root_contract_id: '0.0.2',
    timestamp: '99999999.000000000',
    topics: [
      '0x0000000000000000000000000000000000000000000000000000000000000101',
      '0x0000000000000000000000000000000000000000000000000000000000000102',
      '0x0000000000000000000000000000000000000000000000000000000000000103',
      '0x0000000000000000000000000000000000000000000000000000000000000104',
    ],
  };

  test('ContractLogViewModel - default', () => {
    expect(new ContractLogViewModel(defaultContractLog)).toEqual(defaultExpected);
  });

  test('ContractLogViewModel - no topics', () => {
    expect(
      new ContractLogViewModel({
        ...defaultContractLog,
        topic0: null,
        topic1: null,
        topic2: null,
        topic3: null,
      })
    ).toEqual({
      ...defaultExpected,
      topics: [],
    });
  });

  test('ContractLogViewModel - no root contract id', () => {
    expect(
      new ContractLogViewModel({
        ...defaultContractLog,
        rootContractId: null,
      })
    ).toEqual({
      ...defaultExpected,
      root_contract_id: null,
    });
  });
});
