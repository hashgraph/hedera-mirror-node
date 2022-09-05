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

import BaseService from './baseService';
import {getResponseLimit} from '../config';
import {filterKeys, orderFilterValues} from '../constants';
import EntityId from '../entityId';
import {NotFoundError} from '../errors';
import {OrderSpec} from '../sql';
import {JSONStringify} from '../utils';
import {
  ContractLog,
  ContractResult,
  ContractStateChange,
  Entity,
  EthereumTransaction,
  Transaction,
  TransactionWithEthData,
} from '../model';

const {default: defaultLimit} = getResponseLimit();

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  static detailedContractResultsWithEthereumTransactionHashQuery = `select ${ContractResult.tableAlias}.*,
                                                                           ${EthereumTransaction.tableAlias}.${EthereumTransaction.HASH}
                                                                    from ${ContractResult.tableName} ${ContractResult.tableAlias}
  `;

  static detailedContractResultsWithEthereumTransactionQuery = `select ${ContractResult.tableAlias}.*,
                                                                  ${ContractResult.getFullName(
                                                                    ContractResult.TRANSACTION_RESULT
                                                                  )} as result,
                                                                  ${ContractResult.getFullName(
                                                                    ContractResult.TRANSACTION_INDEX
                                                                  )} as index,
                                                                  ${ContractResult.getFullName(
                                                                    ContractResult.TRANSACTION_HASH
                                                                  )} as hash,
                                                                  ${EthereumTransaction.tableAlias}.*,
                                                                coalesce(
                                                                  ${EthereumTransaction.getFullName(
                                                                    EthereumTransaction.CONSENSUS_TIMESTAMP
                                                                  )},
                                                                  ${ContractResult.getFullName(
                                                                    ContractResult.CONSENSUS_TIMESTAMP
                                                                  )}
                                                                ) as ${ContractResult.CONSENSUS_TIMESTAMP},
                                                                coalesce(
                                                                  ${EthereumTransaction.getFullName(
                                                                    EthereumTransaction.GAS_LIMIT
                                                                  )},
                                                                  ${ContractResult.getFullName(
                                                                    ContractResult.GAS_LIMIT
                                                                  )}
                                                                ) as ${ContractResult.GAS_LIMIT}
                                                                from ${ContractResult.tableName} ${
    ContractResult.tableAlias
  }
  `;

  static transactionTableCTE = `, ${Transaction.tableAlias} as (
      select
      ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.INDEX}, ${Transaction.NONCE}
      from ${Transaction.tableName}
      where $where
    )`;

  static ethereumTransactionTableCTE = `with ${EthereumTransaction.tableAlias} as (
      select ${EthereumTransaction.HASH}, ${EthereumTransaction.CONSENSUS_TIMESTAMP}
      from ${EthereumTransaction.tableName}
    )`;

  static ethereumTransactionTableFullCTE = `with ${EthereumTransaction.tableAlias} as (
    select
      ${EthereumTransaction.ACCESS_LIST},
      ${EthereumTransaction.CALL_DATA},
      ${EthereumTransaction.CALL_DATA_ID},
      ${EthereumTransaction.CHAIN_ID},
      ${EthereumTransaction.CONSENSUS_TIMESTAMP},
      ${EthereumTransaction.GAS_LIMIT},
      ${EthereumTransaction.GAS_PRICE},
      ${EthereumTransaction.HASH},
      ${EthereumTransaction.MAX_FEE_PER_GAS},
      ${EthereumTransaction.MAX_PRIORITY_FEE_PER_GAS},
      ${EthereumTransaction.NONCE},
      ${EthereumTransaction.SIGNATURE_R},
      ${EthereumTransaction.SIGNATURE_S},
      ${EthereumTransaction.TYPE},
      ${EthereumTransaction.RECOVERY_ID},
      ${EthereumTransaction.VALUE}
    from ${EthereumTransaction.tableName}
  )`;

  static joinTransactionTable = `left join ${Transaction.tableAlias}
  on ${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)} = ${Transaction.getFullName(
    Transaction.CONSENSUS_TIMESTAMP
  )}
  `;

  static joinEthereumTransactionTable = `left join ${EthereumTransaction.tableAlias}
  on ${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)} = ${EthereumTransaction.getFullName(
    EthereumTransaction.CONSENSUS_TIMESTAMP
  )}
  `;

  static contractResultsQuery = `select ${ContractResult.AMOUNT},
                                        ${ContractResult.BLOOM},
                                        ${ContractResult.CALL_RESULT},
                                        ${ContractResult.CONSENSUS_TIMESTAMP},
                                        ${ContractResult.CONTRACT_ID},
                                        ${ContractResult.CREATED_CONTRACT_IDS},
                                        ${ContractResult.ERROR_MESSAGE},
                                        ${ContractResult.FAILED_INITCODE},
                                        ${ContractResult.FUNCTION_PARAMETERS},
                                        ${ContractResult.GAS_LIMIT},
                                        ${ContractResult.GAS_USED},
                                        ${ContractResult.PAYER_ACCOUNT_ID},
                                        ${ContractResult.SENDER_ID},
                                        ${ContractResult.TRANSACTION_RESULT} as result,
                                        ${ContractResult.TRANSACTION_INDEX} as index,
                                        ${ContractResult.TRANSACTION_HASH} as hash,
                                 from ${ContractResult.tableName}`;

  static contractStateChangesQuery = `select ${ContractStateChange.CONSENSUS_TIMESTAMP},
                                             ${ContractStateChange.CONTRACT_ID},
                                             ${ContractStateChange.PAYER_ACCOUNT_ID},
                                             ${ContractStateChange.SLOT},
                                             ${ContractStateChange.VALUE_READ},
                                             ${ContractStateChange.VALUE_WRITTEN}
                                      from ${ContractStateChange.tableName}`;

  static contractLogsQuery = `select ${ContractLog.BLOOM},
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

  static contractIdByEvmAddressQuery = `select ${Entity.ID}
                                        from ${Entity.tableName} ${Entity.tableAlias}
                                        where ${Entity.DELETED} <> true
                                          and ${Entity.TYPE} = 'CONTRACT'`;

  static contractByEvmAddressQueryFilters = [
    {
      partName: 'shard',
      columnName: Entity.getFullName(Entity.SHARD),
    },
    {
      partName: 'realm',
      columnName: Entity.getFullName(Entity.REALM),
    },
    {
      partName: 'create2_evm_address',
      columnName: Entity.getFullName(Entity.EVM_ADDRESS),
    },
  ];

  static contractLogsPaginationColumns = {
    [filterKeys.TIMESTAMP]: ContractLog.CONSENSUS_TIMESTAMP,
    [filterKeys.INDEX]: ContractLog.INDEX,
  };

  constructor() {
    super();
  }

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
      ContractService.ethereumTransactionTableCTE,
      joinTransactionTable
        ? ContractService.transactionTableCTE.replace('$where', transactionWhereClauses.join(' and '))
        : '',
      ContractService.detailedContractResultsWithEthereumTransactionHashQuery,
      ContractService.joinEthereumTransactionTable,
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
    return rows.map((cr) => {
      return {
        ...new ContractResult(cr),
        hash: cr.hash,
      };
    });
  }

  /**
   * Retrieves contract results based on the timestamps
   *
   * @param {string|string[]} timestamps consensus timestamps
   * @return {Promise<[{TransactionWithEthData}[],{ContractResult}[]]>}
   */
  async getContractResultsByTimestamps(timestamps) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const whereClause = `where ${ContractResult.getFullName(
      ContractResult.CONSENSUS_TIMESTAMP
    )} ${timestampsOpAndValue}`;
    const query = [
      ContractService.ethereumTransactionTableFullCTE,
      ContractService.detailedContractResultsWithEthereumTransactionQuery,
      ContractService.joinEthereumTransactionTable,
      whereClause,
    ].join('\n');
    const rows = await super.getRows(query, params, 'getContractResultsByTimestamps');

    return [rows.map((row) => new TransactionWithEthData(row)), rows.map((row) => new ContractResult(row))];
  }

  /**
   * Retrieves contract results based on the eth hash
   *
   * @param {string} timestamps consensus timestamps
   * @return {Promise<[{TransactionWithEthData}[],{ContractResult}[]]>}
   */
  async getContractResultsByHash(hash, excludeTransactionResults = [], limit = undefined) {
    let params = [hash];
    let hashOpAndValue = '= $1';
    let transactionsFilter = '';

    if (excludeTransactionResults != null) {
      if (Array.isArray(excludeTransactionResults)) {
        transactionsFilter =
          excludeTransactionResults.length > 0
            ? ` and ${ContractResult.getFullName(
                ContractResult.TRANSACTION_RESULT
              )} not in (${excludeTransactionResults.join(', ')})`
            : '';
      } else {
        transactionsFilter = ` and ${ContractResult.getFullName(
          ContractResult.TRANSACTION_RESULT
        )} <> ${excludeTransactionResults}`;
      }
    }

    const whereClause = `where ${ContractResult.getFullName(ContractResult.TRANSACTION_HASH)} ${hashOpAndValue}`;
    const query = [
      ContractService.ethereumTransactionTableFullCTE,
      ContractService.detailedContractResultsWithEthereumTransactionQuery,
      ContractService.joinEthereumTransactionTable,
      whereClause,
      transactionsFilter,
      super.getOrderByQuery(OrderSpec.from(ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP), 'asc')),
      limit ? `limit ${limit}` : '',
    ].join('\n');
    const rows = await super.getRows(query, params, 'getContractResultsByHash');

    return [rows.map((row) => new TransactionWithEthData(row)), rows.map((row) => new ContractResult(row))];
  }

  /**
   * Build sql query for retrieving contract logs
   * @param query
   * @returns {[string, *[]]}
   */
  getContractLogsQuery({lower, inner, upper, params, conditions, timestampOrder, indexOrder, limit}) {
    params.push(limit);
    const orderClause = super.getOrderByQuery(
      OrderSpec.from(ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP), timestampOrder),
      OrderSpec.from(ContractLog.getFullName(ContractLog.INDEX), indexOrder)
    );
    const orderClauseNoAlias = super.getOrderByQuery(
      OrderSpec.from(ContractLog.CONSENSUS_TIMESTAMP, timestampOrder),
      OrderSpec.from(ContractLog.INDEX, indexOrder)
    );
    const limitClause = super.getLimitQuery(params.length);

    const subQueries = [lower, inner, upper]
      .filter((filters) => filters.length !== 0)
      .map((filters) =>
        super.buildSelectQuery(
          ContractService.contractLogsQuery,
          params,
          conditions,
          orderClause,
          limitClause,
          filters.map((filter) => ({
            ...filter,
            column: ContractLog.getFullName(ContractService.contractLogsPaginationColumns[filter.key]),
          }))
        )
      );

    let sqlQuery;
    if (subQueries.length === 0) {
      // if all three filters are empty, the subqueries will be empty too, just create the query with empty filters
      sqlQuery = super.buildSelectQuery(
        ContractService.contractLogsQuery,
        params,
        conditions,
        orderClause,
        limitClause
      );
    } else if (subQueries.length === 1) {
      sqlQuery = subQueries[0];
    } else {
      sqlQuery = [subQueries.map((q) => `(${q})`).join('\nunion\n'), orderClauseNoAlias, limitClause].join('\n');
    }

    return [sqlQuery, params];
  }

  /**
   * Retrieves contract logs based on contract id and various filters
   *
   * @param query
   * @returns {Promise<ContractLog[]>} the result of the getContractLogs query
   */
  async getContractLogs(query) {
    const [sqlQuery, params] = this.getContractLogsQuery(query);
    const rows = await super.getRows(sqlQuery, params, 'getContractLogs');
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

  async getContractStateChangesByTimestamps(timestamps, contractId = null) {
    let params = [timestamps];
    let timestampsOpAndValue = '= $1';
    if (Array.isArray(timestamps)) {
      params = timestamps;
      const positions = _.range(1, timestamps.length + 1).map((i) => `$${i}`);
      timestampsOpAndValue = `in (${positions})`;
    }

    const conditions = [`${ContractStateChange.CONSENSUS_TIMESTAMP} ${timestampsOpAndValue}`];
    if (contractId) {
      params.push(contractId);
      conditions.push(
        `(${ContractStateChange.MIGRATION} is false or ${ContractStateChange.CONTRACT_ID} = $${params.length})`
      );
    } else {
      conditions.push(`${ContractStateChange.MIGRATION} is false`);
    }

    const whereClause = 'where ' + conditions.join(' and ');
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
    // since evm_address is not a unique index, it is important to make this check.
    if (rows.length > 1) {
      throw new Error(
        `More than one contract with the evm address ${JSONStringify(evmAddressFilter)} have been found.`
      );
    }

    return rows[0].id;
  }

  async computeContractIdFromString(contractIdValue) {
    const contractIdParts = EntityId.computeContractIdPartsFromContractIdValue(contractIdValue);

    if (contractIdParts.hasOwnProperty('create2_evm_address')) {
      contractIdParts.create2_evm_address = Buffer.from(contractIdParts.create2_evm_address, 'hex');
      return this.getContractIdByEvmAddress(contractIdParts);
    }

    return EntityId.parse(contractIdValue, {paramName: filterKeys.CONTRACTID}).getEncodedId();
  }
}

export default new ContractService();
