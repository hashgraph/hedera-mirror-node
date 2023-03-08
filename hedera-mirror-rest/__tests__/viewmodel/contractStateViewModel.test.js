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

import {ContractStateViewModel} from '../../viewmodel';

describe('ContractStateViewModel', () => {
  const defaultContractState = {
    contractId: 1500,
    modifiedTimestamp: 1651770056616171000,
    slot: Buffer.from([0x1]),
    value: Buffer.from([0x1]),
  };

  const defaultExpected = {
    contract_id: '0.0.1500',
    timestamp: '1651770056.616171000',
    address: '0x00000000000000000000000000000000000005dc',
    slot: '0x0000000000000000000000000000000000000000000000000000000000000001',
    value: '0x0000000000000000000000000000000000000000000000000000000000000001',
  };

  test('default', () => {
    expect(new ContractStateViewModel(defaultContractState)).toEqual(defaultExpected);
  });
});
