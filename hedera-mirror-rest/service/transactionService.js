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

const BaseService = require('./baseService');
const {Transaction} = require('../model');
const {EthereumTransaction} = require('../model');
const {TransactionWithEthData} = require('../model');
const {OrderSpec} = require('../sql');

const ethTransactionReplaceString = `$ethTransactionWhere`;

/**
 * Transaction retrieval business logic
 */
class TransactionService extends BaseService {
  constructor() {
    super();
  }

  static ethTransactionTableCTE = `with ${EthereumTransaction.tableAlias} as (
      select
        ${EthereumTransaction.ACCESS_LIST},
        ${EthereumTransaction.CALL_DATA},
        ${EthereumTransaction.CALL_DATA_ID},
        ${EthereumTransaction.CHAIN_ID},
        ${EthereumTransaction.CONSENSUS_TIMESTAMP} as ethConsensus,
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
      where ${ethTransactionReplaceString}
  )`;

  static transactionDetailsFromTimestampQuery = `${this.ethTransactionTableCTE}
    select
      ${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)},
      ${Transaction.getFullName(Transaction.RESULT)},
      coalesce(
        ${EthereumTransaction.getFullName(EthereumTransaction.HASH)},
        ${Transaction.getFullName(Transaction.TRANSACTION_HASH)}
      ) as ${Transaction.TRANSACTION_HASH},
      ${Transaction.getFullName(Transaction.INDEX)},
      ${EthereumTransaction.tableAlias}.*
    from ${Transaction.tableName} ${Transaction.tableAlias}
    left join ${EthereumTransaction.tableAlias}
    on ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} = ethConsensus
    where ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} = $1`;

  static selectTransactionDetailsBaseQuery = `${this.ethTransactionTableCTE}
    select
    ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)},
    ${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)},
    ${Transaction.getFullName(Transaction.RESULT)},
    coalesce(
        ${EthereumTransaction.getFullName(EthereumTransaction.HASH)},
      ${Transaction.getFullName(Transaction.TRANSACTION_HASH)}
    ) as ${Transaction.TRANSACTION_HASH},
    ${Transaction.getFullName(Transaction.INDEX)},
    ${EthereumTransaction.tableAlias}.*
    from ${Transaction.tableName} ${Transaction.tableAlias}
    left join ${EthereumTransaction.tableAlias}
    on ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} = ethConsensus
    `;

  static transactionDetailsFromTransactionIdQuery = `${this.selectTransactionDetailsBaseQuery}
    where ${Transaction.PAYER_ACCOUNT_ID} = $1
      and ${Transaction.VALID_START_NS} = $2`;

  static transactionDetailsFromEthHashQuery = `${this.selectTransactionDetailsBaseQuery}
    where ${EthereumTransaction.getFullName(EthereumTransaction.HASH)} = $1`;

  /**
   * Retrieves the transaction for the given timestamp
   *
   * @param {string} timestamp consensus timestamp
   * @return {Promise<Transaction>} transaction subset
   */
  async getTransactionDetailsFromTimestamp(timestamp) {
    const row = await super.getSingleRow(
      TransactionService.transactionDetailsFromTimestampQuery.replace(
        ethTransactionReplaceString,
        `${EthereumTransaction.CONSENSUS_TIMESTAMP} = $1`
      ),
      [timestamp],
      'getTransactionDetailsFromTimestamp'
    );

    return _.isNull(row) ? null : new TransactionWithEthData(row);
  }

  /**
   * Retrieves the transaction based on the transaction id and its nonce
   *
   * @param {TransactionId} transactionId transactionId
   * @param {Number} nonce nonce of the transaction
   * @param {Number[]|Number} excludeTransactionResults Transaction results to exclude, can be a list or a single result
   * @return {Promise<Transaction[]>} transactions subset
   */
  async getTransactionDetailsFromTransactionId(transactionId, nonce = undefined, excludeTransactionResults = []) {
    return this.getTransactionDetails(
      TransactionService.transactionDetailsFromTransactionIdQuery.replace(
        ethTransactionReplaceString,
        `${EthereumTransaction.PAYER_ACCOUNT_ID} = $1`
      ),
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()],
      'getTransactionDetailsFromEthHash',
      excludeTransactionResults,
      nonce
    );
  }

  async getTransactionDetailsFromEthHash(ethHash, excludeTransactionResults = [], limit = undefined) {
    return this.getTransactionDetails(
      TransactionService.transactionDetailsFromEthHashQuery.replace(
        ethTransactionReplaceString,
        `${EthereumTransaction.HASH} = $1`
      ),
      [ethHash],
      'getTransactionDetailsFromEthHash',
      excludeTransactionResults,
      undefined,
      limit,
      this.getOrderByQuery(OrderSpec.from(Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP), 'asc'))
    );
  }

  async getTransactionDetails(
    query,
    params,
    parentFunctionName,
    excludeTransactionResults = [],
    nonce = undefined,
    limit = undefined,
    orderQuery = undefined
  ) {
    if (nonce !== undefined) {
      params.push(nonce);
      query = `${query}
      and ${Transaction.getFullName(Transaction.NONCE)} = $${params.length}`;
    }

    if (excludeTransactionResults !== undefined) {
      if (Array.isArray(excludeTransactionResults)) {
        if (excludeTransactionResults.length > 0) {
          const start = params.length + 1;
          params.push(...excludeTransactionResults);
          const positions = _.range(start, params.length + 1).map((p) => `$${p}`);
          query += ` and ${Transaction.getFullName(Transaction.RESULT)} not in (${positions})`;
        }
      } else {
        params.push(excludeTransactionResults);
        query += ` and ${Transaction.getFullName(Transaction.RESULT)} <> $${params.length}`;
      }
    }

    if (orderQuery !== undefined) {
      query += ` ${orderQuery}`;
    }

    if (limit !== undefined) {
      params.push(limit);
      query += ` ${this.getLimitQuery(params.length)}`;
    }

    const rows = await super.getRows(query, params, parentFunctionName);
    return rows.map((row) => new TransactionWithEthData(row));
  }
}

module.exports = new TransactionService();
