/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

import _ from 'lodash';

/**
 * Base service class that other services should inherit from for their retrieval business logic
 */
class BaseService {
  buildWhereSqlStatement(whereQuery, params = []) {
    if (_.isEmpty(whereQuery)) {
      return {where: '', params};
    }

    const prefix = params.length === 0 ? 'where' : 'and';
    const condition = whereQuery
      .map((current) => {
        let position;
        if (Array.isArray(current.param)) {
          const first = params.length + 1;
          const last = params.push(...current.param);
          position = `(${_.range(first, last + 1).map((pos) => '$' + pos)})`;
        } else {
          params.push(current.param);
          position = `$${params.length}`;
        }
        return `${current.query} ${position}`;
      })
      .join(' and ');

    return {where: `${prefix} ${condition}`, params};
  }

  getLimitQuery(position) {
    return `limit $${position}`;
  }

  /**
   * Gets the order by query from the order specs
   *
   * @param {OrderSpec} orderSpecs
   * @return {string}
   */
  getOrderByQuery(...orderSpecs) {
    return 'order by ' + orderSpecs.map((spec) => `${spec}`).join(', ');
  }

  getFilterWhereCondition(key, filter) {
    return {
      query: `${key} ${filter.operator}`,
      param: filter.value,
    };
  }

  async getRows(query, params) {
    return (await this.pool().queryQuietly(query, params)).rows;
  }

  async getSingleRow(query, params) {
    const rows = await this.getRows(query, params);
    if (_.isEmpty(rows) || rows.length > 1) {
      return null;
    }

    return rows[0];
  }

  /**
   * Builds a standard sql query that can be extended with an additional filters
   *
   * @param {string} selectFromTable
   * @param {*[]} params
   * @param {string[]} conditions
   * @param {string} orderClause
   * @param {string} limitClause
   * @param {{key: string, operator: string, value: *, column: string}[]} filters
   * @returns {(string|*)[]} the build query and the parameters for the query
   */
  buildSelectQuery(selectFromTable, params, conditions, orderClause, limitClause, filters = []) {
    const whereConditions = [
      ...conditions,
      ...filters.map((filter) => {
        params.push(filter.value);
        return `${filter.column}${filter.operator}$${params.length}`;
      }),
    ];

    return [
      selectFromTable,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      orderClause,
      limitClause,
    ].join('\n');
  }

  pool() {
    return pool;
  }
}

export default BaseService;
