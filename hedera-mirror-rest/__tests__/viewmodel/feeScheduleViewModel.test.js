/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

'use strict';

import FeeScheduleViewModel from '../../viewmodel/feeScheduleViewModel';

describe('FeeScheduleViewModel', () => {
  const exchangeRate = {
    current_cent: 450041,
    current_expiration: 1651762800,
    current_hbar: 30000,
    next_cent: 435305,
    next_expiration: 1651766400,
    next_hbar: 30000,
    timestamp: 1653644164060000000,
  };
  const txFees = {
    EthereumTransaction: {
      fees: [{servicedata: {gas: {toNumber: () => 853000}}}],
      hederaFunctionality: 84,
    },
    ContractCall: {
      fees: [{servicedata: {gas: {toNumber: () => 741000}}}],
      hederaFunctionality: 6,
    },
    ContractCreate: {
      fees: [{servicedata: {gas: {toNumber: () => 990000}}}],
      hederaFunctionality: 7,
    },
  };
  const feeSchedule = {
    timestamp: 1653644164060000000,
    current_feeSchedule: Object.values(txFees),
    next_feeSchedule: [],
  };

  const feesResult = {
    EthereumTransaction: {
      gas: 57,
      transaction_type: 'EthereumTransaction',
    },
    ContractCreate: {
      gas: 66,
      transaction_type: 'ContractCreate',
    },
    ContractCall: {
      gas: 49,
      transaction_type: 'ContractCall',
    },
  };

  test('default asc', () => {
    expect(new FeeScheduleViewModel(feeSchedule, exchangeRate, 'asc')).toEqual({
      fees: [feesResult.ContractCall, feesResult.ContractCreate, feesResult.EthereumTransaction],
      timestamp: '1653644164.060000000',
    });
  });

  test('default desc', () => {
    expect(new FeeScheduleViewModel(feeSchedule, exchangeRate, 'desc')).toEqual({
      fees: [feesResult.EthereumTransaction, feesResult.ContractCreate, feesResult.ContractCall],
      timestamp: '1653644164.060000000',
    });
  });

  test('EthereumTransaction has no fees prop', () => {
    const EthereumTransaction = {...txFees.EthereumTransaction};
    delete EthereumTransaction.fees[0];

    expect(
      new FeeScheduleViewModel(
        {
          ...feeSchedule,
          current_feeSchedule: [EthereumTransaction, txFees.ContractCall, txFees.ContractCreate],
        },
        exchangeRate,
        'desc'
      )
    ).toEqual({
      fees: [feesResult.ContractCreate, feesResult.ContractCall],
      timestamp: '1653644164.060000000',
    });
  });
});
