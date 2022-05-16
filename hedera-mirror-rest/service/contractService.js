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

const constants = require('../constants');
const Contract = require('../model/contract');
const Transaction = require('../model/transaction');
const EthereumTransaction = require('../model/ethereumTransaction');
const {ContractLog, ContractResult, ContractStateChange} = require('../model');
const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const EntityId = require('../entityId');
const {NotFoundError} = require('../errors/notFoundError');
const {orderFilterValues} = require('../constants');
const {OrderSpec} = require('../sql');
const {JSONStringify} = require('../utils');

const BaseService = require('./baseService');

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  constructor() {
    super();
  }

  static detailedContractResultsQuery = `select ${ContractResult.tableAlias}.*
  from ${ContractResult.tableName} ${ContractResult.tableAlias}
  `;

  static transactionTableCTE = `with ${Transaction.tableAlias} as (
      select
      ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.INDEX}, ${Transaction.NONCE}
      from ${Transaction.tableName}
      where $where
    )
  `;

  static joinTransactionTable = `left join ${Transaction.tableAlias}
  on ${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)} = ${Transaction.getFullName(
    Transaction.CONSENSUS_TIMESTAMP
  )}
  `;

  static contractResultsQuery = `select
    ${ContractResult.AMOUNT},
    ${ContractResult.BLOOM},
    ${ContractResult.CALL_RESULT},
    ${ContractResult.CONSENSUS_TIMESTAMP},
    ${ContractResult.CONTRACT_ID},
    ${ContractResult.CREATED_CONTRACT_IDS},
    ${ContractResult.ERROR_MESSAGE},
    ${ContractResult.FUNCTION_PARAMETERS},
    ${ContractResult.GAS_LIMIT},
    ${ContractResult.GAS_USED},
    ${ContractResult.PAYER_ACCOUNT_ID},
    ${ContractResult.SENDER_ID}
    from ${ContractResult.tableName}`;

  static contractStateChangesQuery = `select
    ${ContractStateChange.CONSENSUS_TIMESTAMP},
    ${ContractStateChange.CONTRACT_ID},
    ${ContractStateChange.PAYER_ACCOUNT_ID},
    ${ContractStateChange.SLOT},
    ${ContractStateChange.VALUE_READ},
    ${ContractStateChange.VALUE_WRITTEN}
    from ${ContractStateChange.tableName}`;

  static contractLogsQuery = `select
    ${ContractLog.BLOOM},
    ${ContractLog.CONTRACT_ID},
    ${ContractLog.CONSENSUS_TIMESTAMP},
    ${ContractLog.DATA},
    ${ContractLog.INDEX},
    ${ContractLog.ROOT_CONTRACT_ID},
    ${ContractLog.TOPIC0},
    ${ContractLog.TOPIC1},
    ${ContractLog.TOPIC2},
    ${ContractLog.TOPIC3}
    from ${ContractLog.tableName} ${ContractLog.tableAlias}`;

  static contractIdByEvmAddressQuery = `select
    ${Contract.ID}
    from ${Contract.tableName} ${Contract.tableAlias}
    where deleted <> true`;
  static contractByEvmAddressQueryFilters = [
    {
      partName: 'shard',
      columnName: Contract.getFullName(Contract.SHARD),
    },
    {
      partName: 'realm',
      columnName: Contract.getFullName(Contract.REALM),
    },
    {
      partName: 'create2_evm_address',
      columnName: Contract.getFullName(Contract.EVM_ADDRESS),
    },
  ];

  getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;
    let joinTransactionTable = false;
    const transactionWhereClauses = [];
    if (whereConditions.length) {
      for (let condition of whereConditions) {
        if (
          condition.includes(`${Transaction.tableAlias}.${Transaction.INDEX}`) ||
          condition.includes(`${Transaction.tableAlias}.${Transaction.NONCE}`)
        ) {
          joinTransactionTable = true;
          transactionWhereClauses.push(condition.replace(`${Transaction.tableAlias}.`, `${Transaction.tableName}.`));
        }
      }
    }

    const query = [
      joinTransactionTable
        ? ContractService.transactionTableCTE.replace('$where', transactionWhereClauses.join(' and '))
        : '',
      ContractService.detailedContractResultsQuery,
      joinTransactionTable ? ContractService.joinTransactionTable : '',
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(OrderSpec.from(ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP), order)),
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
    return rows.map((cr) => new ContractResult(cr));
  }

  /**
   * Retrieves contract results based on the timestamps
   *
   * @param {string|string[]} timestamps consensus timestamps
   * @return {Promise<{ContractResult}[]>}
   */
  async getContractResultsByTimestamps(timestamps) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const whereClause = `where ${ContractResult.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`;
    const query = [ContractService.contractResultsQuery, whereClause].join('\n');
    const rows = await super.getRows(query, params, 'getContractResultsByTimestamps');
    return rows.map((row) => new ContractResult(row));
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
  getContractLogsQuery(whereConditions, whereParams, timestampOrder, indexOrder, limit) {
    const params = whereParams;
    const orderClause = super.getOrderByQuery(
      OrderSpec.from(ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP), timestampOrder),
      OrderSpec.from(ContractLog.getFullName(ContractLog.INDEX), indexOrder)
    );
    const query = [
      ContractService.contractLogsQuery,
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
   * @returns {Promise<*[]|*>} the result of the getContractLogs query
   */
  async getContractLogs(
    whereConditions = [],
    whereParams = [],
    timestampOrder = orderFilterValues.DESC,
    indexOrder = orderFilterValues.DESC,
    limit = defaultLimit
  ) {
    const [query, params] = this.getContractLogsQuery(whereConditions, whereParams, timestampOrder, indexOrder, limit);
    const rows = await super.getRows(query, params, 'getContractLogs');
    return rows.map((cr) => new ContractLog(cr));
  }

  async getContractLogsByTimestamps(timestamps) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const whereClause = `where ${ContractLog.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`;
    const orderClause = `order by ${ContractLog.CONSENSUS_TIMESTAMP}, ${ContractLog.INDEX}`;
    const query = [ContractService.contractLogsQuery, whereClause, orderClause].join('\n');
    const rows = await super.getRows(query, params, 'getContractLogsByTimestamps');
    return rows.map((row) => new ContractLog(row));
  }

  async getContractStateChangesByTimestamps(timestamps) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const whereClause = `where ${ContractStateChange.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`;
    const orderClause = `order by ${ContractStateChange.CONSENSUS_TIMESTAMP}, ${ContractStateChange.CONTRACT_ID}, ${ContractStateChange.SLOT}`;
    const query = [ContractService.contractStateChangesQuery, whereClause, orderClause].join('\n');
    const rows = await super.getRows(query, params, 'getContractStateChangesByTimestamps');
    return rows.map((row) => new ContractStateChange(row));
  }

  computeConditionsAndParamsFromEvmAddressFilter({evmAddressFilter, paramOffset = 0}) {
    const params = [];
    const conditions = [];
    ContractService.contractByEvmAddressQueryFilters.forEach(({partName, columnName}) => {
      if (evmAddressFilter[partName] === null) {
        return;
      }
      if (partName === 'create2_evm_address') {
        evmAddressFilter[partName] = Buffer.from(evmAddressFilter[partName], 'hex');
      }
      const length = params.push(evmAddressFilter[partName]);
      conditions.push(`${columnName} = $${length + paramOffset}`);
    });
    return {params, conditions};
  }

  async getContractIdByEvmAddress(evmAddressFilter) {
    const {params, conditions} = this.computeConditionsAndParamsFromEvmAddressFilter({evmAddressFilter});
    const query = `${ContractService.contractIdByEvmAddressQuery} and ${conditions.join(' and ')}`;
    const rows = await super.getRows(query, params, 'getContractIdByEvmAddress');
    if (rows.length === 0) {
      throw new NotFoundError(
        `No contract with the given evm address: ${JSONStringify(evmAddressFilter)} has been found.`
      );
    }
    //since evm_address is not an unique index, it is important to make this check.
    if (rows.length > 1) {
      throw new Error(
        `More than one contract with the evm address ${JSONStringify(evmAddressFilter)} have been found.`
      );
    }
    const contractId = rows[0];
    return BigInt(contractId.id);
  }

  async computeContractIdFromString(contractIdValue) {
    const contractIdParts = EntityId.computeContractIdPartsFromContractIdValue(contractIdValue);

    if (contractIdParts.hasOwnProperty('create2_evm_address')) {
      contractIdParts.create2_evm_address = Buffer.from(contractIdParts.create2_evm_address, 'hex');
      return this.getContractIdByEvmAddress(contractIdParts);
    }

    return EntityId.parse(contractIdValue, {paramName: constants.filterKeys.CONTRACTID}).getEncodedId();
  }
}

module.exports = new ContractService();
