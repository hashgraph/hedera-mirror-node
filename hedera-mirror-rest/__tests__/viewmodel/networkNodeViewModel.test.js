/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {NetworkNodeViewModel} from '../../viewmodel';

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
      maxStake: 1000,
      minStake: 6,
      rewardRate: 7,
      stake: 12,
      stakeNotRewarded: 8,
      stakeRewarded: 4,
      stakingPeriod: '1654991999999999999',
    },
  };
  const defaultExpected = {
    description: 'desc 1',
    file_id: '0.0.102',
    max_stake: 1000,
    memo: '0.0.3',
    min_stake: 6,
    node_account_id: '0.0.3',
    node_cert_hash:
      '0x01d173753810c0aae794ba72d5443c292e9ff962b01046220dd99f5816422696e0569c977e2f169e1e5688afc8f4aa16',
    node_id: 0,
    public_key: '0x4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f',
    reward_rate_start: 7,
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
    stake_not_rewarded: 8,
    stake_rewarded: 4,
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
          maxStake: null,
          minStake: null,
          rewardRate: null,
          stake: null,
          stakeNotRewarded: null,
          stakeRewarded: null,
          stakingPeriod: null,
        },
      })
    ).toEqual({
      ...defaultExpected,
      description: null,
      max_stake: null,
      memo: null,
      min_stake: null,
      node_cert_hash: '0x',
      public_key: '0x',
      reward_rate_start: null,
      stake: null,
      stake_not_rewarded: null,
      stake_rewarded: null,
      staking_period: null,
    });
  });

  test('maxStake minStake stakeNotRewarded with default value -1', () => {
    const nodeStake = {...defaultNetworkNode.nodeStake, maxStake: -1, minStake: -1, stakeNotRewarded: -1};
    expect(
      new NetworkNodeViewModel({
        ...defaultNetworkNode,
        nodeStake,
      })
    ).toEqual({
      ...defaultExpected,
      max_stake: null,
      min_stake: null,
      stake_not_rewarded: null,
    });
  });
});
