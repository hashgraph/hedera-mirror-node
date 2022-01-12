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

const {Contract, ContractLog, ContractResult, FileData, TransactionResult} = require('../model');
const {ContractService, RecordFileService, TransactionService} = require('../service');
const TransactionId = require('../transactionId');
const utils = require('../utils');
const {
  ContractViewModel,
  ContractLogViewModel,
  ContractResultViewModel,
  ContractResultDetailsViewModel,
} = require('../viewmodel');
const {httpStatusCodes} = require('../constants');

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

const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');

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
  ),
  contract_file as (
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
  // get sql filter query, params, limit and limit query from query filters
  const filters = utils.buildAndValidateFilters(req.query);
  const contractId = EntityId.parse(req.params.contractId, constants.filterKeys.CONTRACTID).getEncodedId();
  checkTimestampsForTopics(filters);

  const {conditions, params, timestampOrder, indexOrder, limit} = extractContractLogsByIdQuery(filters, contractId);

  const rows = await ContractService.getContractLogsByIdAndFilters(
    conditions,
    params,
    timestampOrder,
    indexOrder,
    limit
  );

  res.locals[constants.responseDataLabel] = {
    logs: rows.map((row) => new ContractLogViewModel(row)),
  };
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
  const [contractResults, recordFile, transaction, contractLogs] = await Promise.all([
    ContractService.getContractResultsByTimestamps(timestamp),
    RecordFileService.getRecordFileBlockDetailsFromTimestamp(timestamp),
    TransactionService.getTransactionDetailsFromTimestamp(timestamp),
    ContractService.getContractLogsByTimestamps(timestamp),
  ]);
  if (contractResults.length === 0) {
    throw new NotFoundError();
  }

  res.locals[constants.responseDataLabel] = new ContractResultDetailsViewModel(
    contractResults[0],
    recordFile,
    transaction,
    contractLogs
  );
};

/**
 * Gets the last nonce value if exists, defaults to 0
 * @param query
 * @returns {Number}
 */
const getLastNonceParamValue = (query) => {
  const key = constants.filterKeys.NONCE;
  let nonce = 0; // default

  if (key in query) {
    const values = query[key];
    nonce = Array.isArray(values) ? values[values.length - 1] : values;
  }

  return nonce;
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
  const nonce = getLastNonceParamValue(req.query);

  // get transactions using id and nonce, exclude duplicate transactions. there can be at most one
  const transactions = await TransactionService.getTransactionDetailsFromTransactionIdAndNonce(
    transactionId,
    nonce,
    duplicateTransactionResult
  );
  if (transactions.length === 0) {
    throw new NotFoundError('No correlating transaction');
  } else if (transactions.length > 1) {
    logger.error(
      'Transaction invariance breached: there should be at most one transaction with none-duplicate-transaction ' +
        'result for a specific (payer + valid start timestamp + nonce) combination'
    );
    throw new Error('Transaction invariance breached');
  }

  // retrieve contract result and recordFile models concurrently using transaction timestamp
  const transaction = transactions[0];
  const [contractResults, recordFile, contractLogs] = await Promise.all([
    ContractService.getContractResultsByTimestamps(transaction.consensusTimestamp),
    RecordFileService.getRecordFileBlockDetailsFromTimestamp(transaction.consensusTimestamp),
    ContractService.getContractLogsByTimestamps(transaction.consensusTimestamp),
  ]);
  if (contractResults.length === 0) {
    throw new NotFoundError();
  }

  res.locals[constants.responseDataLabel] = new ContractResultDetailsViewModel(
    contractResults[0],
    recordFile,
    transaction,
    contractLogs
  );
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
  let indexOrder = constants.orderFilterValues.DESC;
  const conditions = [`${ContractLog.getFullName(ContractLog.CONTRACT_ID)} = $1`];
  const params = [contractId];

  const inValues = {};
  const keyFullNames = {};
  const oneOperatorValues = {};

  keyFullNames[constants.filterKeys.TIMESTAMP] = ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP);
  inValues[constants.filterKeys.TIMESTAMP] = [];

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.INDEX:
        if (oneOperatorValues[filter.key]) {
          throw new InvalidArgumentError(`Multiple params not allowed for ${filter.key}`);
        }
        params.push(filter.value);
        conditions.push(`${ContractLog.getFullName(filter.key)}${filter.operator}$${params.length}`);
        oneOperatorValues[filter.key] = true;
        break;
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        timestampOrder = filter.value;
        indexOrder = filter.value;
        break;
      case constants.filterKeys.TIMESTAMP:
        if (filter.operator === utils.opsMap.ne) {
          throw new InvalidArgumentError('Not equals operator not supported for timestamp param');
        }
        updateConditionsAndParamsWithInValues(
          filter,
          inValues[filter.key],
          params,
          conditions,
          keyFullNames[filter.key]
        );
        break;
      case constants.filterKeys.TOPIC0:
      case constants.filterKeys.TOPIC1:
      case constants.filterKeys.TOPIC2:
      case constants.filterKeys.TOPIC3:
        if (oneOperatorValues[filter.key]) {
          throw new InvalidArgumentError(`Multiple params not allowed for ${filter.key}`);
        }
        let topic = filter.value.replace(/^(0x)?0*/, '');
        if (topic.length % 2 !== 0) {
          topic = `0${topic}`; //Left pad so that Buffer.from parses correctly
        }
        topic = Buffer.from(topic, 'hex');
        params.push(topic);
        conditions.push(`${ContractLog.getFullName(filter.key)}${filter.operator}$${params.length}`);
        oneOperatorValues[filter.key] = true;
        break;
      default:
        break;
    }
  }

  // update query with repeated values
  updateQueryFiltersWithInValues(
    params,
    conditions,
    inValues[constants.filterKeys.TIMESTAMP],
    keyFullNames[constants.filterKeys.TIMESTAMP]
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

const updateQueryFiltersWithInValues = (existingParams, existingConditions, invalues, fullName) => {
  if (!_.isNil(invalues) && !_.isEmpty(invalues)) {
    // add the condition 'c.id in ()'
    const start = existingParams.length + 1; // start is the next positional index
    existingParams.push(...invalues);
    const positions = _.range(invalues.length)
      .map((position) => position + start)
      .map((position) => `$${position}`);
    existingConditions.push(`${fullName} in (${positions})`);
  }
};

const checkTimestampsForTopics = (filters) => {
  let hasTopic = false;
  const timestampFilters = [];
  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.TOPIC0:
      case constants.filterKeys.TOPIC1:
      case constants.filterKeys.TOPIC2:
      case constants.filterKeys.TOPIC3:
        hasTopic = true;
        break;
      case constants.filterKeys.TIMESTAMP:
        timestampFilters.push(filter);
        break;
      default:
        break;
    }
  }
  if (hasTopic) {
    try {
      utils.checkTimestampRange(timestampFilters);
    } catch (e) {
      throw new InvalidArgumentError(`Cannot search topics without timestamp range: ${e.message}`);
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
    getLastNonceParamValue,
    validateContractIdAndConsensusTimestampParam,
    validateContractIdParam,
  });
}
