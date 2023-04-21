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

import * as utils from '../utils';
import * as math from 'mathjs';

/**
 * Network stake view model
 */
class NetworkStakeViewModel {
  /**
   * Constructs network stake view model
   *
   * @param {NetworkStake} networkStake
   */
  constructor(networkStake) {
    this.max_staking_reward_rate_per_hbar = networkStake.maxStakingRewardRatePerHbar;
    this.node_reward_fee_fraction = this.calculateFeeFraction(
      networkStake.nodeRewardFeeNumerator,
      networkStake.nodeRewardFeeDenominator
    );
    this.stake_total = networkStake.stakeTotal;
    this.staking_period = utils.getStakingPeriod(networkStake.stakingPeriod);
    this.staking_period_duration = networkStake.stakingPeriodDuration;
    this.staking_periods_stored = networkStake.stakingPeriodsStored;
    this.staking_reward_fee_fraction = this.calculateFeeFraction(
      networkStake.stakingRewardFeeNumerator,
      networkStake.stakingRewardFeeDenominator
    );
    this.staking_reward_rate = networkStake.stakingRewardRate;
    this.staking_start_threshold = networkStake.stakingStartThreshold;
  }

  calculateFeeFraction(feeNumerator, feeDenominator) {
    return feeDenominator !== 0
      ? math.divide(math.bignumber(feeNumerator), math.bignumber(feeDenominator)).toNumber()
      : 0;
  }
}

export default NetworkStakeViewModel;
