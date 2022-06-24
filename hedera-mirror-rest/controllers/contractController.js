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

const {
  Contract,
  ContractLog,
  ContractResult,
  TransactionResult,
  RecordFile,
  Transaction,
  TransactionType,
} = require('../model');
const {ContractService, FileDataService, RecordFileService, TransactionService} = require('../service');
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
const wrongNonceTransactionResult = TransactionResult.getProtoId('WRONG_NONCE');
const ethereumTransactionType = TransactionType.getProtoId('ETHEREUMTRANSACTION');

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
    // Passing the entire contract id instead of just the num part from shard.realm.num
    // The contract ID string can be shard.realm.num, realm.num when shard=0 in application.yml or the encoded entity ID string.
    const encodedId = EntityId.parse(contractIdParam).getEncodedId();
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
    ${FileDataService.getContractInitCodeFiledataQuery()}
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

/**
 * If 2 timestamp query filters are present with the same value
 * Overwrites the Request query object to contain a single timestamp filter
 * eg. timestamp=gte:A&timestamp=lte:A -> timestamp=A
 *
 * @param {Request} req
 * @returns {void}
 */
const alterTimestampRangeInReq = (req) => {
  const timestamps = utils.buildAndValidateFilters(req.query).filter((f) => f.key === constants.filterKeys.TIMESTAMP);
  const ops = [utils.opsMap.gte, utils.opsMap.lte];
  const firstTimestamp = _.first(timestamps);
  const secondTimestamp = _.last(timestamps);

  // checks the special cases only
  // all other checks will be handled by the other logic
  if (
    timestamps.length === 2 &&
    firstTimestamp.value === secondTimestamp.value &&
    firstTimestamp.operator !== secondTimestamp.operator &&
    ops.includes(firstTimestamp.operator) &&
    ops.includes(secondTimestamp.operator)
  ) {
    req.query[constants.filterKeys.TIMESTAMP] = utils.nsToSecNs(firstTimestamp.value);
  }
};

/**
 * Modifies the default filterValidityChecks logic to support special rules for operators of BLOCK_NUMBER
 * @param {String} param Parameter to be validated
 * @param {String} opAndVal operator:value to be validated
 * @return {Boolean} true if the parameter is valid. false otherwise
 */
const contractResultsFilterValidityChecks = (param, op, val) => {
  const ret = utils.filterValidityChecks(param, op, val);
  if (ret && param === constants.filterKeys.BLOCK_NUMBER) {
    return op === constants.queryParamOperators.eq;
  }
  return ret;
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
  return {timestamp: utils.parseTimestampParam(consensusTimestamp)};
};

