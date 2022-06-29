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

'use strict';

const _ = require('lodash');

const {Nft} = require('../model');
const BaseService = require('./baseService');
const {OrderSpec} = require('../sql');
const constants = require('../constants');

/**
 * Nft business model
 */
class NftService extends BaseService {
  static columns = {
    [constants.filterKeys.TOKEN_ID]: Nft.TOKEN_ID,
    [constants.filterKeys.SERIAL_NUMBER]: Nft.SERIAL_NUMBER,
    [constants.filterKeys.SPENDER_ID]: Nft.SPENDER,
  };

  static nftByIdQuery = `select * from nft where ${Nft.TOKEN_ID} = $1 and ${Nft.SERIAL_NUMBER} = $2`;

  static nftQuery = `select
    ${Nft.ACCOUNT_ID},
    ${Nft.CREATED_TIMESTAMP},
    ${Nft.DELEGATING_SPENDER},
    ${Nft.DELETED},
    ${Nft.METADATA},
    ${Nft.MODIFIED_TIMESTAMP},
    ${Nft.SERIAL_NUMBER},
    ${Nft.SPENDER},
    ${Nft.TOKEN_ID}
    from ${Nft.tableName}`;

  static orderByColumns = [Nft.TOKEN_ID, Nft.SERIAL_NUMBER];

  async getNft(tokenId, serialNumber) {
    const {rows} = await pool.queryQuietly(NftService.nftByIdQuery, [tokenId, serialNumber]);
    return _.isEmpty(rows) ? null : new Nft(rows[0]);
  }

  /**
   * Gets the subquery to retrieve the nfts based on the filters and conditions
   *
   * @param {{key: string, operator: string, value: *}[]} filters
   * @param {*[]} params
   * @param {string} accountIdCondition
   * @param {string} limitClause
   * @param {string} orderClause
   * @param {{key: string, operator: string, value: *}[]} spenderIdInFilters
   * @param {{key: string, operator: string, value: *}[]} spenderIdFilters
   * @return {string}
   */
  getSubQuery(filters, params, accountIdCondition, limitClause, orderClause, spenderIdInFilters, spenderIdFilters) {
    filters.push(...spenderIdFilters);
    const conditions = [
      accountIdCondition,
      ...filters.map((filter) => {
        params.push(filter.value);
        const column = NftService.columns[filter.key];
        return `${column}${filter.operator}$${params.length}`;
      }),
    ];

    if (!_.isEmpty(spenderIdInFilters)) {
      const paramsForCondition = spenderIdInFilters.map((filter) => {
        params.push(filter.value);
        return `$${params.length}`;
      });

      conditions.push(`${Nft.SPENDER} in (${paramsForCondition})`);
    }

    return [NftService.nftQuery, `where ${conditions.join(' and ')}`, orderClause, limitClause].join('\n');
  }

  /**
   * Gets the full sql query and params
   *
   * @param query
   * @return {{sqlQuery: string, params: *[]}}
   */
  getQuery(query) {
    const {lower, inner, upper, order, ownerAccountId, limit, spenderIdInFilters, spenderIdFilters} = query;
    const params = [ownerAccountId, limit];
    const accountIdCondition = `${Nft.ACCOUNT_ID} = $1`;
    const limitClause = super.getLimitQuery(2);
    const orderClause = super.getOrderByQuery(
      ...NftService.orderByColumns.map((column) => OrderSpec.from(column, order))
    );

    const subQueries = [lower, inner, upper]
      .filter((filters) => filters.length !== 0)
      .map((filters) =>
        this.getSubQuery(
          filters,
          params,
          accountIdCondition,
          limitClause,
          orderClause,
          spenderIdInFilters,
          spenderIdFilters
        )
      );

    let sqlQuery;
    if (subQueries.length === 0) {
      // if all three filters are empty, the subqueries will be empty too, just create the query with empty filters
      sqlQuery = this.getSubQuery(
        [],
        params,
        accountIdCondition,
        limitClause,
        orderClause,
        spenderIdInFilters,
        spenderIdFilters
      );
    } else if (subQueries.length === 1) {
      sqlQuery = subQueries[0];
    } else {
      sqlQuery = [subQueries.map((q) => `(${q})`).join('\nunion all\n'), orderClause, limitClause].join('\n');
    }

    return {sqlQuery, params};
  }

  /**
   * Gets the nfts for the query
   *
   * @param query
   * @return {Promise<Nft[]>}
   */
  async getNfts(query) {
    const {sqlQuery, params} = this.getQuery(query);
    const rows = await super.getRows(sqlQuery, params, 'getNfts');
    return rows.map((ta) => new Nft(ta));
  }
}

module.exports = new NftService();
