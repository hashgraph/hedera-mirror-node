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

import {TokenAccount, TokenBalance, AccountBalanceFile, Nft} from '../model';
import BaseService from './baseService';
import {OrderSpec} from '../sql';
import _ from 'lodash';

/**
 * Token retrieval business logic
 */
class TokenService extends BaseService {
  static tokenRelationshipsQuery = `
        select ${TokenAccount.getFullName(TokenAccount.AUTOMATIC_ASSOCIATION)},
               ${TokenAccount.getFullName(TokenAccount.CREATED_TIMESTAMP)},
               ${TokenAccount.getFullName(TokenAccount.FREEZE_STATUS)},
               ${TokenAccount.getFullName(TokenAccount.KYC_STATUS)},
               ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)},
               coalesce(${TokenBalance.getFullName(TokenBalance.BALANCE)}, 0) balance
        from ${TokenAccount.tableName} ${TokenAccount.tableAlias}
        left join (
                select ${TokenBalance.TOKEN_ID},
                       ${TokenBalance.BALANCE}
                from ${TokenBalance.tableName}
                where ${TokenBalance.ACCOUNT_ID} = $1
                and ${TokenBalance.CONSENSUS_TIMESTAMP} = (select max(${AccountBalanceFile.CONSENSUS_TIMESTAMP})
                from ${AccountBalanceFile.tableName})
        ) ${TokenBalance.tableAlias}
        on ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)} = ${TokenBalance.getFullName(TokenBalance.TOKEN_ID)}
        where ${TokenAccount.tableAlias}.${TokenAccount.ACCOUNT_ID} = $1
        and ${TokenAccount.tableAlias}.${TokenAccount.ASSOCIATED} = true `;

  /**
   * Gets the full sql query and params to retrieve an account's token relationships
   *
   * @param query
   * @return {{sqlQuery: string, params: *[]}}
   */
  getTokenRelationshipsQuery(query) {
    const {conditions, inConditions, order, ownerAccountId, limit} = query;
    const params = [ownerAccountId, limit];
    // This is the inner query to get the latest balance for a token, account pair.
    const moreConditionsExist = conditions.length > 0 ? ` and ` : ``;
    const conditionClause =
      moreConditionsExist +
      conditions
        .map((condition) => {
          params.push(condition.value);
          return `${TokenAccount.getFullName(condition.key)} ${condition.operator} $${params.length}`;
        })
        .join(' and ');
    let inConditionClause = this.getInClauseSubQuery(inConditions, params);
    const limitClause = super.getLimitQuery(2);
    const orderClause = this.getOrderByQuery(OrderSpec.from(TokenAccount.getFullName(TokenAccount.TOKEN_ID), order));
    const sqlQuery = [
      TokenService.tokenRelationshipsQuery,
      conditionClause,
      inConditionClause,
      orderClause,
      limitClause,
    ].join('\n');
    return {sqlQuery, params};
  }

  getInClauseSubQuery(inConditions, params) {
    const tokenIdInParams = [];
    inConditions.map((condition) => {
      tokenIdInParams.push(condition.value);
    });

    if (!_.isEmpty(tokenIdInParams)) {
      return ` and ${TokenAccount.getFullName(TokenAccount.TOKEN_ID)} in (${tokenIdInParams})`;
    }
  }
  /**
   * Gets the tokens for the query
   *
   * @param query
   * @return {Promise<Token[]>}
   */
  async getTokens(query) {
    const {sqlQuery, params} = this.getTokenRelationshipsQuery(query);
    const rows = await super.getRows(sqlQuery, params, 'getTokens');
    return rows.map((ta) => new TokenAccount(ta));
  }
}

export default new TokenService();
