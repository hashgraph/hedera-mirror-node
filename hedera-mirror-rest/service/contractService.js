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
const {logger} = require('../stream/utils');

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  constructor() {
    super();
  }

  static contractResultsByIdQuery = `select *
    from ${ContractResult.tableName} ${ContractResult.tableAlias}`;

  static contractLogTimestampSubQuery = `select ${ContractLog.CONSENSUS_TIMESTAMP} 
    from ${ContractLog.tableName} ${ContractLog.tableAlias}`;

  static contractLogsByIdQuery = `select 
  ${ContractLog.CONTRACT_ID},
  ${ContractLog.BLOOM},
  ${ContractLog.CONSENSUS_TIMESTAMP},
  ${ContractLog.DATA},
  array_to_json(array_remove(ARRAY[${ContractLog.TOPIC0}, ${ContractLog.TOPIC1}, ${ContractLog.TOPIC2}, ${ContractLog.TOPIC3}], null))::jsonb as topics
    from ${ContractLog.tableName} ${ContractLog.tableAlias} where ${ContractLog.CONSENSUS_TIMESTAMP} in (`;

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

  async getContractLogsByIdAndFilters(
    whereConditions = [],
    whereParams = [],
    order = orderFilterValues.DESC,
    limit = defaultLimit,
    subQueryConditions,
    subQueryParams
  ) {
    const [query, params] = this.getContractLogsByIdAndFiltersQuery(
      whereConditions,
      whereParams,
      order,
      limit,
      subQueryConditions,
      subQueryParams
    );
    const rows = await super.getRows(query, params, 'getContractLogsByIdAndFilters');
    return _.isEmpty(rows) ? [] : rows.map((cr) => new ContractLog(cr));
  }

  getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;
    const query = [
      ContractService.contractResultsByIdQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP), order),
      super.getLimitQuery(whereParams.length + 1),
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  getContractLogsByIdAndFiltersQuery(whereConditions, whereParams, order, limit, subQueryConditions, subQueryParams) {
    const params = whereParams.concat(subQueryParams);
    // params.concat(subQueryParams);
    const query = [
      ContractService.contractLogsByIdQuery,
      ContractService.contractLogTimestampSubQuery,
      subQueryConditions.length > 0 ? `where ${subQueryConditions.join(' and ')}` : '',
      super.getOrderByQuery(ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP), order),
      super.getLimitQuery(params.length + 1),
      `)`,
      whereConditions.length > 0 ? `and ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP), order),
      super.getLimitQuery(params.length + 1),
    ].join('\n');
    params.push(limit);

    logger.info(query);
    logger.info(params);

    return [query, params];
  }
}

module.exports = new ContractService();
