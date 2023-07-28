/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import BaseController from './baseController';
import Bound from './bound';
import {getResponseLimit} from '../config';
import {filterKeys, httpStatusCodes, orderFilterValues, queryParamOperators, responseDataLabel} from '../constants';
import EntityId from '../entityId';
import {InvalidArgumentError, NotFoundError} from '../errors';
import {
  Contract,
  ContractLog,
  ContractResult,
  ContractState,
  ContractStateChange,
  Entity,
  RecordFile,
  TransactionResult,
  TransactionType,
} from '../model';
import {ContractService, EntityService, FileDataService, RecordFileService, TransactionService} from '../service';
import TransactionId from '../transactionId';
import * as utils from '../utils';
import {
  ContractActionViewModel,
  ContractBytecodeViewModel,
  ContractLogViewModel,
  ContractResultDetailsViewModel,
  ContractResultViewModel,
  ContractStateViewModel,
  ContractViewModel,
} from '../viewmodel';

const contractSelectFields = [
  Entity.AUTO_RENEW_ACCOUNT_ID,
  Entity.AUTO_RENEW_PERIOD,
  Entity.CREATED_TIMESTAMP,
  Entity.DELETED,
  Entity.EVM_ADDRESS,
  Entity.EXPIRATION_TIMESTAMP,
  Entity.ID,
  Entity.KEY,
  Entity.MAX_AUTOMATIC_TOKEN_ASSOCIATIONS,
  Entity.MEMO,
  Entity.ETHEREUM_NONCE,
  Entity.OBTAINER_ID,
  Entity.PERMANENT_REMOVAL,
  Entity.PROXY_ACCOUNT_ID,
  Entity.TIMESTAMP_RANGE,
].map((column) => Entity.getFullName(column));
contractSelectFields.push(Contract.getFullName(Contract.FILE_ID));
const contractWithBytecodeSelectFields = [
  ...contractSelectFields,
  Contract.getFullName(Contract.INITCODE),
  Contract.getFullName(Contract.RUNTIME_BYTECODE),
];
const {default: defaultLimit} = getResponseLimit();

const contractCallType = Number(TransactionType.getProtoId('CONTRACTCALL'));
const contractCreateType = Number(TransactionType.getProtoId('CONTRACTCREATEINSTANCE'));
const ethereumTransactionType = Number(TransactionType.getProtoId('ETHEREUMTRANSACTION'));
const duplicateTransactionResult = TransactionResult.getProtoId('DUPLICATE_TRANSACTION');
const wrongNonceTransactionResult = TransactionResult.getProtoId('WRONG_NONCE');

/**
 * Extracts the sql where clause, params, order and limit values to be used from the provided contract query
 * param filters
 * @param filters
 * @return {{filterQuery: string, params: number[], order: string, limit: number, limitQuery: string}}
 */
const extractSqlFromContractFilters = async (filters) => {
  const filterQuery = {
    filterQuery: `where e.type = 'CONTRACT'`,
    params: [defaultLimit],
    order: orderFilterValues.DESC,
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
  const contractIdFullName = Entity.getFullName(Entity.ID);

  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.CONTRACT_ID:
        const contractIdValue = await ContractService.computeContractIdFromString(filter.value);

        if (filter.operator === utils.opsMap.eq) {
          // aggregate '=' conditions and use the sql 'in' operator
          contractIdInValues.push(contractIdValue);
        } else {
          params.push(contractIdValue);
          conditions.push(`${contractIdFullName}${filter.operator}$${params.length}`);
        }
        break;
      case filterKeys.LIMIT:
        filterQuery.limit = filter.value;
        break;
      case filterKeys.ORDER:
        filterQuery.order = filter.value;
        break;
    }
  }

  if (contractIdInValues.length !== 0) {
    // add the condition 'e.id in ()'
    const start = params.length + 1; // start is the next positional index
    params.push(...contractIdInValues);
    const positions = _.range(contractIdInValues.length)
      .map((position) => position + start)
      .map((position) => `$${position}`);
    conditions.push(`${contractIdFullName} in (${positions})`);
  }

  if (conditions.length !== 0) {
    filterQuery.filterQuery += ` and ${conditions.join(' and ')}`;
  }

  // add limit
  params.push(filterQuery.limit);
  filterQuery.limitQuery = `limit $${params.length}`;
  filterQuery.params = params;

  return filterQuery;
};

