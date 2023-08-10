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

import BaseService from './baseService';
import {filterKeys} from '../constants';
import {TokenAllowance} from '../model';
import {OrderSpec} from '../sql';

/**
 * TokenAllowance business model
 */
class TokenAllowanceService extends BaseService {
  static accountTokenAllowanceQuery = `select * from ${TokenAllowance.tableName}`;
  static amountCondition = `${TokenAllowance.AMOUNT} > 0`;
  static columns = {
    [filterKeys.SPENDER_ID]: TokenAllowance.SPENDER,
    [filterKeys.TOKEN_ID]: TokenAllowance.TOKEN_ID,
  };
  static orderByColumns = [TokenAllowance.SPENDER, TokenAllowance.TOKEN_ID];

  /**
   * Gets the subquery to retrieve the token allowance based on the filters for an owner account
   *
   * @param {{key: string, operator: string, value: *}[]} filters
   * @param {*[]} params
   * @param {string} accountIdCondition
   * @param {string} limitClause
   * @param {string} orderClause
   * @return {string}
   */
  getSubQuery(filters, params, accountIdCondition, limitClause, orderClause) {
    const conditions = [
      accountIdCondition,
      TokenAllowanceService.amountCondition,
      ...filters.map((filter) => {
        params.push(filter.value);
        const column = TokenAllowanceService.columns[filter.key];
        return `${column}${filter.operator}$${params.length}`;
      }),
    ];
    return [
      TokenAllowanceService.accountTokenAllowanceQuery,
      `where ${conditions.join(' and ')}`,
      orderClause,
      limitClause,
    ].join('\n');
  }

  /**
   * Gets the full sql query and params to retrieve an account's token allowances
   *
   * @param query
   * @return {{sqlQuery: string, params: *[]}}
   */
  getQuery(query) {
    const {lower, inner, upper, order, ownerAccountId, limit} = query;
    const params = [ownerAccountId, limit];
    const accountIdCondition = `${TokenAllowance.OWNER} = $1`;
    const limitClause = super.getLimitQuery(2);
    const orderClause = super.getOrderByQuery(
      ...TokenAllowanceService.orderByColumns.map((column) => OrderSpec.from(column, order))
    );

    const subQueries = [lower, inner, upper]
      .filter((filters) => filters.length !== 0)
      .map((filters) => this.getSubQuery(filters, params, accountIdCondition, limitClause, orderClause));

    let sqlQuery;
    if (subQueries.length === 0) {
      // if all three filters are empty, the subqueries will be empty too, just create the query with empty filters
      sqlQuery = this.getSubQuery([], params, accountIdCondition, limitClause, orderClause);
    } else if (subQueries.length === 1) {
      sqlQuery = subQueries[0];
    } else {
      sqlQuery = [subQueries.map((q) => `(${q})`).join('\nunion all\n'), orderClause, limitClause].join('\n');
    }

    return {sqlQuery, params};
  }

  /**
   * Gets the token allowances for the query
   *
   * @param query
   * @return {Promise<TokenAllowance[]>}
   */
  async getAccountTokenAllowances(query) {
    const {sqlQuery, params} = this.getQuery(query);
    const rows = await super.getRows(sqlQuery, params, 'getAccountTokenAllowances');
    return rows.map((ta) => new TokenAllowance(ta));
  }
}

export default new TokenAllowanceService();
