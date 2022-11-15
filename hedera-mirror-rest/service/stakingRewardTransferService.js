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

import BaseService from './baseService';
import {StakingRewardTransfer} from '../model';
import {OrderSpec} from '../sql';

/**
 * Staking Reward Transfer retrieval business logic
 */
class StakingRewardTransferService extends BaseService {
  constructor() {
    super();
  }

  static listStakingRewardsByAccountIdQuery = `
    select ${StakingRewardTransfer.ACCOUNT_ID},
    ${StakingRewardTransfer.AMOUNT},
    ${StakingRewardTransfer.CONSENSUS_TIMESTAMP}
    from ${StakingRewardTransfer.tableName}
    where ${StakingRewardTransfer.ACCOUNT_ID} = $1`;

  async getRewards(accountId, order, limit, conditions, initParams) {
    const {query, params} = this.getRewardsQuery(accountId, order, limit, conditions, initParams);
    const rows = await super.getRows(query, params, 'getRewards');
    return rows.map((srt) => new StakingRewardTransfer(srt));
  }

  conditionToWhereClause(condition) {
    if (condition.key) {
      if (condition.key === 'timestamp') {
        return `${StakingRewardTransfer.CONSENSUS_TIMESTAMP} ${condition.operator} ${condition.value}`;
      }
      return `${condition.key} ${condition.operator} ${condition.value}`;
    }
    return condition;
  }

  getRewardsQuery(accountId, order, limit, whereConditions, whereParams) {
    const params = Array.from(whereParams);
    const conditionsArray = Array.from(whereConditions);
    // if there is a "key" element inside the where condition, parse it accordingly.
    //  Otherwise, just take the where condition's string value as a pre-parsed "where"-like clause.
    const conditions = conditionsArray.map((condition) => this.conditionToWhereClause(condition));
    const query = [
      StakingRewardTransferService.listStakingRewardsByAccountIdQuery,
      conditions.length > 0 ? `and ${conditions.join(' and ')}` : '', // "and" since we already have "where account_id = $1" at the end of the above line
      super.getOrderByQuery(OrderSpec.from(StakingRewardTransfer.CONSENSUS_TIMESTAMP, order)),
      super.getLimitQuery(2), // limit is specified in $2 (not necessarily a limit *of* 2)
    ].join('\n');

    return {query, params};
  }
}

export default new StakingRewardTransferService();
