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

const BaseController = require('./baseController');
const Bound = require('./bound');

const contractSelectFields = [
  Contract.AUTO_RENEW_ACCOUNT_ID,
  Contract.AUTO_RENEW_PERIOD,
  Contract.CREATED_TIMESTAMP,
  Contract.DELETED,
  Contract.EVM_ADDRESS,
  Contract.EXPIRATION_TIMESTAMP,
  Contract.FILE_ID,
  Contract.ID,
  Contract.KEY,
  Contract.MAX_AUTOMATIC_TOKEN_ASSOCIATIONS,
  Contract.MEMO,
  Contract.OBTAINER_ID,
  Contract.PERMANENT_REMOVAL,
  Contract.PROXY_ACCOUNT_ID,
  Contract.TIMESTAMP_RANGE,
].map((column) => Contract.getFullName(column));
const contractWithInitcodeSelectFields = [...contractSelectFields, Contract.getFullName(Contract.INITCODE)];

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
 * @return {{filterQuery: string, params: number[], order: string, limit: number, limitQuery: string}}
 */
const extractSqlFromContractFilters = async (filters) => {
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
  const contractIdInValues = [];
  const params = [];
  const contractIdFullName = Contract.getFullName(Contract.ID);

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.CONTRACT_ID:
        const contractIdValue = await ContractService.computeContractIdFromString(filter.value);

        if (filter.operator === utils.opsMap.eq) {
          // aggregate '=' conditions and use the sql 'in' operator
          contractIdInValues.push(contractIdValue);
        } else {
          params.push(contractIdValue);
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
      const position = `$${params.length + 1}`;
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
 * @param conditions
 * @return {string}
 */
const getContractByIdOrAddressQueryForTable = (table, conditions) => {
  return [
    `select ${contractWithInitcodeSelectFields}`,
    `from ${table} ${Contract.tableAlias}`,
    `where ${conditions.join(' and ')}`,
  ].join('\n');
};

/**
 * Gets the sql query for a specific contract, optionally with timestamp condition
 * @param timestampConditions
 * @return {query: string, params: any[]}
 */
const getContractByIdOrAddressQuery = ({timestampConditions, timestampParams, contractIdParam}) => {
  const conditions = [...timestampConditions];
  const params = [...timestampParams];
  const contractIdParamParts = EntityId.computeContractIdPartsFromContractIdValue(contractIdParam);

  if (contractIdParamParts.hasOwnProperty('create2_evm_address')) {
    const {params: evmAddressParams, conditions: evmAddressConditions} =
      ContractService.computeConditionsAndParamsFromEvmAddressFilter({
        evmAddressFilter: contractIdParamParts,
        paramOffset: params.length,
      });
    params.push(...evmAddressParams);
    conditions.push(...evmAddressConditions);
  } else {
    const encodedId = EntityId.parse(_.last(contractIdParam.split('.'))).getEncodedId();
    params.push(encodedId);
    conditions.push(`${Contract.getFullName(Contract.ID)} = $${params.length}`);
  }

  const tableUnionQueries = [getContractByIdOrAddressQueryForTable(Contract.tableName, conditions)];
  if (timestampConditions.length !== 0) {
    // if there is timestamp condition, union the result from both tables
    tableUnionQueries.push(
      'union',
      getContractByIdOrAddressQueryForTable(Contract.historyTableName, conditions),
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

  const selectFields = [
    ...contractSelectFields,
    `coalesce(encode(${Contract.getFullName(Contract.INITCODE)}, 'hex')::bytea, cf.bytecode) as bytecode`,
  ];
  return {
    query: [cte, `select ${selectFields}`, `from contract ${Contract.tableAlias}, contract_file cf`].join('\n'),
    params,
  };
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
      throw new InvalidArgumentError(`Cannot search topics without a valid timestamp range: ${e.message}`);
    }
  }
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
  if (EntityId.isValidEvmAddress(contractId)) {
    return;
  }

  if (!EntityId.isValidEntityId(contractId)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.CONTRACTID);
  }
};

const getAndValidateContractIdRequestPathParam = (req) => {
  const contractIdValue = req.params.contractId;
  validateContractIdParam(contractIdValue);
  // if it is a valid contract id and has the substring 0x, the substring 0x can only be a prefix.
  return contractIdValue.replace('0x', '');
};

/**
 * Verify both
 * consensusTimestamp meets seconds or seconds.upto 9 digits format
 * contractId meets entity id format
 */
const validateContractIdAndConsensusTimestampParam = (consensusTimestamp, contractId) => {
  const params = [];
  if (!EntityId.isValidEntityId(contractId) && !EntityId.isValidEvmAddress(contractId)) {
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
  return {timestamp: utils.parseTimestampParam(consensusTimestamp)};
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

class ContractController extends BaseController {
  /**
   * Extracts SQL where conditions, params, order, and limit
   *
   * @param {[]} filters parsed and validated filters
   * @param {string} contractId encoded contract ID
   * @return {{conditions: [], params: [], order: 'asc'|'desc', limit: number}}
   */
  extractContractResultsByIdQuery = (filters, contractId, paramSupportMap = defaultParamSupportMap) => {
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
          this.updateConditionsAndParamsWithInValues(
            filter,
            contractResultFromInValues,
            params,
            conditions,
            contractResultFromFullName,
            conditions.length + 1
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
          this.updateConditionsAndParamsWithInValues(
            filter,
            contractResultTimestampInValues,
            params,
            conditions,
            contractResultTimestampFullName,
            conditions.length + 1
          );
          break;
        default:
          break;
      }
    }

    // update query with repeated values
    this.updateQueryFiltersWithInValues(params, conditions, contractResultFromInValues, contractResultFromFullName);
    this.updateQueryFiltersWithInValues(
      params,
      conditions,
      contractResultTimestampInValues,
      contractResultTimestampFullName
    );

    return {
      conditions: conditions,
      params: params,
      order: order,
      limit: limit,
    };
  };
  /**
   * Extracts SQL where conditions, params, order, and limit
   *
   * @param {[]} filters parsed and validated filters
   * @param {string} contractId encoded contract ID
   * @return {{conditions: [], params: [], order: 'asc'|'desc', limit: number}}
   */
  extractContractLogsQuery = (filters, contractId) => {
    let limit = defaultLimit;
    let timestampOrder = constants.orderFilterValues.DESC;
    let indexOrder = constants.orderFilterValues.DESC;

    const bounds = {
      [constants.filterKeys.INDEX]: new Bound(),
      [constants.filterKeys.TIMESTAMP]: new Bound(),
    };

    const conditions = [];
    const params = [];

    if (contractId) {
      conditions.push(`${ContractLog.getFullName(ContractLog.CONTRACT_ID)} = $1`);
      params.push(contractId);
    }

    const oneOperatorValues = {};

    const keyFullNames = {
      [constants.filterKeys.TOPIC0]: ContractLog.getFullName(ContractLog.TOPIC0),
      [constants.filterKeys.TOPIC1]: ContractLog.getFullName(ContractLog.TOPIC1),
      [constants.filterKeys.TOPIC2]: ContractLog.getFullName(ContractLog.TOPIC2),
      [constants.filterKeys.TOPIC3]: ContractLog.getFullName(ContractLog.TOPIC3),
    };

    const inValues = {
      [constants.filterKeys.TOPIC0]: [],
      [constants.filterKeys.TOPIC1]: [],
      [constants.filterKeys.TOPIC2]: [],
      [constants.filterKeys.TOPIC3]: [],
    };

    for (const filter of filters) {
      switch (filter.key) {
        case constants.filterKeys.INDEX:
          if (oneOperatorValues[filter.key]) {
            throw new InvalidArgumentError(`Multiple params not allowed for ${filter.key}`);
          }
          bounds[filter.key].parse(filter);
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
          if (bounds[filter.key].hasEqual() && filter.operator === utils.opsMap.eq) {
            throw new InvalidArgumentError(`Cannot search across timestamps`);
          }
          bounds[filter.key].parse(filter);
          break;
        case constants.filterKeys.TOPIC0:
        case constants.filterKeys.TOPIC1:
        case constants.filterKeys.TOPIC2:
        case constants.filterKeys.TOPIC3:
          let topic = filter.value.replace(/^(0x)?0*/, '');
          if (topic.length % 2 !== 0) {
            topic = `0${topic}`; // Left pad so that Buffer.from parses correctly
          }
          filter.value = Buffer.from(topic, 'hex');
          this.updateConditionsAndParamsWithInValues(
            filter,
            inValues[filter.key],
            params,
            conditions,
            keyFullNames[filter.key],
            conditions.length + 1
          );
          break;
        default:
          break;
      }
    }

    // update query with repeated values
    Object.keys(keyFullNames).forEach((filterKey) => {
      this.updateQueryFiltersWithInValues(params, conditions, inValues[filterKey], keyFullNames[filterKey]);
    });

    return {
      bounds,
      conditions,
      params,
      timestampOrder,
      indexOrder,
      limit,
    };
  };

  getContractLogsPaginationFilters = (indexBound, timestampBound, defaultOrder) => {
    const lower = [];
    const upper = [];
    let paginationOrder = defaultOrder;

    if (timestampBound.hasLower() && timestampBound.hasUpper() && indexBound.isEmpty()) {
      // timestamp range is provided but index is missing
      lower.push(timestampBound.lower, timestampBound.upper);
    } else {
      if (timestampBound.hasLower()) {
        // split timestamp and index into 2 parts if both operators are compatible
        // timestamp >= & index =/>/>=
        if (timestampBound.lower.operator == utils.opsMap.gte && (indexBound.hasEqual() || indexBound.hasLower())) {
          lower.push(indexBound.lower, indexBound.equal, {...timestampBound.lower, operator: utils.opsMap.eq});
          upper.push({...timestampBound.lower, operator: utils.opsMap.gt});
        } else {
          // use timestamp only, index is not compatible
          lower.push(timestampBound.lower);
        }
        if (!indexBound.hasUpper()) {
          // ASC order for index >/>=/= & timestamp >=
          paginationOrder = constants.orderFilterValues.ASC;
        }
      }

      if (timestampBound.hasUpper()) {
        // split timestamp and index into 2 parts if both operators are compatible
        // timestamp <= & index =/</<=
        if (timestampBound.upper.operator == utils.opsMap.lte && (indexBound.hasEqual() || indexBound.hasUpper())) {
          upper.push(indexBound.upper, indexBound.equal, {...timestampBound.upper, operator: utils.opsMap.eq});
          lower.push({...timestampBound.upper, operator: utils.opsMap.lt});
        } else {
          // use timestamp only, index is not compatible
          upper.push(timestampBound.upper);
        }
        if (!indexBound.hasLower()) {
          // DESC order for index =/</<= & timestamp <=
          paginationOrder = constants.orderFilterValues.DESC;
        }
      }

      if (timestampBound.hasEqual()) {
        // timestamp has no lower or upper bound, so
        // use the default filters as they are supplied by the user
        lower.push(
          indexBound.lower,
          indexBound.equal,
          indexBound.upper,
          timestampBound.lower,
          timestampBound.equal,
          timestampBound.upper
        );
        // if timestamp and index have no bounds, use the default order
        if (indexBound.hasLower()) {
          // ASC order for index >/>=/= & timestamp =
          paginationOrder = constants.orderFilterValues.ASC;
        }
        if (indexBound.hasUpper()) {
          // DESC order for index =/</<= & timestamp =
          paginationOrder = constants.orderFilterValues.DESC;
        }
      }
    }

    return {
      paginationFilters: [lower.filter((f) => !_.isNil(f)), upper.filter((f) => !_.isNil(f))].filter(
        (filters) => !_.isEmpty(filters)
      ),
      paginationOrder,
    };
  };

  extractContractLogsPaginationQuery = (filters, contractId) => {
    const {
      bounds,
      conditions: baseConditions,
      params: baseParams,
      timestampOrder,
      indexOrder,
      limit,
    } = this.extractContractLogsQuery(filters, contractId);

    const mapFilterKeyToColumn = {
      [constants.filterKeys.INDEX]: constants.filterKeys.INDEX,
      [constants.filterKeys.TIMESTAMP]: ContractLog.CONSENSUS_TIMESTAMP,
    };
    const indexBound = bounds[constants.filterKeys.INDEX];
    const timestampBound = bounds[constants.filterKeys.TIMESTAMP];
    let conditions = [];
    const params = [...baseParams];

    // if index is presented, at least 1 timestamp is required
    if (!indexBound.isEmpty() && timestampBound.isEmpty()) {
      throw new InvalidArgumentError('Cannot search by index without a timestamp');
    }

    if (timestampBound.hasBound() && timestampBound.hasEqual()) {
      throw new InvalidArgumentError('Timestamp range must have gt (or gte) and lt (or lte)');
    }

    // validate index and timestamp operators compatibility
    if (
      indexBound.hasEqual() &&
      timestampBound.hasLower() &&
      timestampBound.lower.operator == utils.opsMap.gte &&
      timestampBound.hasUpper() &&
      timestampBound.upper.operator == utils.opsMap.lte
    ) {
      // invalid ops: index = & timestamp >= & timestamp <=
      throw new InvalidArgumentError('Unsupported combination');
    }

    if (indexBound.hasEqual() && timestampBound.hasEqual() && timestampBound.hasBound()) {
      // invalid ops: index = & timestamp = & timestamp >/>=/</<=
      throw new InvalidArgumentError('Cannot support both range and equal');
    }

    if (
      indexBound.hasLower() &&
      !timestampBound.hasEqual() &&
      (!timestampBound.hasLower() || timestampBound.lower.operator != utils.opsMap.gte)
    ) {
      // bounds should match index >/>= & timestamp =/>=
      throw new InvalidArgumentError('Unsupported combination');
    }

    if (
      indexBound.hasUpper() &&
      !timestampBound.hasEqual() &&
      (!timestampBound.hasUpper() || timestampBound.upper.operator != utils.opsMap.lte)
    ) {
      // bounds should match index </<= & timestamp =/<=
      throw new InvalidArgumentError('Unsupported combination');
    }

    if (
      indexBound.hasEqual() &&
      !timestampBound.hasEqual() &&
      (!timestampBound.hasLower() || timestampBound.lower.operator != utils.opsMap.gte) &&
      (!timestampBound.hasUpper() || timestampBound.upper.operator != utils.opsMap.lte)
    ) {
      // bounds should match index = & timestamp =/>=/<=
      throw new InvalidArgumentError('Unsupported combination');
    }

    const {paginationFilters, paginationOrder} = this.getContractLogsPaginationFilters(
      indexBound,
      timestampBound,
      timestampOrder
    );

    paginationFilters.forEach((filters) => {
      const nestedConditions = [...baseConditions];

      filters.forEach((filter) => {
        this.updateConditionsAndParamsWithValues(
          filter,
          params,
          nestedConditions,
          ContractLog.getFullName(mapFilterKeyToColumn[filter.key]),
          params.length + 1
        );
      });

      if (!_.isEmpty(nestedConditions)) {
        conditions.push(nestedConditions);
      }
    });

    if (_.isEmpty(conditions) && !_.isEmpty(baseConditions)) {
      conditions = [baseConditions];
    }

    return {
      conditions,
      params,
      paginationOrder,
      timestampOrder,
      indexOrder,
      limit,
    };
  };

  /**
   * Handler function for /contracts/:contractId API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractById = async (req, res) => {
    const {filters, contractId: contractIdParam} = extractContractIdAndFiltersFromValidatedRequest(req);

    const {conditions: timestampConditions, params: timestampParams} =
      extractTimestampConditionsFromContractFilters(filters);

    const {query, params} = getContractByIdOrAddressQuery({timestampConditions, timestampParams, contractIdParam});

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
  getContracts = async (req, res) => {
    utils.validateReq(req);

    // extract filters from query param
    const filters = utils.buildAndValidateFilters(req.query);

    // get sql filter query, params, limit and limit query from query filters
    const {filterQuery, params, order, limit, limitQuery} = await extractSqlFromContractFilters(filters);

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
      {
        [constants.filterKeys.CONTRACT_ID]: lastContractId,
      },
      order
    );

    res.locals[constants.responseDataLabel] = response;
  };
  /**
   * Generates pagination link for the next page
   * @param {Request} req
   * @param {ContractLogs[]} logs
   * @param {string} paginationOrder
   * @param {string} timestampOrder
   * @param {number} limit
   * @returns {string|null}
   */
  generateContractLogsPaginationLink = (req, logs, paginationOrder, timestampOrder, limit) => {
    if (_.isEmpty(logs)) {
      return null;
    }
    const nextLog = timestampOrder === paginationOrder ? _.last(logs) : _.first(logs);
    const lastValueMap = {
      [constants.filterKeys.TIMESTAMP]: {value: nextLog.timestamp, inclusive: true},
      [constants.filterKeys.INDEX]: {value: nextLog.index, inclusive: false},
    };

    return utils.getPaginationLink(req, logs.length !== limit, lastValueMap, paginationOrder);
  };
  /**
   * Handler function for /contracts/:contractId/results/logs API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractLogsById = async (req, res) => {
    // get sql filter query, params, limit and limit query from query filters
    const {filters, contractId: contractIdParam} = extractContractIdAndFiltersFromValidatedRequest(req);
    checkTimestampsForTopics(filters);

    const contractId = await ContractService.computeContractIdFromString(contractIdParam);

    const {conditions, params, timestampOrder, indexOrder, limit, paginationOrder} =
      this.extractContractLogsPaginationQuery(filters, contractId);

    const rows = await ContractService.getContractLogs(
      conditions,
      params,
      timestampOrder,
      indexOrder,
      limit,
      paginationOrder
    );

    const logs = rows.map((row) => new ContractLogViewModel(row));
    const links = {
      next: this.generateContractLogsPaginationLink(req, logs, paginationOrder, timestampOrder, limit),
    };

    res.locals[constants.responseDataLabel] = {logs, links};
  };

  /**
   * Handler function for /contracts/results/logs API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractLogs = async (req, res) => {
    // get sql filter query, params, limit and limit query from query filters
    const filters = utils.buildAndValidateFilters(req.query);
    checkTimestampsForTopics(filters);

    const {conditions, params, timestampOrder, indexOrder, limit, paginationOrder} =
      this.extractContractLogsPaginationQuery(filters);

    const rows = await ContractService.getContractLogs(
      conditions,
      params,
      timestampOrder,
      indexOrder,
      limit,
      paginationOrder
    );

    const logs = rows.map((row) => new ContractLogViewModel(row));
    const links = {
      next: this.generateContractLogsPaginationLink(req, logs, paginationOrder, timestampOrder, limit),
    };

    res.locals[constants.responseDataLabel] = {logs, links};
  };

  /**
   * Handler function for /contracts/:contractId/results API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractResultsById = async (req, res) => {
    const {contractId: contractIdParam, filters} = extractContractIdAndFiltersFromValidatedRequest(req);

    const contractId = await ContractService.computeContractIdFromString(contractIdParam);

    const {conditions, params, order, limit} = this.extractContractResultsByIdQuery(
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
        {
          [constants.filterKeys.TIMESTAMP]: lastContractResultTimestamp,
        },
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
  getContractResultsByTimestamp = async (req, res) => {
    const {timestamp} = getAndValidateContractIdAndConsensusTimestampPathParams(req);

    // retrieve contract result, recordFile and transaction models concurrently
    const [contractResults, recordFile, transaction, contractLogs, contractStateChanges] = await Promise.all([
      ContractService.getContractResultsByTimestamps(timestamp),
      RecordFileService.getRecordFileBlockDetailsFromTimestamp(timestamp),
      TransactionService.getTransactionDetailsFromTimestamp(timestamp),
      ContractService.getContractLogsByTimestamps(timestamp),
      ContractService.getContractStateChangesByTimestamps(timestamp),
    ]);
    if (_.isNil(transaction)) {
      throw new NotFoundError('No correlating transaction');
    }

    if (contractResults.length === 0) {
      throw new NotFoundError();
    }

    if (_.isNil(contractResults[0].callResult)) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getContractResultsByTimestamp returning partial content`);
    }

    res.locals[constants.responseDataLabel] = new ContractResultDetailsViewModel(
      contractResults[0],
      recordFile,
      transaction,
      contractLogs,
      contractStateChanges
    );
  };

  /**
   * Handler function for /contracts/results/:transactionId API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractResultsByTransactionId = async (req, res) => {
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
    const [contractResults, recordFile, contractLogs, contractStateChanges] = await Promise.all([
      ContractService.getContractResultsByTimestamps(transaction.consensusTimestamp),
      RecordFileService.getRecordFileBlockDetailsFromTimestamp(transaction.consensusTimestamp),
      ContractService.getContractLogsByTimestamps(transaction.consensusTimestamp),
      ContractService.getContractStateChangesByTimestamps(transaction.consensusTimestamp),
    ]);

    if (contractResults.length === 0) {
      throw new NotFoundError();
    }

    res.locals[constants.responseDataLabel] = new ContractResultDetailsViewModel(
      contractResults[0],
      recordFile,
      transaction,
      contractLogs,
      contractStateChanges
    );

    if (_.isNil(contractResults[0].callResult)) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getContractResultsByTransactionId returning partial content`);
    }
  };
}

const contractCtrlInstance = new ContractController();

/**
 * Export specific methods from the controller
 * @param {Array<string>} methods Controller method names to export
 * @returns {Object} exported controller methods
 */
const exportControllerMethods = (methods = []) => {
  return methods.reduce((exported, methodName) => {
    if (!contractCtrlInstance[methodName]) {
      throw new NotFoundError(`Method ${methodName} does not exists in ContractController`);
    }

    exported[methodName] = contractCtrlInstance[methodName];

    return exported;
  }, {});
};

module.exports = exportControllerMethods([
  'getContractById',
  'getContracts',
  'getContractLogsById',
  'getContractLogs',
  'getContractResultsById',
  'getContractResultsByTimestamp',
  'getContractResultsByTransactionId',
]);

if (utils.isTestEnv()) {
  Object.assign(
    module.exports,
    exportControllerMethods([
      'extractContractLogsQuery',
      'extractContractResultsByIdQuery',
      'extractContractLogsPaginationQuery',
      'getContractLogsPaginationFilters',
    ]),
    {
      contractResultsByIdParamSupportMap,
      checkTimestampsForTopics,
      extractSqlFromContractFilters,
      extractTimestampConditionsFromContractFilters,
      fileDataQuery,
      formatContractRow,
      getContractByIdOrAddressQuery,
      getContractsQuery,
      getLastNonceParamValue,
      validateContractIdAndConsensusTimestampParam,
      validateContractIdParam,
    }
  );
}