/**
 * Formats a contract row from database to the contract view model
 * @param row
 * @param viewModel
 * @return {ContractViewModel | ContractBytecodeViewModel}
 */
const formatContractRow = (row, viewModel) => {
  const contract = new Contract(row);
  const entity = new Entity(row);
  return new viewModel(contract, entity);
};

/**
 * Gets the query by contract id for the specified table, optionally with timestamp conditions
 * @param table
 * @param conditions
 * @return {string}
 */
const getContractByIdOrAddressQueryForTable = (table, conditions) => {
  return [
    `select ${contractWithBytecodeSelectFields}`,
    `from ${table} ${Entity.tableAlias}`,
    `left join ${Contract.tableName} ${Contract.tableAlias}`,
    `on ${Entity.getFullName(Entity.ID)} = ${Contract.getFullName(Contract.ID)}`,
    `where e.type = 'CONTRACT' and ${conditions.join(' and ')}`,
  ].join('\n');
};

/**
 * Gets the sql query for a specific contract, optionally with timestamp condition
 * @param timestampConditions
 * @return {query: string, params: any[]}
 */
const getContractByIdOrAddressContractEntityQuery = ({timestampConditions, timestampParams, contractIdParam}) => {
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
    conditions.push(`${Entity.getFullName(Entity.ID)} = $${params.length}`);
  }

  const tableUnionQueries = [getContractByIdOrAddressQueryForTable(Entity.tableName, conditions)];
  if (timestampConditions.length !== 0) {
    // if there is timestamp condition, union the result from both tables
    tableUnionQueries.push(
      'union',
      getContractByIdOrAddressQueryForTable(Entity.historyTableName, conditions),
      `order by ${Entity.TIMESTAMP_RANGE} desc`,
      `limit 1`
    );
  }

  return {
    query: tableUnionQueries.join('\n'),
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
    `from ${Entity.tableName} ${Entity.tableAlias}`,
    `left join ${Contract.tableName} ${Contract.tableAlias}`,
    `on ${Entity.getFullName(Entity.ID)} = ${Contract.getFullName(Contract.ID)}`,
    whereQuery,
    `order by ${Entity.getFullName(Entity.ID)} ${order}`,
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
  const timestamps = utils
    .buildAndValidateFilters(req.query, acceptedContractLogParameters)
    .filter((f) => f.key === filterKeys.TIMESTAMP);
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
    req.query[filterKeys.TIMESTAMP] = utils.nsToSecNs(firstTimestamp.value);
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
  if (ret && param === filterKeys.BLOCK_NUMBER) {
    return op === queryParamOperators.eq;
  }
  return ret;
};

const checkTimestampsForTopics = (filters) => {
  let hasTopic = false;
  const timestampFilters = [];
  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.TOPIC0:
      case filterKeys.TOPIC1:
      case filterKeys.TOPIC2:
      case filterKeys.TOPIC3:
        hasTopic = true;
        break;
      case filterKeys.TIMESTAMP:
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
  const key = filterKeys.NONCE;
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
    throw InvalidArgumentError.forParams(filterKeys.CONTRACTID);
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
    params.push(filterKeys.CONTRACTID);
  }
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    params.push(filterKeys.TIMESTAMP);
  }

  if (params.length > 0) {
    throw InvalidArgumentError.forParams(params);
  }
};

const getAndValidateContractIdAndConsensusTimestampPathParams = async (req) => {
  const {consensusTimestamp, contractId} = req.params;
  validateContractIdAndConsensusTimestampParam(consensusTimestamp, contractId);
  utils.validateReq(req);
  const encodedContractId = await ContractService.computeContractIdFromString(contractId);
  return {contractId: encodedContractId, timestamp: utils.parseTimestampParam(consensusTimestamp)};
};

