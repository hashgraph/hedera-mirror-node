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

const NetworkNodeViewModel = require('../../viewmodel/networkNodeViewModel');

describe('NetworkNodeViewModel', () => {
  const defaultNetworkNode = {
    addressBook: {
      endConsensusTimestamp: null,
      fileId: 102,
      startConsensusTimestamp: '187654000123457',
    },
    addressBookEntry: {
      description: 'desc 1',
      memo: '0.0.3',
      nodeAccountId: 3,
      nodeCertHash: Buffer.from(
        '01d173753810c0aae794ba72d5443c292e9ff962b01046220dd99f5816422696e0569c977e2f169e1e5688afc8f4aa16'
      ),
      nodeId: 0,
      publicKey: '4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    },
    addressBookServiceEndpoints: [
      {
        ipAddressV4: '128.0.0.1',
        port: 50211,
      },
      {
        ipAddressV4: '128.0.0.2',
        port: 50212,
      },
    ],
    nodeStake: {
      stake: 12,
      stakeRewarded: 4,
      stakeTotal: 6,
      stakingPeriod: '1654991999999999999',
    },
  };
  const defaultExpected = {
    description: 'desc 1',
    file_id: '0.0.102',
    memo: '0.0.3',
    node_account_id: '0.0.3',
    node_cert_hash:
      '0x01d173753810c0aae794ba72d5443c292e9ff962b01046220dd99f5816422696e0569c977e2f169e1e5688afc8f4aa16',
    node_id: 0,
    public_key: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    service_endpoints: [
      {
        ip_address_v4: '128.0.0.1',
        port: 50211,
      },
      {
        ip_address_v4: '128.0.0.2',
        port: 50212,
      },
    ],
    stake: 12,
    stake_rewarded: 4,
    stake_total: 6,
    staking_period: {
      from: '1654992000.000000000',
      to: '1655078400.000000000',
    },
    timestamp: {
      from: '187654.000123457',
      to: null,
    },
  };

  test('default', () => {
    expect(new NetworkNodeViewModel(defaultNetworkNode)).toEqual(defaultExpected);
  });

  test('null fields', () => {
    expect(
      new NetworkNodeViewModel({
        ...defaultNetworkNode,
        addressBookEntry: {
          description: null,
          memo: null,
          nodeAccountId: 3,
          nodeCertHash: null,
          nodeId: 0,
          publicKey: null,
        },
        nodeStake: {
          stake: null,
          stakeRewarded: null,
          stakeTotal: null,
          stakingPeriod: null,
        },
      })
    ).toEqual({
      ...defaultExpected,
      description: null,
      memo: null,
      node_cert_hash: '0x',
      public_key: '0x',
      stake: null,
      stake_rewarded: null,
      stake_total: null,
      staking_period: null,
    });
  });
});
