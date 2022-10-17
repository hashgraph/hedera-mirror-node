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

import _ from 'lodash';

/**
 * Base service class that other services should inherit from for their retrieval business logic
 */
class BaseService {
  async buildWhereSqlStatementIncludingMultipleORs(whereQuery, params = []) {
    if (_.isEmpty(whereQuery)) {
      return {where: '', params};
    }

    let where = params.length === 0 ? 'where' : 'and';
    let grouped = _.groupBy(whereQuery, 'query');

    for (let [index, value] of Object.values(grouped).entries()) {
      if (value.length > 1) {
        let innerOr = '';
        for (let i = 0; i < value.length; i++) {
          params.push(value[i].param);
          innerOr += `${i === 0 ? 'and(' : 'or'} ${value[i].query} $${params.length}`;
        }
        where += innerOr + ')';
      } else {
        params.push(value[0].param);
        where += `${index === 0 ? '' : 'and'} ${value[0].query} $${params.length}`;
      }
    }

    return {where, params};
  }

  buildWhereSqlStatement(whereQuery, params = []) {
    if (_.isEmpty(whereQuery)) {
      return {where: '', params};
    }

    let where = params.length === 0 ? 'where' : 'and';
    for (let i = 0; i < whereQuery.length; i++) {
      const query = whereQuery[i];
      params.push(query.param);
      where += `${i === 0 ? '' : 'and'} ${query.query} $${params.length}`;
    }

    return {where, params};
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

  async getRows(query, params, functionName = '') {
    if (logger.isTraceEnabled()) {
      logger.trace(`${functionName} query: ${query}, params: ${params}`);
    }
    const {rows} = await pool.queryQuietly(query, params);
    if (logger.isTraceEnabled()) {
      logger.trace(`${functionName} ${rows.length} entries`);
    }
    return rows;
  }

  async getSingleRow(query, params, functionName = '') {
    const rows = await this.getRows(query, params, functionName);
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
}

export default BaseService;
