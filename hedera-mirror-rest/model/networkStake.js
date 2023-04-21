/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

class NetworkStake {
  /**
   * Parses node_stake table columns into object
   */
  constructor(networkStake) {
    this.maxStakingRewardRatePerHbar = networkStake.max_staking_reward_rate_per_hbar;
    this.nodeRewardFeeDenominator = networkStake.node_reward_fee_denominator;
    this.nodeRewardFeeNumerator = networkStake.node_reward_fee_numerator;
    this.stakeTotal = networkStake.stake_total;
    this.stakingPeriod = networkStake.staking_period;
    this.stakingPeriodDuration = networkStake.staking_period_duration;
    this.stakingPeriodsStored = networkStake.staking_periods_stored;
    this.stakingRewardFeeDenominator = networkStake.staking_reward_fee_denominator;
    this.stakingRewardFeeNumerator = networkStake.staking_reward_fee_numerator;
    this.stakingRewardRate = networkStake.staking_reward_rate;
    this.stakingStartThreshold = networkStake.staking_start_threshold;
  }

  static tableAlias = 'ns';
  static tableName = 'network_stake';

  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static EPOCH_DAY = `epoch_day`;
  static MAX_STAKING_REWARD_RATE_PER_HBAR = `max_staking_reward_rate_per_hbar`;
  static NODE_REWARD_FEE_DENOMINATOR = `node_reward_fee_denominator`;
  static NODE_REWARD_FEE_NUMERATOR = `node_reward_fee_numerator`;
  static STAKE_TOTAL = `stake_total`;
  static STAKING_PERIOD = `staking_period`;
  static STAKING_PERIOD_DURATION = `staking_period_duration`;
  static STAKING_PERIODS_STORED = `staking_periods_stored`;
  static STAKING_REWARD_FEE_DENOMINATOR = `staking_reward_fee_denominator`;
  static STAKING_REWARD_FEE_NUMERATOR = `staking_reward_fee_numerator`;
  static STAKING_REWARD_RATE = `staking_reward_rate`;
  static STAKING_START_THRESHOLD = `staking_start_threshold`;

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default NetworkStake;
