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
    select ${StakingRewardTransfer.getFullName(StakingRewardTransfer.ACCOUNT_ID)},
    ${StakingRewardTransfer.getFullName(StakingRewardTransfer.AMOUNT)},
    ${StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP)}
    from ${StakingRewardTransfer.tableName} ${StakingRewardTransfer.tableAlias}`;

  async getRewards(accountId, order, limit, conditions, initParams) {
    const {query, params} = this.getRewardsQuery(accountId, order, limit, conditions, initParams);
    const rows = await super.getRows(query, params, 'getRewards');
    return rows.map((srt) => new StakingRewardTransfer(srt));
  }

  getRewardsQuery(accountId, order, limit, whereConditions, whereParams) {
    const params = Array.from(whereParams);
    if (params.length == 0) {
      params.push(accountId);
    }
    params.push(limit);
    const conditionsArray = Array.from(whereConditions);
    // if there is a "key" element inside the where condition, parse it accordingly.
    //  Otherwise, just take the where condition's string value as a pre-parsed "where"-like clause.
    const conditions = conditionsArray.map((condition) => conditionToWhereClause(condition));
    conditions.unshift(`${StakingRewardTransfer.getFullName(StakingRewardTransfer.ACCOUNT_ID)}` + ' = $1');
    const query = [
      StakingRewardTransferService.listStakingRewardsByAccountIdQuery,
      conditions.length > 0 ? `where ${conditions.join(' and ')}` : '',
      super.getOrderByQuery(
        OrderSpec.from(`${StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP)}`, order)
      ),
      super.getLimitQuery(params.length),
    ].join('\n');

    return {query, params};
  }

  conditionToWhereClause(condition) {
    if (condition.key) {
      if (condition.key === 'timestamp') {
        return `${StakingRewardTransfer.getFullName(StakingRewardTransfer.CONSENSUS_TIMESTAMP)} ${condition.operator} ${
          condition.value
        }`;
      }
      return `${condition.key} ${condition.operator} ${condition.value}`;
    }
    return condition;
  }
}

export default new StakingRewardTransferService();
