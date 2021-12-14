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

const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const constants = require('../constants');
const EntityId = require('../entityId');

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');
const {NotFoundError} = require('../errors/notFoundError');

const config = require('../config.js');
const {Contract, FileData, ContractLog, ContractResult} = require('../model');
const {ContractService, RecordFileService, TransactionService} = require('../service');
const {logger} = require('../stream/utils');
const TransactionId = require('../transactionId');
const utils = require('../utils');
const {ContractViewModel, ContractLogViewModel, ContractResultViewModel} = require('../viewmodel');

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
    params: [defaultLimit],
    order: constants.orderFilterValues.DESC,
    limit: defaultLimit,
    limitQuery: 'limit $1',
  };

  // if no filters return default filter of no where clause, defaultLimit and asc order
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
  const contractId = EntityId.parse(req.params.contractId, constants.filterKeys.CONTRACTID).getEncodedId();
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

const defaultParamSupportMap = {
  [constants.filterKeys.LIMIT]: true,
  [constants.filterKeys.ORDER]: true,
};
const contractResultsByIdParamSupportMap = {
  [constants.filterKeys.FROM]: true,
  [constants.filterKeys.TIMESTAMP]: true,
  ...defaultParamSupportMap,
};

/**
 * Verify contractId meets entity id format
 */
const validateContractIdParam = (contractId) => {
  if (!EntityId.isValidEntityId(contractId)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.CONTRACTID);
  }
};

const getAndValidateContractIdRequestPathParam = (req) => {
  const contractIdString = req.params.contractId;
  validateContractIdParam(contractIdString);
  return EntityId.parse(contractIdString, constants.filterKeys.CONTRACTID).getEncodedId();
};

/**
 * Verify both
 * consensusTimestamp meets seconds or seconds.upto 9 digits format
 * contractId meets entity id format
 */
const validateContractIdAndConsensusTimestampParam = (consensusTimestamp, contractId) => {
  const params = [];
  if (!EntityId.isValidEntityId(contractId)) {
    params.push(constants.filterKeys.CONTRACTID);
  }
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    params.push(constants.filterKeys.TIMESTAMP);
  }

  if (params.length > 0) {
    throw InvalidArgumentError.forParams(params);
  }
};

const getAndValidateContractIdAndConsensusTimestampPathParams = (req) => {
  const {consensusTimestamp, contractId} = req.params;
  validateContractIdAndConsensusTimestampParam(consensusTimestamp, contractId);
  return {
    timestamp: utils.parseTimestampParam(consensusTimestamp),
    contractId: EntityId.parse(contractId, constants.filterKeys.CONTRACTID).getEncodedId(),
  };
};

const extractContractIdAndFiltersFromValidatedRequest = (req) => {
  utils.validateReq(req);
  // extract filters from query param
  const contractId = getAndValidateContractIdRequestPathParam(req);
  const filters = utils.buildAndValidateFilters(req.query);
  return {
    contractId,
    filters,
  };
};

/**
 * Handler function for /contracts/:contractId/results/logs API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getContractLogs = async (req, res) => {
  utils.validateReq(req);

  checkTimestampsForTopics(req.query.timestamp, req.query.topic0, req.query.topic1, req.query.topic2, req.query.topic3);
  // extract filters from query param
  const contractId = EntityId.parse(req.params.contractId, constants.filterKeys.CONTRACTID).getEncodedId();
  const filters = utils.buildAndValidateFilters(req.query);

  // get sql filter query, params, limit and limit query from query filters
  const {conditions, params, timestampOrder, indexOrder, limit} = extractContractLogsByIdQuery(filters, contractId);

  const rows = await ContractService.getContractLogsByIdAndFilters(
    conditions,
    params,
    timestampOrder,
    indexOrder,
    limit
  );

  const response = {
    contract_logs: rows.map((row) => new ContractLogViewModel(row)),
  };

  res.send(response);
};

/**
 * Extracts SQL where conditions, params, order, and limit
 *
 * @param {[]} filters parsed and validated filters
 * @param {string} contractId encoded contract ID
 * @return {{conditions: [], params: [], order: 'asc'|'desc', limit: number}}
 */
const extractContractResultsByIdQuery = (filters, contractId, paramSupportMap = defaultParamSupportMap) => {
  let limit = defaultLimit;
  let order = constants.orderFilterValues.DESC;
  const conditions = [`${ContractResult.getFullName(ContractResult.CONTRACT_ID)} = $1`];
  const params = [contractId];

  const contractResultFromFullName = ContractResult.getFullName(ContractResult.PAYER_ACCOUNT_ID);
  const contractResultFromInValues = [];

  const contractResultTimestampFullName = ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP);
  const contractResultTimestampInValues = [];

  for (const filter of filters) {
    if (_.isNil(paramSupportMap[filter.key])) {
      // param not supported for current endpoint
      continue;
    }

    switch (filter.key) {
      case constants.filterKeys.FROM:
        // handle repeated values
        updateConditionsAndParamsWithInValues(
          filter,
          contractResultFromInValues,
          params,
          conditions,
          contractResultFromFullName
        );
        break;
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      case constants.filterKeys.TIMESTAMP:
        // handle repeated values
        updateConditionsAndParamsWithInValues(
          filter,
          contractResultTimestampInValues,
          params,
          conditions,
          contractResultTimestampFullName
        );
        break;
      default:
        break;
    }
  }

  // update query with repeated values
  updateQueryFiltersWithInValues(params, conditions, contractResultFromInValues, contractResultFromFullName);
  updateQueryFiltersWithInValues(params, conditions, contractResultTimestampInValues, contractResultTimestampFullName);

  return {
    conditions: conditions,
    params: params,
    order: order,
    limit: limit,
  };
};

