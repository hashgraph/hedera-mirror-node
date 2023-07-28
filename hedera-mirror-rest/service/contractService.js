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
  ContractState,
  ContractStateChange,
  Entity,
  RecordFile,
  EthereumTransaction,
} from '../model';

const {default: defaultLimit} = getResponseLimit();
const contractLogsFields = `${ContractLog.getFullName(ContractLog.BLOOM)},
${ContractLog.getFullName(ContractLog.CONTRACT_ID)},
${ContractLog.getFullName(ContractLog.CONSENSUS_TIMESTAMP)},
${ContractLog.getFullName(ContractLog.DATA)},
${ContractLog.getFullName(ContractLog.INDEX)},
${ContractLog.getFullName(ContractLog.ROOT_CONTRACT_ID)},
${ContractLog.getFullName(ContractLog.TOPIC0)},
${ContractLog.getFullName(ContractLog.TOPIC1)},
${ContractLog.getFullName(ContractLog.TOPIC2)},
${ContractLog.getFullName(ContractLog.TOPIC3)},
${ContractLog.getFullName(ContractLog.TRANSACTION_HASH)},
${ContractLog.getFullName(ContractLog.TRANSACTION_INDEX)}
`;

const contractResultsFields = `${ContractResult.getFullName(ContractResult.AMOUNT)},
${ContractResult.getFullName(ContractResult.BLOOM)},
${ContractResult.getFullName(ContractResult.CALL_RESULT)},
${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)},
${ContractResult.getFullName(ContractResult.CONTRACT_ID)},
${ContractResult.getFullName(ContractResult.CREATED_CONTRACT_IDS)},
${ContractResult.getFullName(ContractResult.ERROR_MESSAGE)},
${ContractResult.getFullName(ContractResult.FAILED_INITCODE)},
${ContractResult.getFullName(ContractResult.FUNCTION_PARAMETERS)},
${ContractResult.getFullName(ContractResult.GAS_LIMIT)},
${ContractResult.getFullName(ContractResult.GAS_USED)},
${ContractResult.getFullName(ContractResult.PAYER_ACCOUNT_ID)},
${ContractResult.getFullName(ContractResult.SENDER_ID)},
${ContractResult.getFullName(ContractResult.TRANSACTION_HASH)},
${ContractResult.getFullName(ContractResult.TRANSACTION_INDEX)},
${ContractResult.getFullName(ContractResult.TRANSACTION_NONCE)},
${ContractResult.getFullName(ContractResult.TRANSACTION_RESULT)}
`;

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  static entityCTE = ` ${Entity.tableName} as (
    select ${Entity.EVM_ADDRESS}, ${Entity.ID} from ${Entity.tableName}
  ) `;

  static contractResultsWithEvmAddressQuery = `
    select
      ${contractResultsFields},
      coalesce(${Entity.getFullName(Entity.EVM_ADDRESS)},'') as ${Entity.EVM_ADDRESS}
    from ${ContractResult.tableName} ${ContractResult.tableAlias}
    `;

  static joinContractResultWithEvmAddress = `
      left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractResult.getFullName(ContractResult.CONTRACT_ID)}
   `;

  static contractStateChangesQuery = `
    with ${ContractService.entityCTE}
    select ${ContractStateChange.CONSENSUS_TIMESTAMP},
           ${ContractStateChange.CONTRACT_ID},
           ${ContractStateChange.PAYER_ACCOUNT_ID},
           ${ContractStateChange.SLOT},
           ${ContractStateChange.VALUE_READ},
           ${ContractStateChange.VALUE_WRITTEN},
           coalesce(${Entity.getFullName(Entity.EVM_ADDRESS)},'') as ${Entity.EVM_ADDRESS}
    from ${ContractStateChange.tableName} ${ContractStateChange.tableAlias}
    left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractStateChange.getFullName(ContractStateChange.CONTRACT_ID)}
    `;

  static contractStateQuery = `
    with ${ContractService.entityCTE}
    select ${ContractState.MODIFIED_TIMESTAMP},
           ${ContractState.CONTRACT_ID},
           ${ContractState.SLOT},
           ${ContractState.VALUE},
           coalesce(${Entity.getFullName(Entity.EVM_ADDRESS)},'') as ${Entity.EVM_ADDRESS}
    from ${ContractState.tableName} ${ContractState.tableAlias}
    left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractState.getFullName(ContractState.CONTRACT_ID)}
    `;

  static contractStateTimestampQuery = `
      with ${ContractService.entityCTE}
      select DISTINCT on (${ContractStateChange.SLOT}) 
            ${ContractStateChange.CONTRACT_ID},
            ${ContractStateChange.SLOT},
            ${Entity.EVM_ADDRESS},
            coalesce(${ContractStateChange.VALUE_WRITTEN}, ${ContractStateChange.VALUE_READ}) as ${ContractState.VALUE},
            ${ContractStateChange.CONSENSUS_TIMESTAMP} as ${ContractState.MODIFIED_TIMESTAMP}
      from ${ContractStateChange.tableName} ${ContractStateChange.tableAlias}
      left join ${Entity.tableName} ${Entity.tableAlias}
        on ${Entity.getFullName(Entity.ID)} = ${ContractStateChange.getFullName(ContractStateChange.CONTRACT_ID)}
    `;

  static contractLogsWithEvmAddressQuery = `
    with ${ContractService.entityCTE}
    select
      ${contractLogsFields},
      ${Entity.getFullName(Entity.EVM_ADDRESS)} as ${Entity.EVM_ADDRESS}
    from ${ContractLog.tableName} ${ContractLog.tableAlias}
      left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.getFullName(Entity.ID)} = ${ContractLog.getFullName(ContractLog.CONTRACT_ID)}
    `;

  static contractLogsExtendedQuery = `
    with ${RecordFile.tableName} as (
      select ${RecordFile.CONSENSUS_END}, ${RecordFile.HASH}, ${RecordFile.INDEX}
      from ${RecordFile.tableName}
    ), ${Entity.tableName} as (
      select ${Entity.EVM_ADDRESS}, ${Entity.ID}
      from ${Entity.tableName}
    )
    select ${contractLogsFields},
      block_number,
      block_hash,
      ${Entity.EVM_ADDRESS}
    from ${ContractLog.tableName} ${ContractLog.tableAlias}
    left join ${Entity.tableName} ${Entity.tableAlias}
      on ${Entity.ID} = ${ContractLog.CONTRACT_ID}
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
    where ${Entity.DELETED} <> true and ${Entity.TYPE} = 'CONTRACT'`;

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
    from ${ContractAction.tableName}
    where ${ContractAction.CONSENSUS_TIMESTAMP} = $1 and ${ContractAction.PAYER_ACCOUNT_ID} = $2`;

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

  static ethereumTransactionByPayerAndTimestampArrayQuery = `select
        t.consensus_timestamp,
        case when ${EthereumTransaction.getFullName(EthereumTransaction.CONSENSUS_TIMESTAMP)} is not null then
          json_build_object('access_list', encode(${EthereumTransaction.ACCESS_LIST}, 'hex'),
                'chain_id', encode(${EthereumTransaction.CHAIN_ID}, 'hex'),
                'gas_price', encode(${EthereumTransaction.GAS_PRICE}, 'hex'),
                'max_fee_per_gas', encode(${EthereumTransaction.MAX_FEE_PER_GAS}, 'hex'),
                'max_priority_fee_per_gas', encode(${EthereumTransaction.MAX_PRIORITY_FEE_PER_GAS}, 'hex'),
                'nonce', ${EthereumTransaction.NONCE},
                'signature_r', encode(${EthereumTransaction.SIGNATURE_R}, 'hex'),
                'signature_s', encode(${EthereumTransaction.SIGNATURE_S}, 'hex'),
                'type', ${EthereumTransaction.TYPE},
                'recovery_id', ${EthereumTransaction.RECOVERY_ID},
                'value', encode(${EthereumTransaction.VALUE}, 'hex'))
        end as ${EthereumTransaction.tableName}
      from (select * from unnest($1::bigint[], $2::bigint[]) as tmp (payer_account_id, consensus_timestamp)) as t
      left join ${EthereumTransaction.tableName} as ${EthereumTransaction.tableAlias}
        on t.payer_account_id = ${EthereumTransaction.getFullName(EthereumTransaction.PAYER_ACCOUNT_ID)} and
          t.consensus_timestamp = ${EthereumTransaction.getFullName(EthereumTransaction.CONSENSUS_TIMESTAMP)}`;

  constructor() {
    super();
  }

  getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;

    const query = [
      ContractService.contractResultsWithEvmAddressQuery,
      ContractService.joinContractResultWithEvmAddress,
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

  async getContractStateByIdAndFilters(
    whereConditions = [],
    order = orderFilterValues.ASC,
    limit = defaultLimit,
    timestamp = false
  ) {
    let orderClause = this.getOrderByQuery(OrderSpec.from(ContractStateChange.SLOT, order));
    const {where, params} = this.buildWhereSqlStatement(whereConditions);
    const limitClause = this.getLimitQuery(params.push(limit));
    let query = [ContractService.contractStateQuery, where, orderClause, limitClause].join(' ');

    if (timestamp) {
      //timestamp order needs to be always desc to get only the latest changes until the provided timestamp
      orderClause = this.getOrderByQuery(
        OrderSpec.from(ContractStateChange.SLOT, order),
        OrderSpec.from(ContractStateChange.CONSENSUS_TIMESTAMP, orderFilterValues.DESC)
      );

      query = [ContractService.contractStateTimestampQuery, where, orderClause, limitClause].join(' ');
    }
    const rows = await super.getRows(query, params, 'getContractStateByIdAndFilters');
    return rows.map((row) => new ContractState(row));
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
    const query = [
      `with ${ContractService.entityCTE} `,
      ContractService.contractResultsWithEvmAddressQuery,
      ContractService.joinContractResultWithEvmAddress,
      whereClause,
    ].join('\n');

    const rows = await super.getRows(query, params, 'getContractResultsByTimestamps');

    return rows.map((row) => {
      return {
        ...new ContractResult(row),
        evmAddress: row.evm_address,
      };
    });
  }

  /**
   * Retrieves contract results based on the eth hash
   *
   * @param {string} hash eth transaction hash or 32-byte hedera transaction hash prefix
   * @return {Promise<{ContractResult}[]>}
   */
  async getContractResultsByHash(hash, excludeTransactionResults = [], limit = undefined) {
    const params = [hash];
    let transactionsFilter = '';

    if (excludeTransactionResults != null) {
      if (Array.isArray(excludeTransactionResults)) {
        transactionsFilter =
          excludeTransactionResults.length > 0
            ? ` and ${ContractResult.TRANSACTION_RESULT} not in (${excludeTransactionResults.join(', ')})`
            : '';
      } else {
        transactionsFilter = ` and ${ContractResult.TRANSACTION_RESULT} <> ${excludeTransactionResults}`;
      }
    }

    const whereClause = `where ${ContractResult.TRANSACTION_HASH} = $1`;
    const query = [
      `with ${ContractService.entityCTE} `,
      ContractService.contractResultsWithEvmAddressQuery,
      ContractService.joinContractResultWithEvmAddress,
      whereClause,
      transactionsFilter,
      this.getOrderByQuery(OrderSpec.from(ContractResult.CONSENSUS_TIMESTAMP, 'asc')),
      limit ? `limit ${limit}` : '',
    ].join('\n');
    const rows = await super.getRows(query, params, 'getContractResultsByHash');

    return rows.map((row) => {
      return {
        ...new ContractResult(row),
        evmAddress: row.evm_address,
      };
    });
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
          ContractService.contractLogsExtendedQuery,
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
        ContractService.contractLogsExtendedQuery,
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

    const query = [ContractService.contractLogsWithEvmAddressQuery, whereClause, orderClause].join('\n');
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

    return rows.map((row) => {
      return {
        ...new ContractStateChange(row),
        evmAddress: row.evm_address,
      };
    });
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

  async getContractActionsByConsensusTimestamp(consensusTimestamp, payerAccountId, filters, order, limit) {
    const params = [consensusTimestamp, payerAccountId];
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
          params.push(filter.value);
          whereClause += `\nand ${ContractAction.INDEX}${filter.operator}$${params.length}`;
        }
      }
    }

    const orderClause = super.getOrderByQuery(OrderSpec.from(ContractAction.INDEX, order));

    params.push(limit);
    const limitClause = super.getLimitQuery(params.length);

    const query = [baseQuery, whereClause, orderClause, limitClause].join('\n');

    const rows = await super.getRows(query, params, 'getContractActions');
    return rows.map((row) => new ContractAction(row));
  }

  /**
   * Get the ethereum transaction matching the payer and timestamp pairs. Note the payers array and timestamps array
   * should have equal length
   *
   * @param payers
   * @param timestamps
   * @returns {Promise<{Map}>}
   */
  async getEthereumTransactionsByPayerAndTimestampArray(payers, timestamps) {
    const transactionMap = new Map();
    if (_.isEmpty(payers) || _.isEmpty(timestamps)) {
      return transactionMap;
    }

    const rows = await super.getRows(
      ContractService.ethereumTransactionByPayerAndTimestampArrayQuery,
      [payers, timestamps],
      'getEthereumTransactionsByPayerAndTimestampArray'
    );

    rows.forEach((row) => {
      const ethereumTransaction = row.ethereum_transaction ? new EthereumTransaction(row.ethereum_transaction) : null;
      transactionMap.set(row.consensus_timestamp, ethereumTransaction);
    });
    return transactionMap;
  }
}

export default new ContractService();