const extractContractIdAndFiltersFromValidatedRequest = (req, acceptedParameters) => {
  // extract filters from query param
  const contractId = getAndValidateContractIdRequestPathParam(req);
  const filters = utils.buildAndValidateFilters(req.query, acceptedParameters, contractResultsFilterValidityChecks);

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
   * @return {Promise<{conditions: [], params: [], order: 'asc'|'desc', limit: number}>}
   */
  extractContractResultsByIdQuery = async (filters, contractId) => {
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    const conditions = [];
    const params = [];
    if (contractId !== '') {
      conditions.push(`${ContractResult.getFullName(ContractResult.CONTRACT_ID)} = $1`);
      params.push(contractId);
    }

    let internal = false;

    const contractResultSenderFullName = ContractResult.getFullName(ContractResult.SENDER_ID);
    const contractResultFromInValues = [];

    const contractResultTimestampFullName = ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP);
    const contractResultTimestampInValues = [];

    const transactionIndexFullName = ContractResult.getFullName(ContractResult.TRANSACTION_INDEX);
    const transactionIndexInValues = [];

    let blockFilter;

    const supportedParams = [
      filterKeys.FROM,
      filterKeys.TIMESTAMP,
      filterKeys.BLOCK_NUMBER,
      filterKeys.BLOCK_HASH,
      filterKeys.TRANSACTION_INDEX,
      filterKeys.INTERNAL,
      filterKeys.LIMIT,
      filterKeys.ORDER,
    ];
    for (const filter of filters) {
      if (!supportedParams.includes(filter.key)) {
        // param not supported for current endpoint
        continue;
      }

      switch (filter.key) {
        case filterKeys.FROM:
          // Evm addresses are not parsed by utils.buildAndValidateFilters, so they are converted to encoded ids here.
          if (EntityId.isValidEvmAddress(filter.value)) {
            filter.value = await EntityService.getEncodedId(filter.value);
          }
          this.updateConditionsAndParamsWithInValues(
            filter,
            contractResultFromInValues,
            params,
            conditions,
            contractResultSenderFullName,
            conditions.length + 1
          );
          break;
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        case filterKeys.TIMESTAMP:
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
        case filterKeys.BLOCK_NUMBER:
        case filterKeys.BLOCK_HASH:
          blockFilter = filter;
          break;
        case filterKeys.TRANSACTION_INDEX:
          this.updateConditionsAndParamsWithInValues(
            filter,
            transactionIndexInValues,
            params,
            conditions,
            transactionIndexFullName,
            conditions.length + 1
          );
          break;
        case filterKeys.INTERNAL:
          internal = filter.value;
          break;
        default:
          break;
      }
    }

    if (!internal) {
      params.push(0);
      conditions.push(`${ContractResult.getFullName(ContractResult.TRANSACTION_NONCE)} = $${params.length}`);
    }

    if (blockFilter) {
      let blockData;
      if (blockFilter.key === filterKeys.BLOCK_NUMBER) {
        blockData = await RecordFileService.getRecordFileBlockDetailsFromIndex(blockFilter.value);
      } else {
        blockData = await RecordFileService.getRecordFileBlockDetailsFromHash(blockFilter.value);
      }

      if (blockData) {
        const conStartColName = _.camelCase(RecordFile.CONSENSUS_START);
        const conEndColName = _.camelCase(RecordFile.CONSENSUS_END);

        this.updateConditionsAndParamsWithInValues(
          {key: filterKeys.TIMESTAMP, operator: utils.opsMap.gte, value: blockData[conStartColName]},
          contractResultTimestampInValues,
          params,
          conditions,
          contractResultTimestampFullName,
          conditions.length + 1
        );
        this.updateConditionsAndParamsWithInValues(
          {key: filterKeys.TIMESTAMP, operator: utils.opsMap.lte, value: blockData[conEndColName]},
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
    this.updateQueryFiltersWithInValues(params, conditions, contractResultFromInValues, contractResultSenderFullName);
    this.updateQueryFiltersWithInValues(
      params,
      conditions,
      contractResultTimestampInValues,
      contractResultTimestampFullName
    );
    this.updateQueryFiltersWithInValues(params, conditions, transactionIndexInValues, transactionIndexFullName);

    return {
      conditions,
      params,
      order,
      limit,
    };
  };

  validateContractLogsBounds = (bounds) => {
    if (bounds.secondary.hasEqual() && !bounds.primary.hasEqual()) {
      throw new InvalidArgumentError(`${bounds.primary.filterKey} must have eq operator`);
    }
    this.validateBounds(bounds);
  };

  /**
   * Extends base getLowerFilters function and adds a special case to
   * extract contract logs lower filters
   * @param {Bound}[] bounds
   * @returns {{key: string, operator: string, value: *}[]}
   */
  getContractLogsLowerFilters = (bounds) => {
    let filters = this.getLowerFilters(bounds);

    if (!_.isEmpty(filters)) {
      return filters;
    }

    const timestampBound = bounds.primary;
    const indexBound = bounds.secondary;
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
   * @return {{bounds: {string: Bound}, lower: *[], inner: *[], upper: *[], conditions: [], params: [], timestampOrder: 'asc'|'desc', indexOrder: 'asc'|'desc', limit: number}}
   */
  extractContractLogsMultiUnionQuery = (filters, contractId) => {
    let limit = defaultLimit;
    let timestampOrder = orderFilterValues.DESC;
    let indexOrder = orderFilterValues.DESC;
    const conditions = [];
    const params = [];

    if (contractId) {
      conditions.push(`${ContractLog.getFullName(ContractLog.CONTRACT_ID)} = $1`);
      params.push(contractId);
    }

    const bounds = {
      primary: new Bound(filterKeys.TIMESTAMP),
      secondary: new Bound(filterKeys.INDEX),
    };
    const keyFullNames = {
      [filterKeys.TOPIC0]: ContractLog.getFullName(ContractLog.TOPIC0),
      [filterKeys.TOPIC1]: ContractLog.getFullName(ContractLog.TOPIC1),
      [filterKeys.TOPIC2]: ContractLog.getFullName(ContractLog.TOPIC2),
      [filterKeys.TOPIC3]: ContractLog.getFullName(ContractLog.TOPIC3),
    };

    const inValues = {
      [filterKeys.TOPIC0]: [],
      [filterKeys.TOPIC1]: [],
      [filterKeys.TOPIC2]: [],
      [filterKeys.TOPIC3]: [],
    };

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.INDEX:
          bounds.secondary.parse(filter);
          break;
        case filterKeys.TIMESTAMP:
          bounds.primary.parse(filter);
          break;
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          timestampOrder = filter.value;
          indexOrder = filter.value;
          break;
        case filterKeys.TOPIC0:
        case filterKeys.TOPIC1:
        case filterKeys.TOPIC2:
        case filterKeys.TOPIC3:
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

    this.validateContractLogsBounds(bounds);

    // update query with repeated values
    Object.keys(keyFullNames).forEach((filterKey) => {
      this.updateQueryFiltersWithInValues(params, conditions, inValues[filterKey], keyFullNames[filterKey]);
    });

    return {
      bounds,
      lower: this.getContractLogsLowerFilters(bounds),
      inner: this.getInnerFilters(bounds),
      upper: this.getUpperFilters(bounds),
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

    const {filters, contractId: contractIdParam} = extractContractIdAndFiltersFromValidatedRequest(
      req,
      acceptedContractByIdParameters
    );

    const {conditions: timestampConditions, params: timestampParams} =
      utils.extractTimestampRangeConditionFilters(filters);

    const {query, params} = getContractByIdOrAddressContractEntityQuery({
      timestampConditions,
      timestampParams,
      contractIdParam,
    });

    if (logger.isTraceEnabled()) {
      logger.trace(`getContractById query: ${query}, params: ${params}`);
    }

    const {rows} = await pool.queryQuietly(query, params);
    if (rows.length !== 1) {
      throw new NotFoundError();
    }
    const contract = rows[0];
    if (contract.file_id !== null) {
      contract.bytecode = await FileDataService.getFileData(contract.file_id, contract.created_timestamp);
    } else {
      contract.bytecode = contract.initcode?.toString('hex');
    }
    res.locals[responseDataLabel] = formatContractRow(contract, ContractBytecodeViewModel);
  };

  /**
   * Handler function for /contracts API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContracts = async (req, res) => {
    // extract filters from query param
    const filters = utils.buildAndValidateFilters(req.query, acceptedContractParameters);

    // get sql filter query, params, limit and limit query from query filters
    const {filterQuery, params, order, limit, limitQuery} = await extractSqlFromContractFilters(filters);

    const query = getContractsQuery(filterQuery, limitQuery, order);
    if (logger.isTraceEnabled()) {
      logger.trace(`getContracts query: ${query}, params: ${params}`);
    }

    const {rows} = await pool.queryQuietly(query, params);
    logger.debug(`getContracts returning ${rows.length} entries`);

    const response = {
      contracts: rows.map((row) => formatContractRow(row, ContractViewModel)),
      links: {},
    };
    const lastRow = _.last(response.contracts);
    const lastContractId = lastRow !== undefined ? lastRow.contract_id : null;
    response.links.next = utils.getPaginationLink(
      req,
      response.contracts.length !== limit,
      {
        [filterKeys.CONTRACT_ID]: lastContractId,
      },
      order
    );

    res.locals[responseDataLabel] = response;
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
    const {filters, contractId: contractIdParam} = extractContractIdAndFiltersFromValidatedRequest(
      req,
      acceptedContractLogParameters
    );
    checkTimestampsForTopics(filters);

    const contractId = await ContractService.computeContractIdFromString(contractIdParam);

    const query = this.extractContractLogsMultiUnionQuery(filters, contractId);

    const rows = await ContractService.getContractLogs(query);

    const logs = rows.map((row) => new ContractLogViewModel(row));

    res.locals[responseDataLabel] = {
      logs,
      links: {
        next: this.getPaginationLink(req, logs, query.bounds, query.limit, query.timestampOrder),
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
    const filters = utils.buildAndValidateFilters(req.query, acceptedContractLogParameters);
    checkTimestampsForTopics(filters);

    const query = this.extractContractLogsMultiUnionQuery(filters);

    const rows = await ContractService.getContractLogs(query);

    const logs = rows.map((row) => new ContractLogViewModel(row));

    res.locals[responseDataLabel] = {
      logs,
      links: {
        next: this.getPaginationLink(req, logs, query.bounds, query.limit, query.timestampOrder),
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
    const {contractId: contractIdParam, filters} = extractContractIdAndFiltersFromValidatedRequest(
      req,
      acceptedContractResultsParameters
    );

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
          [filterKeys.TIMESTAMP]: lastContractResultTimestamp,
        },
        order
      );
    }

    res.locals[responseDataLabel] = response;
  };

  async extractContractStateByIdQuery(filters, contractId) {
    let limit = defaultLimit;
    let order = orderFilterValues.ASC;
    let timestamp = false;
    const conditions = [this.getFilterWhereCondition(ContractState.CONTRACT_ID, {operator: '=', value: contractId})];
    const slotInValues = [];

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        case filterKeys.TIMESTAMP:
          if (filter.operator === utils.opsMap.eq) {
            conditions.push(
              this.getFilterWhereCondition(ContractStateChange.CONSENSUS_TIMESTAMP, {
                operator: '<=',
                value: filter.value,
              })
            );
            timestamp = true;
          }
          break;
        case filterKeys.SLOT:
          let slot = utils.formatSlot(filter.value);
          //we need this additional conversion, because there is inconsistency between colums slot in table contract_state and contract_state_change.
          if (timestamp) {
            slot = utils.formatSlot(filter.value, true);
          }
          if (filter.operator === utils.opsMap.eq) {
            slotInValues.push(slot);
          } else {
            conditions.push(
              this.getFilterWhereCondition(ContractState.SLOT, {
                operator: filter.operator,
                value: slot,
              })
            );
          }
          break;
        default:
          break;
      }
    }

    if (slotInValues.length !== 0) {
      conditions.push(this.getFilterWhereCondition(ContractState.SLOT, {operator: 'in', value: slotInValues}));
    }

    return {
      conditions,
      order,
      limit,
      timestamp,
    };
  }

  /**
   * Handler function for /contracts/:contractId/state API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractStateById = async (req, res) => {
    const {contractId: contractIdParam, filters} = extractContractIdAndFiltersFromValidatedRequest(
      req,
      acceptedContractStateParameters
    );
    const contractId = await ContractService.computeContractIdFromString(contractIdParam);
    const {conditions, order, limit, timestamp} = await this.extractContractStateByIdQuery(filters, contractId);
    const rows = await ContractService.getContractStateByIdAndFilters(conditions, order, limit, timestamp);
    const state = rows.map((row) => new ContractStateViewModel(row));

    let nextLink = null;
    if (state.length) {
      const lastRow = _.last(state);
      const lastSlot = lastRow.slot;
      nextLink = utils.getPaginationLink(
        req,
        state.length !== limit,
        {
          [filterKeys.SLOT]: lastSlot,
        },
        order
      );
    }

    res.locals[responseDataLabel] = {
      state,
      links: {
        next: nextLink,
      },
    };
  };

  /**
   * Handler function for /contracts/:contractId/results/:consensusTimestamp API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractResultsByTimestamp = async (req, res) => {
    const {contractId, timestamp} = await getAndValidateContractIdAndConsensusTimestampPathParams(req);

    // retrieve contract result, recordFile and transaction models concurrently
    const [ethTransactions, contractResults, recordFile, contractLogs, contractStateChanges] = await Promise.all([
      TransactionService.getEthTransactionByTimestamp(timestamp),
      ContractService.getContractResultsByTimestamps(timestamp),
      RecordFileService.getRecordFileBlockDetailsFromTimestamp(timestamp),
      ContractService.getContractLogsByTimestamps(timestamp),
      ContractService.getContractStateChangesByTimestamps(timestamp, contractId),
    ]);

    if (contractResults.length === 0) {
      throw new NotFoundError();
    }

    const ethTransaction = ethTransactions[0];

    let fileData = null;
    if (ethTransaction && !_.isNil(ethTransaction.callDataId)) {
      fileData = await FileDataService.getLatestFileDataContents(ethTransaction.callDataId, {whereQuery: []});
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
      ethTransaction,
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
    const filters = utils.buildAndValidateFilters(
      req.query,
      acceptedContractResultsParameters,
      contractResultsFilterValidityChecks
    );

    const {conditions, params, order, limit} = await this.extractContractResultsByIdQuery(filters, '');

    const rows = await ContractService.getContractResultsByIdAndFilters(conditions, params, order, limit);
    const response = {
      results: [],
      links: {
        next: null,
      },
    };
    res.locals[responseDataLabel] = response;
    if (rows.length === 0) {
      return;
    }

    const payers = [];
    const timestamps = [];
    rows.forEach((row) => {
      payers.push(row.payerAccountId);
      timestamps.push(row.consensusTimestamp);
    });
    const [ethereumTransactionMap, recordFileMap] = await Promise.all([
      ContractService.getEthereumTransactionsByPayerAndTimestampArray(payers, timestamps),
      RecordFileService.getRecordFileBlockDetailsFromTimestampArray(timestamps),
    ]);

    response.results = rows.map(
      (row) =>
        new ContractResultDetailsViewModel(
          row,
          recordFileMap.get(row.consensusTimestamp),
          ethereumTransactionMap.get(row.consensusTimestamp)
        )
    );

    const lastRow = _.last(response.results);
    const lastContractResultTimestamp = lastRow.timestamp;
    response.links.next = utils.getPaginationLink(
      req,
      response.results.length !== limit,
      {
        [filterKeys.TIMESTAMP]: lastContractResultTimestamp,
      },
      order
    );
  };

  /**
   * Handler function for /contracts/results/:transactionIdOrHash API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getContractResultsByTransactionIdOrHash = async (req, res) => {
    if (utils.conflictingPathParam(req, 'transactionIdOrHash', 'logs')) {
      return;
    }

    utils.validateReq(req, acceptedSingleContractResultsParameters);

    let contractResults;
    let ethTransactions;
    // Exclude duplicate transactions and ethereum transactions failed with wrong ethereum nonce
    const excludeTransactionResults = [duplicateTransactionResult, wrongNonceTransactionResult];
    const {transactionIdOrHash} = req.params;
    if (utils.isValidEthHash(transactionIdOrHash)) {
      const ethHash = Buffer.from(transactionIdOrHash.replace('0x', ''), 'hex');
      [ethTransactions, contractResults] = await Promise.all([
        TransactionService.getEthTransactionByHash(ethHash, excludeTransactionResults, 1),
        ContractService.getContractResultsByHash(ethHash, excludeTransactionResults, 1),
      ]);
    } else {
      const transactionId = TransactionId.fromString(transactionIdOrHash);
      const nonce = getLastNonceParamValue(req.query);
      // Map the transactions id to a consensus timestamp
      const transactions = await TransactionService.getTransactionDetailsFromTransactionId(
        transactionId,
        nonce,
        excludeTransactionResults
      );

      if (transactions.length === 0) {
        throw new NotFoundError();
      } else if (transactions.length > 1) {
        for (const transaction of transactions) {
          if (!isContractTransaction(transaction)) {
            throw new NotFoundError();
          }
        }

        logger.error(
          'Transaction invariance breached: there should be at most one transaction with none-duplicate-transaction ' +
            'result for a specific (payer + valid start timestamp + nonce) combination'
        );
        throw new Error('Transaction invariance breached');
      }

      // Fetch transactions details and contractResults by mapped timestamp
      const consensusTimestamp = transactions[0].consensusTimestamp;
      [ethTransactions, contractResults] = await Promise.all([
        TransactionService.getEthTransactionByTimestamp(consensusTimestamp),
        ContractService.getContractResultsByTimestamps(consensusTimestamp),
      ]);
    }

    if (contractResults.length === 0) {
      if (ethTransactions.length !== 0) {
        logger.error(
          `Contract result not found for ethereum transaction at consensus timestamp ${ethTransactions[0].consensusTimestamp}`
        );
      }

      throw new NotFoundError();
    }

    const contractResult = contractResults[0];
    const ethTransaction = ethTransactions[0];

    const [recordFile, contractLogs, contractStateChanges] = await Promise.all([
      RecordFileService.getRecordFileBlockDetailsFromTimestamp(contractResult.consensusTimestamp),
      ContractService.getContractLogsByTimestamps(contractResult.consensusTimestamp),
      ContractService.getContractStateChangesByTimestamps(contractResult.consensusTimestamp),
    ]);

    let fileData = null;

    if (ethTransaction && !_.isNil(ethTransaction.callDataId)) {
      fileData = await FileDataService.getLatestFileDataContents(ethTransaction.callDataId, {whereQuery: []});
    }

    this.setContractResultsResponse(
      res,
      contractResult,
      recordFile,
      ethTransaction,
      contractLogs,
      contractStateChanges,
      fileData
    );

    if (_.isNil(contractResult.callResult)) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getContractResultsByTransactionId returning partial content`);
    }
  };

  getContractActions = async (req, res) => {
    // Supported args: index, limit, order
    const rawFilters = utils.buildAndValidateFilters(req.query, acceptedContractActionsParameters);
    const filters = [];
    let order = orderFilterValues.ASC;
    let limit = defaultLimit;

    for (const filter of rawFilters) {
      if (filter.key === filterKeys.ORDER) {
        order = filter.value;
      } else if (filter.key === filterKeys.LIMIT) {
        limit = filter.value;
      } else if (filter.key === filterKeys.INDEX) {
        if (filter.operator === utils.opsMap.ne) {
          throw InvalidArgumentError.forRequestValidation(filterKeys.INDEX);
        }

        filters.push(filter);
      }
    }

    // extract filters from query param
    const {transactionIdOrHash} = req.params;
    let consensusTimestamp;
    let transactionId;
    let tx;
    if (utils.isValidEthHash(transactionIdOrHash)) {
      const hash = Buffer.from(transactionIdOrHash.replace('0x', ''), 'hex');
      tx = await ContractService.getContractResultsByHash(hash);
    } else {
      transactionId = TransactionId.fromString(transactionIdOrHash);
      tx = await TransactionService.getTransactionDetailsFromTransactionId(transactionId);
    }

    let payerAccountId;
    if (tx.length) {
      consensusTimestamp = tx[0].consensusTimestamp;
      payerAccountId = transactionId ? transactionId.getEntityId().getEncodedId() : tx[0].payerAccountId;
    } else {
      throw new NotFoundError();
    }

    const rows = await ContractService.getContractActionsByConsensusTimestamp(
      consensusTimestamp,
      payerAccountId,
      filters,
      order,
      limit
    );
    const actions = rows.map((row) => new ContractActionViewModel(row));
    let nextLink = null;
    if (actions.length) {
      const lastRow = _.last(actions);
      const lastIndex = lastRow.index;
      nextLink = utils.getPaginationLink(
        req,
        actions.length !== limit,
        {
          [filterKeys.INDEX]: lastIndex,
        },
        order
      );
    }

    res.locals[responseDataLabel] = {
      actions,
      links: {
        next: nextLink,
      },
    };
  };

  setContractResultsResponse = (
    res,
    contractResult,
    recordFile,
    ethTransaction,
    contractLogs,
    contractStateChanges,
    fileData
  ) => {
    res.locals[responseDataLabel] = new ContractResultDetailsViewModel(
      contractResult,
      recordFile,
      ethTransaction,
      contractLogs,
      contractStateChanges,
      fileData
    );
  };
}

const contractCtrlInstance = new ContractController();

const acceptedContractLogParameters = new Set([
  filterKeys.INDEX,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TIMESTAMP,
  filterKeys.TOPIC0,
  filterKeys.TOPIC1,
  filterKeys.TOPIC2,
  filterKeys.TOPIC3,
]);

const acceptedContractParameters = new Set([filterKeys.CONTRACT_ID, filterKeys.LIMIT, filterKeys.ORDER]);

const acceptedContractByIdParameters = new Set([filterKeys.TIMESTAMP]);

const acceptedContractActionsParameters = new Set([filterKeys.INDEX, filterKeys.LIMIT, filterKeys.ORDER]);

const acceptedContractResultsParameters = new Set([
  filterKeys.FROM,
  filterKeys.BLOCK_HASH,
  filterKeys.BLOCK_NUMBER,
  filterKeys.INTERNAL,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TIMESTAMP,
  filterKeys.TRANSACTION_INDEX,
]);

const acceptedSingleContractResultsParameters = new Set([filterKeys.NONCE]);

const acceptedContractStateParameters = new Set([
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.SLOT,
  filterKeys.TIMESTAMP,
]);

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

const isContractTransaction = (transaction) =>
  transaction.type === contractCallType ||
  transaction.type === contractCreateType ||
  transaction.type === ethereumTransactionType;

const contractController = exportControllerMethods([
  'getContractActions',
  'getContractById',
  'getContracts',
  'getContractLogsById',
  'getContractLogs',
  'getContractResults',
  'getContractResultsById',
  'getContractResultsByTimestamp',
  'getContractResultsByTransactionIdOrHash',
  'getContractStateById',
]);

if (utils.isTestEnv()) {
  Object.assign(
    contractController,
    exportControllerMethods(['extractContractResultsByIdQuery', 'extractContractLogsMultiUnionQuery']),
    {
      checkTimestampsForTopics,
      contractSelectFields,
      contractWithBytecodeSelectFields,
      extractSqlFromContractFilters,
      formatContractRow,
      getContractByIdOrAddressContractEntityQuery,
      getContractsQuery,
      getLastNonceParamValue,
      validateContractIdAndConsensusTimestampParam,
      validateContractIdParam,
      alterTimestampRangeInReq,
    }
  );
}

export default contractController;