/**
 * Handler function for /contracts/:contractId/results API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getContractResultsById = async (req, res) => {
  const {contractId, filters} = extractContractIdAndFiltersFromValidatedRequest(req);

  const {conditions, params, order, limit} = extractContractResultsByIdQuery(
    filters,
    contractId,
    contractResultsByIdParamSupportMap
  );

  const rows = await ContractService.getContractResultsByIdAndFilters(conditions, params, order, limit);
  const response = {
    results: rows.map((row) => new ContractResultViewModel(row)),
    links: {
      next: null,
    },
  };

  if (!_.isEmpty(response.results)) {
    const lastRow = _.last(response.results);
    const lastContractResultTimestamp = lastRow !== undefined ? lastRow.timestamp : null;
    response.links.next = utils.getPaginationLink(
      req,
      response.results.length !== limit,
      constants.filterKeys.TIMESTAMP,
      lastContractResultTimestamp,
      order
    );
  }

  res.locals[constants.responseDataLabel] = response;
};

/**
 * Handler function for /contracts/:contractId/results/:consensusTimestamp API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getContractResultsByTimestamp = async (req, res) => {
  const {timestamp} = getAndValidateContractIdAndConsensusTimestampPathParams(req);

  // retrieve contract result, recordFile and transaction models concurrently
  await Promise.all([
    ContractService.getContractResultByTimestamp(timestamp),
    RecordFileService.getRecordFileBlockDetailsFromTimestamp(timestamp),
    TransactionService.getTransactionContractDetailsFromTimestamp(timestamp),
  ]).then((responses) => {
    const contractResult = responses[0];

    if (contractResult === null) {
      throw new NotFoundError();
    }

    res.locals[constants.responseDataLabel] = new ContractResultViewModel(contractResult, responses[1], responses[2]);
  });
};

/**
 * Handler function for /contracts/results/:transactionId API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getContractResultsByTransactionId = async (req, res) => {
  utils.validateReq(req);
  // extract filters from query param
  const transactionId = TransactionId.fromString(req.params.transactionId);

  // get transaction using id.
  const transaction = await TransactionService.getTransactionContractDetailsFromTransactionId(transactionId);
  if (transaction === null) {
    throw new NotFoundError('No correlating transaction');
  }

  // retrieve contract result and recordFile models concurrently using transaction timestamp
  await Promise.all([
    ContractService.getContractResultByTimestamp(transaction.consensusTimestamp),
    RecordFileService.getRecordFileBlockDetailsFromTimestamp(transaction.consensusTimestamp),
  ]).then((responses) => {
    const contractResult = responses[0];

    if (contractResult === null) {
      throw new NotFoundError();
    }

    res.locals[constants.responseDataLabel] = new ContractResultViewModel(contractResult, responses[1], transaction);
  });
};

/**
 * Extracts SQL where conditions, params, order, and limit
 *
 * @param {[]} filters parsed and validated filters
 * @param {string} contractId encoded contract ID
 * @return {{conditions: [], params: [], order: 'asc'|'desc', limit: number}}
 */
