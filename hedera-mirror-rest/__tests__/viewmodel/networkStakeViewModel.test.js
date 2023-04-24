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

import {NetworkStakeViewModel} from '../../viewmodel';

describe('NetworkStakeViewModel', () => {
  const defaultNetworkStake = {
    maxStakingRewardRatePerHbar: 17808,
    nodeRewardFeeDenominator: 0,
    nodeRewardFeeNumerator: 100,
    stakeTotal: '35000000000000000',
    stakingPeriod: '1654991999999999999',
    stakingPeriodDuration: 1440,
    stakingPeriodsStored: 365,
    stakingRewardFeeDenominator: 100,
    stakingRewardFeeNumerator: 10,
    stakingRewardRate: '100000000000',
    stakingStartThreshold: '25000000000000000',
  };

  const defaultExpected = {
    max_staking_reward_rate_per_hbar: 17808,
    node_reward_fee_fraction: 0.0,
    stake_total: '35000000000000000',
    staking_period: {
      from: '1654992000.000000000',
      to: '1655078400.000000000',
    },
    staking_period_duration: 1440,
    staking_periods_stored: 365,
    staking_reward_fee_fraction: 0.1,
    staking_reward_rate: '100000000000',
    staking_start_threshold: '25000000000000000',
  };

  test('default', () => {
    expect(new NetworkStakeViewModel(defaultNetworkStake)).toEqual(defaultExpected);
  });

  test('null fields', () => {
    expect(
      new NetworkStakeViewModel({
        maxStakingRewardRatePerHbar: null,
        nodeRewardFeeDenominator: 0,
        nodeRewardFeeNumerator: 0,
        stakeTotal: null,
        stakingPeriod: null,
        stakingPeriodDuration: null,
        stakingPeriodsStored: null,
        stakingRewardFeeDenominator: 0,
        stakingRewardFeeNumerator: 0,
        stakingRewardRate: null,
        stakingStartThreshold: null,
      })
    ).toEqual({
      max_staking_reward_rate_per_hbar: null,
      node_reward_fee_fraction: 0.0,
      stake_total: null,
      staking_period: null,
      staking_period_duration: null,
      staking_periods_stored: null,
      staking_reward_fee_fraction: 0.0,
      staking_reward_rate: null,
      staking_start_threshold: null,
    });
  });
});
