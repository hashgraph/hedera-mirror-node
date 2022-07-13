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

import ContractResultViewModel from '../../viewmodel/contractResultViewModel';

describe('ContractResultViewModel', () => {
  const defaultContractResult = {
    amount: 10,
    bloom: Buffer.from([0x1, 0x2, 0x3, 0x4]),
    callResult: Buffer.from([0xa, 0xb, 0xc, 0xd]),
    consensusTimestamp: '900123456789',
    contractId: 1500,
    createdContractIds: [1501, 1502],
    errorMessage: 'unknown error',
    payerAccountId: 1100,
    functionParameters: Buffer.from([0x1, 0x2, 0x3, 0x4]),
    gasLimit: 6000,
    gasUsed: 3500,
  };
  const defaultExpected = {
    amount: 10,
    bloom: '0x01020304',
    call_result: '0x0a0b0c0d',
    contract_id: '0.0.1500',
    created_contract_ids: ['0.0.1501', '0.0.1502'],
    error_message: 'unknown error',
    from: '0x000000000000000000000000000000000000044c',
    function_parameters: '0x01020304',
    gas_limit: 6000,
    gas_used: 3500,
    hash: null,
    timestamp: '900.123456789',
    to: '0x00000000000000000000000000000000000005dc',
  };

  test('default', () => {
    expect(new ContractResultViewModel(defaultContractResult)).toEqual(defaultExpected);
  });

  test('null fields', () => {
    expect(
      new ContractResultViewModel({
        ...defaultContractResult,
        amount: null,
        bloom: null,
        callResult: null,
        contractId: null,
        createdContractIds: null,
        errorMessage: null,
        functionParameters: null,
        gasUsed: null,
      })
    ).toEqual({
      ...defaultExpected,
      amount: null,
      bloom: '0x',
      call_result: '0x',
      contract_id: null,
      created_contract_ids: [],
      error_message: null,
      function_parameters: '0x',
      gas_used: null,
      hash: null,
      to: null,
    });
  });
});
