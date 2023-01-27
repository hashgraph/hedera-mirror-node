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

class NodeStake {
  /**
   * Parses node_stake table columns into object
   */
  constructor(nodeStake) {
    this.consensusTimestamp = nodeStake.consensus_timestamp;
    this.epochDay = nodeStake.epoch_day;
    this.maxStake = nodeStake.max_stake;
    this.minStake = nodeStake.min_stake;
    this.nodeId = nodeStake.node_id;
    this.rewardRate = nodeStake.reward_rate;
    this.stake = nodeStake.stake;
    this.stakeNotRewarded = nodeStake.stake_not_rewarded;
    this.stakeRewarded = nodeStake.stake_rewarded;
    this.stakingPeriod = nodeStake.staking_period;
  }

  static tableAlias = 'ns';
  static tableName = 'node_stake';

  static CONSENSUS_TIMESTAMP = `consensus_timestamp`;
  static EPOCH_DAY = `epoch_day`;
  static MAX_STAKE = `max_stake`;
  static MIN_STAKE = `min_stake`;
  static NODE_ID = `node_id`;
  static REWARD_RATE = `reward_rate`;
  static STAKE = `stake`;
  static STAKE_NOT_REWARDED = `stake_not_rewarded`;
  static STAKE_REWARDED = `stake_rewarded`;
  static STAKING_PERIOD = `staking_period`;

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

export default NodeStake;
