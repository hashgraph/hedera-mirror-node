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
  RecordFile,
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

  static contractResultsQuery = `
    select ${ContractResult.AMOUNT},
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

  static contractStateChangesQuery = `
    select ${ContractStateChange.CONSENSUS_TIMESTAMP},
           ${ContractStateChange.CONTRACT_ID},
           ${ContractStateChange.PAYER_ACCOUNT_ID},
           ${ContractStateChange.SLOT},
           ${ContractStateChange.VALUE_READ},
           ${ContractStateChange.VALUE_WRITTEN}
    from ${ContractStateChange.tableName}`;

  static contractLogsQuery = `select ${ContractLog.getFullName(ContractLog.BLOOM)},
                                     ${ContractLog.getFullName(ContractLog.CONTRACT_ID)},
                                     ${ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP)},
                                     ${ContractLog.getFullName(ContractLog.DATA)},
                                     ${ContractLog.getFullName(ContractLog.INDEX)},
                                     ${ContractLog.getFullName(ContractLog.ROOT_CONTRACT_ID)},
                                     ${ContractLog.getFullName(ContractLog.TOPIC0)},
                                     ${ContractLog.getFullName(ContractLog.TOPIC1)},
                                     ${ContractLog.getFullName(ContractLog.TOPIC2)},
                                     ${ContractLog.getFullName(ContractLog.TOPIC3)},
                                     ${ContractResult.getFullName(ContractResult.TRANSACTION_HASH)},
                                     ${ContractResult.getFullName(ContractResult.TRANSACTION_INDEX)},
                                     block.block_number,
                                     block.block_hash
    from ${ContractLog.tableName} ${ContractLog.tableAlias}
    left join ${ContractResult.tableName} ${ContractResult.tableAlias} on
    ${ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP)} = ${ContractResult.getFullName(
    ContractResult.CONSENSUS_TIMESTAMP
  )}
    left join lateral (
      select ${RecordFile.INDEX} as block_number, ${RecordFile.HASH} as block_hash
      from ${RecordFile.tableName}
      where ${RecordFile.CONSENSUS_END} >= ${ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP)}
      order by ${RecordFile.CONSENSUS_END} asc
      limit 1
    ) as block on true
  `;

  static contractIdByEvmAddressQuery = `
    select ${Entity.ID}
    from ${Entity.tableName} ${Entity.tableAlias}
    where ${Entity.DELETED} <> true
      and ${Entity.TYPE} = 'CONTRACT'`;

  static contractActionsByConsensusTimestampQuery = `
    select ${ContractAction.CALLER},
           ${ContractAction.CALL_DEPTH},
           ${ContractAction.CALLER_TYPE},
           ${ContractAction.CALL_OPERATION_TYPE},
           ${ContractAction.CALL_TYPE},
           ${ContractAction.CALLER_TYPE},
           ${ContractAction.CONSENSUS_TIMESTAMP},
           ${ContractAction.GAS},
           ${ContractAction.GAS_USED},
           ${ContractAction.INDEX},
           ${ContractAction.INPUT},
           ${ContractAction.RECIPIENT_ACCOUNT},
           ${ContractAction.RECIPIENT_ADDRESS},
           ${ContractAction.RECIPIENT_CONTRACT},
           ${ContractAction.RESULT_DATA},
           ${ContractAction.RESULT_DATA_TYPE},
           ${ContractAction.VALUE}
    from ${ContractAction.tableName} ${ContractAction.tableAlias}
    where ${ContractAction.getFullName(ContractAction.CONSENSUS_TIMESTAMP)} = $1`;

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

    const whereClause = `where ${ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP)} ${timestampsOpAndValue}`;
    const orderClause = `order by ${ContractLog.getFullName(
      ContractLog.CONSENSUS_TIMESTAMP
    )}, ${ContractLog.getFullName(ContractLog.INDEX)}`;
    const query = [ContractService.contractLogsQuery, whereClause, orderClause].join('\n');
    const rows = await super.getRows(query, params, 'getContractLogsByTimestamps');
    return rows.map((cr) => new ContractLog(cr));
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

  async getContractActionsByConsensusTimestamp(consensusTimestamp, filters, order, limit) {
    const params = [consensusTimestamp];
    return this.getContractActions(
      ContractService.contractActionsByConsensusTimestampQuery,
      params,
      filters,
      order,
      limit
    );
  }

  async getContractActions(baseQuery, params, filters, order, limit) {
    let whereClause = ``;
    if (filters && filters.length) {
      for (const filter of filters) {
        if (filter.key === 'index') {
          whereClause += `\nand ${ContractAction.getFullName(ContractAction.INDEX)}${filter.operator}$${
            params.length + 1
          }`;
          params.push(filter.value);
        }
      }
    }

    const orderClause = super.getOrderByQuery(OrderSpec.from(ContractAction.getFullName(ContractAction.INDEX), order));

    params.push(limit);
    const limitClause = super.getLimitQuery(params.length);

    const query = [baseQuery, whereClause, orderClause, limitClause].join('\n');

    const rows = await super.getRows(query, params, 'getActionsByHash');
    return rows.map((row) => new ContractAction(row));
  }
}

export default new ContractService();
