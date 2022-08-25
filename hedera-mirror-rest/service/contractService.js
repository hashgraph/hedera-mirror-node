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
  ContractAction,
  ContractLog,
  ContractResult,
  ContractStateChange,
  Entity,
  EthereumTransaction,
  Transaction,
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
                                        ${ContractResult.SENDER_ID}
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

  static contractActionsByHashQuery = `with ${ContractAction.tableAlias} as (
      select ${ContractAction.tableName}.*
      from ${ContractAction.tableName}
    )
    select ${ContractAction.tableAlias}.* from ${ContractResult.tableName} ${ContractResult.tableAlias}
    left join ${ContractAction.tableAlias}
      on ${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)} = ${ContractAction.getFullName(
    ContractAction.CONSENSUS_TIMESTAMP
  )}
    where ${ContractResult.TRANSACTION_HASH} = $1
    `;

  static contractActionsByTxIdQuery = `
       select ${ContractAction.tableAlias}.* from ${ContractAction.tableName} ${ContractAction.tableAlias}
       where ${ContractAction.CONSENSUS_TIMESTAMP} = $1
       and ${ContractAction.CALLER} = $2
  `;

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

    return EntityId.parse(contractIdValue, {paramName: filterKeys.CONTRACTID}).getEncodedId();
  }

  async getContractActionsByHash(hash, filters, order, limit) {
    let params = [hash];
    return this.getContractActions(ContractService.contractActionsByHashQuery, params, filters, order, limit);
  }

  async getContractActionsByTransactionId(transactionId, filters, order, limit) {
    const [entityId, ...timestampParts] = transactionId.split('-');
    const timestamp = timestampParts.join('-');
    let params = [entityId, timestamp];
    return this.getContractActions(ContractService.contractActionsByTxIdQuery, params, filters, order, limit);
  }

  async getContractActions(baseQuery, params = [], filters = {}, order = orderFilterValues.ASC, limit = 25) {
    let whereClause = ``;
    if (filters && filters.index) {
      whereClause = `and ${ContractAction.getFullName(ContractAction.INDEX)} = $${params.length + 1}`;
      params.push(filters.index);
    }

    const orderClause = super.getOrderByQuery(OrderSpec.from(ContractAction.getFullName(ContractAction.INDEX), order));

    if (limit < 1) limit = 1;
    if (limit > 100) limit = 100;

    const limitClause = `limit ${limit}`;

    let query = [baseQuery, whereClause, orderClause, limitClause].join('\n');

    const rows = await super.getRows(query, params, 'getActionsByHash');
    return rows.map((row) => new ContractStateChange(row));
  }
}

export default new ContractService();