const extractContractLogsByIdQuery = (filters, contractId) => {
  let limit = defaultLimit;
  let timestampOrder = constants.orderFilterValues.DESC;
  let indexOrder = constants.orderFilterValues.ASC;
  const conditions = [`${ContractLog.getFullName(ContractLog.CONTRACT_ID)} = $1`];
  const params = [contractId];

  const contractLogIndexFullName = ContractLog.getFullName(ContractLog.INDEX);
  const contractLogIndexInValues = [];

  const contractLogTimestampFullName = ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP);
  const contractLogTimestampInValues = [];

  const contractLogTopic0FullName = ContractLog.getFullName(ContractLog.TOPIC0);
  const contractLogTopic0InValues = [];

  const contractLogTopic1FullName = ContractLog.getFullName(ContractLog.TOPIC1);
  const contractLogTopic1InValues = [];

  const contractLogTopic2FullName = ContractLog.getFullName(ContractLog.TOPIC2);
  const contractLogTopic2InValues = [];

  const contractLogTopic3FullName = ContractLog.getFullName(ContractLog.TOPIC3);
  const contractLogTopic3InValues = [];

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.INDEX:
        updateConditionsAndParamsWithInValues(
          filter,
          contractLogIndexInValues,
          params,
          conditions,
          contractLogIndexFullName
        );
        break;
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        timestampOrder = filter.value;
        indexOrder = filter.value;
        break;
      case constants.filterKeys.TIMESTAMP:
        updateConditionsAndParamsWithInValues(
          filter,
          contractLogTimestampInValues,
          params,
          conditions,
          contractLogTimestampFullName
        );
        break;
      case constants.filterKeys.TOPIC0:
        // handle repeated values
        filter.value = filter.value.replace('0x', '');
        updateConditionsAndParamsWithInValues(
          filter,
          contractLogTopic0InValues,
          params,
          conditions,
          contractLogTopic0FullName
        );
        break;
      case constants.filterKeys.TOPIC1:
        filter.value = filter.value.replace('0x', '');
        updateConditionsAndParamsWithInValues(
          filter,
          contractLogTopic1InValues,
          params,
          conditions,
          contractLogTopic1FullName
        );
        break;
      case constants.filterKeys.TOPIC2:
        filter.value = filter.value.replace('0x', '');
        updateConditionsAndParamsWithInValues(
          filter,
          contractLogTopic2InValues,
          params,
          conditions,
          contractLogTopic2FullName
        );
        break;
      case constants.filterKeys.TOPIC3:
        filter.value = filter.value.replace('0x', '');
        updateConditionsAndParamsWithInValues(
          filter,
          contractLogTopic3InValues,
          params,
          conditions,
          contractLogTopic3FullName
        );
        break;
      default:
        break;
    }
  }

  // update query with repeated values
  updateQueryFiltersWithInValues(params, conditions, contractLogIndexInValues, contractLogIndexFullName);
  updateQueryFiltersWithInValues(params, conditions, contractLogTimestampInValues, contractLogTimestampFullName);
  updateQueryFiltersWithInValues(
    params,
    conditions,
    contractLogTopic0InValues,
    contractLogTopic0FullName,
    paramStringBuilderBytes
  );
  updateQueryFiltersWithInValues(
    params,
    conditions,
    contractLogTopic1InValues,
    contractLogTopic1FullName,
    paramStringBuilderBytes
  );
  updateQueryFiltersWithInValues(
    params,
    conditions,
    contractLogTopic2InValues,
    contractLogTopic2FullName,
    paramStringBuilderBytes
  );
  updateQueryFiltersWithInValues(
    params,
    conditions,
    contractLogTopic3InValues,
    contractLogTopic3FullName,
    paramStringBuilderBytes
  );

  return {
    conditions,
    params,
    timestampOrder,
    indexOrder,
    limit,
  };
};

const updateConditionsAndParamsWithInValues = (filter, invalues, existingParams, existingConditions, fullName) => {
  if (filter.operator === utils.opsMap.eq) {
    // aggregate '=' conditions and use the sql 'in' operator
    invalues.push(filter.value);
  } else {
    existingParams.push(filter.value);
    existingConditions.push(`${fullName}${filter.operator}$${existingParams.length}`);
  }
};

const updateQueryFiltersWithInValues = (
  existingParams,
  existingConditions,
  invalues,
  fullName,
  paramStringBuilder = paramStringBuilderDefault
) => {
  if (!_.isNil(invalues) && !_.isEmpty(invalues)) {
    // add the condition 'c.id in ()'
    const start = existingParams.length + 1; // start is the next positional index
    existingParams.push(...invalues);
    const positions = paramStringBuilder(invalues, start);
    existingConditions.push(`${fullName} in (${positions})`);
  }
};

const paramStringBuilderDefault = (invalues, start) => {
  return _.range(invalues.length)
    .map((position) => position + start)
    .map((position) => `$${position}`);
};

const paramStringBuilderBytes = (invalues, start) => {
  return _.range(invalues.length)
    .map((position) => position + start)
    .map((position) => `decode($${position}, 'hex')`);
};

const checkTimestampsForTopics = (timestamps, topic0, topic1, topic2, topic3) => {
  if (topic0 || topic1 || topic2 || topic3) {
    if (!utils.checkTimestampRange(timestamps)) {
      throw new InvalidArgumentError(
        `Cannot search topics without timestamp range within ${config.maxTimestampRange} seconds`
      );
    }
  }
};

module.exports = {
  getContractById,
  getContracts,
  getContractLogs,
  getContractResultsById,
  getContractResultsByTimestamp,
  getContractResultsByTransactionId,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    contractResultsByIdParamSupportMap,
    checkTimestampsForTopics,
    extractContractLogsByIdQuery,
    extractContractResultsByIdQuery,
    extractSqlFromContractFilters,
    extractTimestampConditionsFromContractFilters,
    fileDataQuery,
    formatContractRow,
    getContractByIdQuery,
    getContractsQuery,
    validateContractIdAndConsensusTimestampParam,
    validateContractIdParam,
  });
}
