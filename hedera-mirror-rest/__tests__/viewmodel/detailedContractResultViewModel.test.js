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

import {DetailedContractResultViewModel} from '../../viewmodel';

describe('DetailedContractResultViewModel', () => {
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
    transactionHash: Buffer.from([...Array(32).keys()]),
    accessList: Buffer.from([]),
    blockGasUsed: 400000,
    blockHash: '810be01ed512088a982113884e95a081f665f924473d1e29d70f3874981503f8a810fefcedd0f0bd5d5b6ac6cd20379f',
    blockNumber: 5644,
    chainId: Buffer.from([0x1, 0x2a]),
    gasPrice:  Buffer.from([]),
    maxFeePerGas: Buffer.from([0x59]),
    maxPriorityFeePerGas:  Buffer.from([]),
    etNonce: 5,
    r: Buffer.from([0xb5, 0xc2, 0x1a, 0xb4, 0xdf, 0xd3, 0x36, 0xe3, 0x0a, 0xc2, 0x10, 0x6c, 0xad, 0x4a, 0xa8, 0x88,
      0x8b, 0x18, 0x73, 0xa9, 0x9b, 0xce, 0x35, 0xd5, 0x0f, 0x64, 0xd2, 0xec, 0x2c, 0xc5, 0xf6, 0xd9]),
    s: Buffer.from([0x10, 0x92, 0x80, 0x6a, 0x99, 0x72, 0x7a, 0x20, 0xc3, 0x18, 0x36, 0x95, 0x91, 0x33, 0x30, 0x1b,
      0x65, 0xa2, 0xbf, 0xa9, 0x80, 0xf9, 0x79, 0x55, 0x22, 0xd2, 0x1a, 0x25, 0x4e, 0x62, 0x91, 0x10]),
    v: 1,
    transactionIndex: 33,
    transactionType: 2
  };
  const defaultExpected = {
    access_list: '0x',
    address: '0x00000000000000000000000000000000000005dc',
    amount: 10,
    block_gas_used: 400000,
    block_hash: '0x810be01ed512088a982113884e95a081f665f924473d1e29d70f3874981503f8a810fefcedd0f0bd5d5b6ac6cd20379f',
    block_number: 5644,
    bloom: '0x01020304',
    call_result: '0x0a0b0c0d',
    chain_id: '0x12a',
    contract_id: '0.0.1500',
    created_contract_ids: ['0.0.1501', '0.0.1502'],
    error_message: 'unknown error',
    from: '0x000000000000000000000000000000000000044c',
    function_parameters: '0x01020304',
    gas_limit: 6000,
    gas_price: '0x',
    gas_used: 3500,
    hash: '0x000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f',
    max_fee_per_gas: '0x59',
    max_priority_fee_per_gas: '0x',
    nonce: 5,
    r: '0xb5c21ab4dfd336e30ac2106cad4aa8888b1873a99bce35d50f64d2ec2cc5f6d9',
    s: '0x1092806a99727a20c31836959133301b65a2bfa980f9795522d21a254e629110',
    timestamp: '900.123456789',
    to: '0x00000000000000000000000000000000000005dc',
    transaction_index: 33,
    type: 2,
    v: 1
  };

  test('default', () => {
    expect(new DetailedContractResultViewModel(defaultContractResult)).toEqual(defaultExpected);
  });

  test('null fields', () => {
    expect(
      new DetailedContractResultViewModel({
        ...defaultContractResult,
        amount: null,
        bloom: null,
        callResult: null,
        contractId: null,
        createdContractIds: null,
        errorMessage: null,
        functionParameters: null,
        gasUsed: null,
        transactionHash: null,
      })
    ).toEqual({
      ...defaultExpected,
      address: null,
      amount: null,
      bloom: '0x',
      call_result: '0x',
      contract_id: null,
      created_contract_ids: [],
      error_message: null,
      function_parameters: '0x',
      gas_used: null,
      hash: '0x',
      to: null,
    });
  });
});
