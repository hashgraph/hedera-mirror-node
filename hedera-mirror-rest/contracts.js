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
const {Range} = require('pg-range');

const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');

const {Contract, FileData} = require('./model');
const {ContractViewModel} = require('./viewmodel');
const {NotFoundError} = require('./errors/notFoundError');

const contractSelectFields = [
  Contract.AUTO_RENEW_PERIOD,
  Contract.CREATED_TIMESTAMP,
  Contract.DELETED,
  Contract.EXPIRATION_TIMESTAMP,
  Contract.FILE_ID,
  Contract.ID,
  Contract.KEY,
  Contract.MEMO,
  Contract.OBTAINER_ID,
  Contract.PROXY_ACCOUNT_ID,
  Contract.TIMESTAMP_RANGE,
].map((column) => Contract.getFullName(column));

// the query finds the file content valid at the contract's created timestamp T by aggregating the contents of all the
// file* txs from the latest FileCreate or FileUpdate transaction before T, to T
// Note the 'contract' relation is the cte not the 'contract' table
const fileDataQuery = `select
      string_agg(
          ${FileData.getFullName(FileData.FILE_DATA)}, ''
          order by ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)}
      ) bytecode
    from ${FileData.tableName} ${FileData.tableAlias}
    join ${Contract.tableName} ${Contract.tableAlias}
      on ${Contract.getFullName(Contract.FILE_ID)} = ${FileData.getFullName(FileData.ENTITY_ID)}
    where ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} >= (
      select ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)}
      from ${FileData.tableName} ${FileData.tableAlias}
      join ${Contract.tableName} ${Contract.tableAlias}
        on ${Contract.getFullName(Contract.FILE_ID)} = ${FileData.getFullName(FileData.ENTITY_ID)}
          and ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} <= ${Contract.getFullName(
  Contract.CREATED_TIMESTAMP
)}
      where ${FileData.getFullName(FileData.TRANSACTION_TYPE)} = 17
        or (${FileData.getFullName(FileData.TRANSACTION_TYPE)} = 19 and length(${FileData.getFullName(
  FileData.FILE_DATA
)}) <> 0)
      order by ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} desc
      limit 1
    ) and ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} <= ${Contract.getFullName(Contract.CREATED_TIMESTAMP)}
      and ${Contract.getFullName(Contract.FILE_ID)} is not null`;

/**
 * Extracts the sql where clause, params, order and limit values to be used from the provided contract query
 * param filters
 * @param filters
 * @return {{limit: number, params: number[], filterQuery: string, order: string}}
 */
const extractSqlFromContractFilters = (filters) => {
  const filterQuery = {
    filterQuery: '',
    params: [config.maxLimit],
    order: constants.orderFilterValues.ASC,
    limit: config.maxLimit,
    limitQuery: 'limit $1',
  };

  // if no filters return default filter of no where clause, maxLimit and asc order
  if (filters && filters.length === 0) {
    return filterQuery;
  }

  const conditions = [];
  const contractIdFullName = Contract.getFullName(Contract.ID);
  const contractIdInValues = [];
  const params = [];

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.CONTRACT_ID:
        if (filter.operator === utils.opsMap.eq) {
          // aggregate '=' conditions and use the sql 'in' operator
          contractIdInValues.push(filter.value);
        } else {
          params.push(filter.value);
          conditions.push(`${contractIdFullName}${filter.operator}$${params.length}`);
        }
        break;
      case constants.filterKeys.LIMIT:
        filterQuery.limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        filterQuery.order = filter.value;
        break;
    }
  }

  if (contractIdInValues.length !== 0) {
    // add the condition 'c.id in ()'
    const start = params.length + 1; // start is the next positional index
    params.push(...contractIdInValues);
    const positions = _.range(contractIdInValues.length)
      .map((position) => position + start)
      .map((position) => `$${position}`);
    conditions.push(`${contractIdFullName} in (${positions})`);
  }
  const whereQuery = conditions.length !== 0 ? `where ${conditions.join(' and ')}` : '';

  // add limit
  params.push(filterQuery.limit);
  filterQuery.limitQuery = `limit $${params.length}`;

  filterQuery.filterQuery = whereQuery;
  filterQuery.params = params;

  return filterQuery;
};

/**
 * Extracts the aggregated timestamp range condition from the timestamp filters
 * @param filters
 */
