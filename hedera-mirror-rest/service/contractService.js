/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const BaseService = require('./baseService');
const {ContractResult, ContractLog} = require('../model');
const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const {orderFilterValues} = require('../constants');

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  constructor() {
    super();
  }

  static detailedContractResultsQuery = `select *
  from ${ContractResult.tableName} ${ContractResult.tableAlias}`;

  static contractResultsQuery = `select
    ${ContractResult.AMOUNT},
    ${ContractResult.CALL_RESULT},
    ${ContractResult.CONSENSUS_TIMESTAMP},
    ${ContractResult.CONTRACT_ID},
    ${ContractResult.CREATED_CONTRACT_IDS},
    ${ContractResult.ERROR_MESSAGE},
    ${ContractResult.FUNCTION_PARAMETERS},
    ${ContractResult.GAS_LIMIT},
    ${ContractResult.GAS_USED},
    ${ContractResult.PAYER_ACCOUNT_ID}
    from ${ContractResult.tableName}`;

  static contractLogsByIdQuery = `select ${ContractLog.CONTRACT_ID},
    ${ContractLog.CONSENSUS_TIMESTAMP},
    ${ContractLog.DATA},
    ${ContractLog.INDEX},
    ${ContractLog.ROOT_CONTRACT_ID},
    ${ContractLog.TOPIC0},
    ${ContractLog.TOPIC1},
    ${ContractLog.TOPIC2},
    ${ContractLog.TOPIC3}
    from ${ContractLog.tableName} ${ContractLog.tableAlias}`;

  getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;
    const query = [
      ContractService.detailedContractResultsQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP), order),
      super.getLimitQuery(whereParams.length + 1), // get limit param located at end of array
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  async getContractResultsByIdAndFilters(
    whereConditions = [],
    whereParams = [],
    order = orderFilterValues.DESC,
    limit = defaultLimit
  ) {
    const [query, params] = this.getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit);
    const rows = await super.getRows(query, params, 'getContractResultsByIdAndFilters');
    return _.isEmpty(rows) ? [] : rows.map((cr) => new ContractResult(cr));
  }

  /**
   * Retrieves a contract result based on its timestamp
   *
   * @param {string} timestamp consensus timestamp
   * @return {Promise<{ContractResult}>}
   */
  async getContractResultByTimestamp(timestamp) {
    const whereParams = [timestamp];
    const whereConditions = [`${ContractResult.CONSENSUS_TIMESTAMP} = $1`];
    const query = [ContractService.contractResultsQuery, `where ${whereConditions.join(' and ')}`].join('\n');
    const rows = await super.getRows(query, whereParams, 'getContractResultsByIdAndTimestamp');
    if (rows.length !== 1) {
      logger.trace(`No matching contract results for timestamp: ${timestamp}`);
      return null;
    }

    return new ContractResult(rows[0]);
  }

  /**
   * Builds a query for retrieving contract logs based on contract id and various filters
   *
   * @param whereConditions the conditions to build a where clause out of
   * @param whereParams the parameters for the where clause
   * @param timestampOrder the sorting order for field consensus_timestamp
   * @param indexOrder the sorting order for field index
   * @param limit the limit parameter for the query
   * @returns {(string|*)[]} the build query and the parameters for the query
   */
  getContractLogsByIdAndFiltersQuery(whereConditions, whereParams, timestampOrder, indexOrder, limit) {
    const params = whereParams;
    const orderClause = [
      super.getOrderByQuery(ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP), timestampOrder),
      `${ContractLog.getFullName(ContractLog.INDEX)} ${indexOrder}`,
    ].join(', ');
    const query = [
      ContractService.contractLogsByIdQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      orderClause,
      super.getLimitQuery(params.length + 1),
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  /**
   * Retrieves contract logs based on contract id and various filters
   *
   * @param whereConditions the conditions to build a where clause out of
   * @param whereParams the parameters for the where clause
   * @param timestampOrder the sorting order for field consensus_timestamp
   * @param indexOrder the sorting order for field index
   * @param limit the limit parameter for the query
   * @returns {Promise<*[]|*>} the result of the getContractLogsByIdAndFilters query
   */
  async getContractLogsByIdAndFilters(
    whereConditions = [],
    whereParams = [],
    timestampOrder = orderFilterValues.DESC,
    indexOrder = orderFilterValues.DESC,
    limit = defaultLimit
  ) {
    const [query, params] = this.getContractLogsByIdAndFiltersQuery(
      whereConditions,
      whereParams,
      timestampOrder,
      indexOrder,
      limit
    );
    const rows = await super.getRows(query, params, 'getContractLogsByIdAndFilters');
    return _.isEmpty(rows) ? [] : rows.map((cr) => new ContractLog(cr));
  }
}

module.exports = new ContractService();
