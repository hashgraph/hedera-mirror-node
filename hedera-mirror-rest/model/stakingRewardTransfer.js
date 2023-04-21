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

class StakingRewardTransfer {
  /**
   * Parses staking_reward_transfer table columns into object
   */
  constructor(stakingRewardTransfer) {
    this.accountId = stakingRewardTransfer.account_id;
    this.amount = stakingRewardTransfer.amount;
    this.consensusTimestamp = stakingRewardTransfer.consensus_timestamp;
    this.payerAccountId = stakingRewardTransfer.payer_account_id;
  }

  static tableAlias = 'srt';
  static tableName = 'staking_reward_transfer';

  static ACCOUNT_ID = 'account_id';
  static AMOUNT = 'amount';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static STAKING_REWARD_ACCOUNT = 800;

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

export default StakingRewardTransfer;