const extractTimestampConditionsFromContractFilters = (filters) => {
  const conditions = [];
  const params = [];
  const timestampRangeColumn = Contract.getFullName(Contract.TIMESTAMP_RANGE);

  filters
    .filter((filter) => filter.key === constants.filterKeys.TIMESTAMP)
    .forEach((filter) => {
      // the first param is the contract id, the param for the current filter will be pushed later, so add 2
      const position = `$${params.length + 2}`;
      let condition;
      let range;

      if (filter.operator === utils.opsMap.ne) {
        // handle ne filter differently
        condition = `not ${timestampRangeColumn} @> ${position}`; // @> is the pg range "contains" operator
        range = Range(filter.value, filter.value, '[]');
      } else {
        condition = `${timestampRangeColumn} && ${position}`; // && is the pg range "overlaps" operator

        switch (filter.operator) {
          case utils.opsMap.lt:
            range = Range(null, filter.value, '()');
            break;
          case utils.opsMap.eq:
          case utils.opsMap.lte:
            range = Range(null, filter.value, '(]');
            break;
          case utils.opsMap.gt:
            range = Range(filter.value, null, '()');
            break;
          case utils.opsMap.gte:
            range = Range(filter.value, null, '[)');
            break;
        }
      }

      conditions.push(condition);
      params.push(range);
    });

  return {
    conditions,
    params,
  };
};

/**
 * Formats a contract row from database to the contract view model
 * @param row
 * @return {ContractViewModel}
 */
const formatContractRow = (row) => {
  const model = new Contract(row);
  return new ContractViewModel(model);
};

/**
 * Gets the query by contract id for the specified table, optionally with timestamp conditions
 * @param table
 * @param timestampConditions
 * @return {string}
 */
const getContractByIdQueryForTable = (table, timestampConditions) => {
  const conditions = [`${Contract.getFullName(Contract.ID)} = $1`, ...timestampConditions];

  return [
    `select ${contractSelectFields}`,
    `from ${table} ${Contract.tableAlias}`,
    `where ${conditions.join(' and ')}`,
  ].join('\n');
};

/**
 * Gets the sql query for a specific contract, optionally with timestamp condition
 * @param timestampConditions
 * @return {string}
 */
const getContractByIdQuery = (timestampConditions) => {
  const tableUnionQueries = [getContractByIdQueryForTable(Contract.tableName, timestampConditions)];
  if (timestampConditions.length !== 0) {
    // if there is timestamp condition, union the result from both tables
    tableUnionQueries.push(
      'union',
      getContractByIdQueryForTable(Contract.historyTableName, timestampConditions),
      `order by ${Contract.TIMESTAMP_RANGE} desc`,
      `limit 1`
    );
  }

  const cte = `with contract as (
    ${tableUnionQueries.join('\n')}
  ), contract_file as (
    ${fileDataQuery}
  )`;

  return [
    cte,
    `select ${[...contractSelectFields, 'cf.bytecode']}`,
    `from contract ${Contract.tableAlias}, contract_file cf`,
  ].join('\n');
};

/**
 * Gets the sql query for contracts
 * @param whereQuery
 * @param limitQuery
 * @param order
 * @return {string}
 */
const getContractsQuery = (whereQuery, limitQuery, order) => {
  return [
    `select ${contractSelectFields}`,
    `from ${Contract.tableName} ${Contract.tableAlias}`,
    whereQuery,
    `order by ${Contract.getFullName(Contract.ID)} ${order}`,
    limitQuery,
  ]
    .filter((q) => q !== '')
    .join('\n');
};

/**
 * Handler function for /contracts/:contractId API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getContractById = async (req, res) => {
  const contractId = EntityId.fromString(req.params.contractId, constants.filterKeys.CONTRACTID).getEncodedId();
  const params = [contractId];

  // extract filters from query param
  const filters = utils.buildAndValidateFilters(req.query);
  const {conditions, params: timestampParams} = extractTimestampConditionsFromContractFilters(filters);
  const query = getContractByIdQuery(conditions);
  params.push(...timestampParams);

  if (logger.isTraceEnabled()) {
    logger.trace(`getContractById query: ${query}, params: ${params}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  res.locals[constants.responseDataLabel] = formatContractRow(rows[0]);
};

/**
 * Handler function for /contracts API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getContracts = async (req, res) => {
  utils.validateReq(req);

  // extract filters from query param
  const filters = utils.buildAndValidateFilters(req.query);

  // get sql filter query, params, limit and limit query from query filters
  const {filterQuery, params, order, limit, limitQuery} = extractSqlFromContractFilters(filters);
  const query = getContractsQuery(filterQuery, limitQuery, order);

  if (logger.isTraceEnabled()) {
    logger.trace(`getContracts query: ${query}, params: ${params}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  logger.debug(`getContracts returning ${rows.length} entries`);

  const response = {
    contracts: rows.map((row) => formatContractRow(row)),
    links: {},
  };

  const lastRow = _.last(response.contracts);
  const lastContractId = lastRow !== undefined ? lastRow.contract_id : null;
  response.links.next = utils.getPaginationLink(
    req,
    response.contracts.length !== limit,
    constants.filterKeys.CONTRACT_ID,
    lastContractId,
    order
  );

  res.locals[constants.responseDataLabel] = response;
};

module.exports = {
  getContractById,
  getContracts,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    extractSqlFromContractFilters,
    extractTimestampConditionsFromContractFilters,
    fileDataQuery,
    formatContractRow,
    getContractByIdQuery,
    getContractsQuery,
  });
}