const extractContractIdAndFiltersFromValidatedRequest = (req) => {
  // extract filters from query param
  const contractId = getAndValidateContractIdRequestPathParam(req);

  const filters = utils.buildAndValidateFilters(req.query, contractResultsFilterValidityChecks);

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
  extractContractResultsByIdQuery = async (filters, contractId) => {
    let limit = defaultLimit;
    let order = constants.orderFilterValues.DESC;
    const conditions = [];
    const params = [];
    if (contractId !== '') {
      conditions.push(`${ContractResult.getFullName(ContractResult.CONTRACT_ID)} = $1`);
      params.push(contractId);
    }

    let internal = false;

    const contractResultFromFullName = ContractResult.getFullName(ContractResult.PAYER_ACCOUNT_ID);
    const contractResultFromInValues = [];

    const contractResultTimestampFullName = ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP);
    const contractResultTimestampInValues = [];

    const transactionIndexFullName = Transaction.getFullName(Transaction.INDEX);
    const transactionIndexInValues = [];

    let blockFilter;

    const supportedParams = [
      constants.filterKeys.FROM,
      constants.filterKeys.TIMESTAMP,
      constants.filterKeys.BLOCK_NUMBER,
      constants.filterKeys.BLOCK_HASH,
      constants.filterKeys.TRANSACTION_INDEX,
      constants.filterKeys.INTERNAL,
      constants.filterKeys.LIMIT,
      constants.filterKeys.ORDER,
    ];
    for (const filter of filters) {
      if (!supportedParams.includes(filter.key)) {
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
        case constants.filterKeys.BLOCK_NUMBER:
        case constants.filterKeys.BLOCK_HASH:
          blockFilter = filter;
          break;
        case constants.filterKeys.TRANSACTION_INDEX:
          this.updateConditionsAndParamsWithInValues(
            filter,
            transactionIndexInValues,
            params,
            conditions,
            transactionIndexFullName,
            conditions.length + 1
          );
          break;
        case constants.filterKeys.INTERNAL:
          internal = filter.value;
          break;
        default:
          break;
      }
    }

    if (!internal) {
      params.push(0);
      conditions.push(`${Transaction.getFullName(Transaction.NONCE)} = $${params.length}`);
    }

    if (blockFilter) {
      let blockData;
      if (blockFilter.key === constants.filterKeys.BLOCK_NUMBER) {
        blockData = await RecordFileService.getRecordFileBlockDetailsFromIndex(blockFilter.value);
      } else {
        blockData = await RecordFileService.getRecordFileBlockDetailsFromHash(blockFilter.value);
      }

      if (blockData) {
        const conStartColName = _.camelCase(RecordFile.CONSENSUS_START);
        const conEndColName = _.camelCase(RecordFile.CONSENSUS_END);

        this.updateConditionsAndParamsWithInValues(
          {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.gte, value: blockData[conStartColName]},
          contractResultTimestampInValues,
          params,
          conditions,
          contractResultTimestampFullName,
          conditions.length + 1
        );
        this.updateConditionsAndParamsWithInValues(
          {key: constants.filterKeys.TIMESTAMP, operator: utils.opsMap.lte, value: blockData[conEndColName]},
          contractResultTimestampInValues,
          params,
          conditions,
          contractResultTimestampFullName,
          conditions.length + 1
        );
      } else {
        throw new NotFoundError();
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
    this.updateQueryFiltersWithInValues(params, conditions, transactionIndexInValues, transactionIndexFullName);

    return {
      conditions: conditions,
      params: params,
      order: order,
      limit: limit,
    };
  };

  validateContractLogsBounds = (timestampBound, indexBound) => {
    for (const bound of [timestampBound, indexBound]) {
      if (bound.hasBound() && bound.hasEqual()) {
        throw new InvalidArgumentError(`Can't support both range and equal`);
      }
    }

    if (timestampBound.isEmpty() && !indexBound.isEmpty()) {
      throw new InvalidArgumentError(
        `Cannot search by ${constants.filterKeys.INDEX} without a ${constants.filterKeys.TIMESTAMP} parameter filter`
      );
    }

    if (
      indexBound.hasLower() &&
      !timestampBound.hasEqual() &&
      (!timestampBound.hasLower() || timestampBound.lower.operator === utils.opsMap.gt)
    ) {
      // invalid ops: index >/>= & timestamp </<=/>
      throw new InvalidArgumentError(`Timestamp must have gte or eq operator`);
    }

    if (
      indexBound.hasUpper() &&
      !timestampBound.hasEqual() &&
      (!timestampBound.hasUpper() || timestampBound.upper.operator === utils.opsMap.lt)
    ) {
      // invalid ops: index </<= & timestamp >/>=/<
      throw new InvalidArgumentError(`Timestamp must have lte or eq operator`);
    }

    if (indexBound.hasEqual() && !timestampBound.hasEqual()) {
      throw new InvalidArgumentError(`Timestamp must have eq operator`);
    }
  };

  /**
   * Extends base getLowerFilters function and adds a special case to
   * extract contract logs lower filters
   * @param {Bound} timestampBound
   * @param {Bound} indexBound
   * @returns {{key: string, operator: string, value: *}[]}
   */
  getContractLogsLowerFilters = (timestampBound, indexBound) => {
    let filters = this.getLowerFilters(timestampBound, indexBound);

    if (!_.isEmpty(filters)) {
      return filters;
    }

    // timestamp has equal and index has bound/equal
    // only lower bound is used, inner and upper are not needed
    if (timestampBound.hasEqual() && (indexBound.hasBound() || indexBound.hasEqual())) {
      filters = [timestampBound.equal, indexBound.lower, indexBound.equal, indexBound.upper];
    }

    return filters.filter((f) => !_.isNil(f));
  };
  /**
   * Extracts multiple queries to be combined in union
   *
   * @param {[]} filters parsed and validated filters
   * @param {string|undefined} contractId encoded contract ID
   * @return {{bounds: {string: Bound},boundKeys: {{primary:string,secondary:string}}, lower: *[], inner: *[], upper: *[], conditions: [], params: [], timestampOrder: 'asc'|'desc', indexOrder: 'asc'|'desc', limit: number}}
   */
  extractContractLogsMultiUnionQuery = (filters, contractId) => {
    let limit = defaultLimit;
    let timestampOrder = constants.orderFilterValues.DESC;
    let indexOrder = constants.orderFilterValues.DESC;
    const conditions = [];
    const params = [];

    if (contractId) {
      conditions.push(`${ContractLog.getFullName(ContractLog.CONTRACT_ID)} = $1`);
      params.push(contractId);
    }

    const indexBound = new Bound();
    const timestampBound = new Bound();
    const bounds = {
      [constants.filterKeys.INDEX]: indexBound,
      [constants.filterKeys.TIMESTAMP]: timestampBound,
    };
    const boundKeys = {primary: constants.filterKeys.TIMESTAMP, secondary: constants.filterKeys.INDEX};
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
        case constants.filterKeys.TIMESTAMP:
          if (filter.operator === utils.opsMap.ne) {
            throw new InvalidArgumentError(`Not equals operator not supported for ${filter.key} param`);
          }
          bounds[filter.key].parse(filter);
          break;
        case constants.filterKeys.LIMIT:
          limit = filter.value;
          break;
        case constants.filterKeys.ORDER:
          timestampOrder = filter.value;
          indexOrder = filter.value;
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

    this.validateContractLogsBounds(timestampBound, indexBound);

    // update query with repeated values
    Object.keys(keyFullNames).forEach((filterKey) => {
      this.updateQueryFiltersWithInValues(params, conditions, inValues[filterKey], keyFullNames[filterKey]);
    });

    return {
      bounds,
      boundKeys,
      lower: this.getContractLogsLowerFilters(timestampBound, indexBound),
      inner: this.getInnerFilters(timestampBound, indexBound),
      upper: this.getUpperFilters(timestampBound, indexBound),
      conditions,
      params,
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
    if (utils.conflictingPathParam(req, 'contractId', 'results')) {
      return;
    }

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
   * Handler function for /contracts/:contractId/results/logs API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractLogsById = async (req, res) => {
    alterTimestampRangeInReq(req);
    // get sql filter query, params, limit and limit query from query filters
    const {filters, contractId: contractIdParam} = extractContractIdAndFiltersFromValidatedRequest(req);
    checkTimestampsForTopics(filters);

    const contractId = await ContractService.computeContractIdFromString(contractIdParam);

    const query = this.extractContractLogsMultiUnionQuery(filters, contractId);

    const rows = await ContractService.getContractLogs(query);

    const logs = rows.map((row) => new ContractLogViewModel(row));

    res.locals[constants.responseDataLabel] = {
      logs,
      links: {
        next: this.getPaginationLink(req, logs, query.bounds, query.boundKeys, query.limit, query.timestampOrder),
      },
    };
  };

  /**
   * Handler function for /contracts/results/logs API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractLogs = async (req, res) => {
    alterTimestampRangeInReq(req);
    // get sql filter query, params, limit and limit query from query filters
    const filters = utils.buildAndValidateFilters(req.query);
    checkTimestampsForTopics(filters);

    const query = this.extractContractLogsMultiUnionQuery(filters);

    const rows = await ContractService.getContractLogs(query);

    const logs = rows.map((row) => new ContractLogViewModel(row));

    res.locals[constants.responseDataLabel] = {
      logs,
      links: {
        next: this.getPaginationLink(req, logs, query.bounds, query.boundKeys, query.limit, query.timestampOrder),
      },
    };
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

    const {conditions, params, order, limit} = await this.extractContractResultsByIdQuery(filters, contractId);

    const rows = await ContractService.getContractResultsByIdAndFilters(conditions, params, order, limit);
    const response = {
      results: rows.map((row) => new ContractResultViewModel(row)),
      links: {
        next: null,
      },
    };

    if (!_.isEmpty(response.results)) {
      const lastRow = _.last(response.results);
      const lastContractResultTimestamp = lastRow.timestamp;
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

    let fileData = null;
    if (!_.isNil(transaction.callDataId)) {
      fileData = await FileDataService.getLatestFileDataContents(transaction.callDataId, {whereQuery: []});
    }

    if (_.isNil(contractResults[0].callResult)) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getContractResultsByTimestamp returning partial content`);
    }

    this.setContractResultsResponse(
      res,
      contractResults[0],
      recordFile,
      transaction,
      contractLogs,
      contractStateChanges,
      fileData
    );
  };

  /**
   * Handler function for /contracts/results API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractResults = async (req, res) => {
    const filters = utils.buildAndValidateFilters(req.query, contractResultsFilterValidityChecks);
    const {conditions, params, order, limit} = await this.extractContractResultsByIdQuery(filters, '');

    const rows = await ContractService.getContractResultsByIdAndFilters(conditions, params, order, limit);
    const response = {
      results: rows.map((row) => new ContractResultViewModel(row, 'hash' in row ? row.hash : null)),
      links: {
        next: null,
      },
    };

    if (!_.isEmpty(response.results)) {
      const lastRow = _.last(response.results);
      const lastContractResultTimestamp = lastRow.timestamp;
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
   * Handler function for /contracts/results/:transactionId API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractResultsByTransactionIdOrHash = async (req, res) => {
    if (utils.conflictingPathParam(req, 'transactionIdOrHash', 'logs')) {
      return;
    }

    utils.validateReq(req);

    // extract filters from query param
    const {transactionIdOrHash} = req.params;
    let transactions;
    let shouldMockContractResults = false;
    // When getting transactions, exclude duplicate transactions. there can be at most one
    if (utils.isValidEthHash(transactionIdOrHash)) {
      const ethHash = Buffer.from(transactionIdOrHash.replace('0x', ''), 'hex');
      // get transactions using ethereum hash and nonce
      transactions = await TransactionService.getTransactionDetailsFromEthHash(
        ethHash,
        [duplicateTransactionResult, wrongNonceTransactionResult],
        1
      );
    } else {
      const transactionId = TransactionId.fromString(transactionIdOrHash);
      const nonce = getLastNonceParamValue(req.query);
      // get transactions using id and nonce
      transactions = await TransactionService.getTransactionDetailsFromTransactionId(
        transactionId,
        nonce,
        duplicateTransactionResult
      );
    }

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

    let fileData = null;
    if (!_.isNil(transaction.callDataId)) {
      fileData = await FileDataService.getLatestFileDataContents(transaction.callDataId, {whereQuery: []});
    }
    transaction.callData = null;
    if (contractResults.length === 0) {
      // should mock contract results only if:
      // - contract results are empty
      // - transaction type = ethereum transaction
      shouldMockContractResults = transaction.transactionType.toString() === ethereumTransactionType;
      if (shouldMockContractResults) {
        contractResults.push(this.getMockedContractResultByTransaction(transaction));
      } else {
        throw new NotFoundError();
      }
    }

    this.setContractResultsResponse(
      res,
      contractResults[0],
      recordFile,
      transaction,
      contractLogs,
      contractStateChanges,
      fileData,
      shouldMockContractResults
    );

    if (_.isNil(contractResults[0].callResult)) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getContractResultsByTransactionId returning partial content`);
    }
  };

  setContractResultsResponse = (
    res,
    contractResult,
    recordFile,
    transaction,
    contractLogs,
    contractStateChanges,
    fileData,
    shouldMockContractResults
  ) => {
    res.locals[constants.responseDataLabel] = new ContractResultDetailsViewModel(
      contractResult,
      recordFile,
      transaction,
      contractLogs,
      contractStateChanges,
      fileData,
      shouldMockContractResults
    );
  };

  getMockedContractResultByTransaction = (transaction) => {
    return {
      bloom: Buffer.alloc(256),
      callResult: Buffer.alloc(0),
      createdContractIds: [],
      functionParameters: [],
      payerAccountId: transaction.payerAccountId,
      errorMessage: TransactionResult.getName(transaction.result),
      consensusTimestamp: transaction.consensusTimestamp,
      contractId: transaction.toAddress ? transaction.toAddress.toString('hex') : null,
      gasUsed: 0,
    };
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
  'getContractResults',
  'getContractResultsById',
  'getContractResultsByTimestamp',
  'getContractResultsByTransactionIdOrHash',
]);

if (utils.isTestEnv()) {
  Object.assign(
    module.exports,
    exportControllerMethods(['extractContractResultsByIdQuery', 'extractContractLogsMultiUnionQuery']),
    {
      checkTimestampsForTopics,
      extractSqlFromContractFilters,
      extractTimestampConditionsFromContractFilters,
      formatContractRow,
      getContractByIdOrAddressQuery,
      getContractsQuery,
      getLastNonceParamValue,
      validateContractIdAndConsensusTimestampParam,
      validateContractIdParam,
      alterTimestampRangeInReq,
    }
  );
}
