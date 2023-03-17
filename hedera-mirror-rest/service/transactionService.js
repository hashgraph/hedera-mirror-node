/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import {orderFilterValues} from '../constants';
import {EthereumTransaction, Transaction} from '../model';
import {OrderSpec} from '../sql';

/**
 * Transaction retrieval business logic
 */
class TransactionService extends BaseService {
  constructor() {
    super();
  }

  static transactionDetailsFromTransactionIdQuery = `
    select ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.NONCE}, ${Transaction.SCHEDULED}, ${Transaction.TYPE}
    from ${Transaction.tableName}
    where ${Transaction.PAYER_ACCOUNT_ID} = $1 and ${Transaction.VALID_START_NS} = $2`;

  static ethereumTransactionDetailsQuery = `
  select
    ${EthereumTransaction.getFullName(EthereumTransaction.ACCESS_LIST)},
    ${EthereumTransaction.getFullName(EthereumTransaction.CALL_DATA)},
    ${EthereumTransaction.getFullName(EthereumTransaction.CALL_DATA_ID)},
    ${EthereumTransaction.getFullName(EthereumTransaction.CHAIN_ID)},
    ${EthereumTransaction.getFullName(EthereumTransaction.CONSENSUS_TIMESTAMP)},
    ${EthereumTransaction.getFullName(EthereumTransaction.GAS_LIMIT)},
    ${EthereumTransaction.getFullName(EthereumTransaction.GAS_PRICE)},
    ${EthereumTransaction.getFullName(EthereumTransaction.HASH)},
    ${EthereumTransaction.getFullName(EthereumTransaction.MAX_FEE_PER_GAS)},
    ${EthereumTransaction.getFullName(EthereumTransaction.MAX_PRIORITY_FEE_PER_GAS)},
    ${EthereumTransaction.getFullName(EthereumTransaction.NONCE)},
    ${EthereumTransaction.getFullName(EthereumTransaction.PAYER_ACCOUNT_ID)},
    ${EthereumTransaction.getFullName(EthereumTransaction.SIGNATURE_R)},
    ${EthereumTransaction.getFullName(EthereumTransaction.SIGNATURE_S)},
    ${EthereumTransaction.getFullName(EthereumTransaction.TYPE)},
    ${EthereumTransaction.getFullName(EthereumTransaction.RECOVERY_ID)},
    ${EthereumTransaction.getFullName(EthereumTransaction.VALUE)},
    ${Transaction.getFullName(Transaction.RESULT)}
  from ${EthereumTransaction.tableName} ${EthereumTransaction.tableAlias}
  join ${Transaction.tableName} ${Transaction.tableAlias}
  on ${EthereumTransaction.getFullName(EthereumTransaction.CONSENSUS_TIMESTAMP)} =
     ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} and
     ${EthereumTransaction.getFullName(EthereumTransaction.PAYER_ACCOUNT_ID)} =
     ${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)}`;

  /**
   * Retrieves the transaction based on the transaction id and its nonce
   *
   * @param {TransactionId} transactionId transactionId
   * @param {Number} nonce nonce of the transaction
   * @param {string[]|string} excludeTransactionResults Transaction results to exclude, can be a list or a single result
   * @return {Promise<Transaction[]>} transactions subset
   */
  async getTransactionDetailsFromTransactionId(transactionId, nonce = undefined, excludeTransactionResults = []) {
    return this.getTransactionDetails(
      TransactionService.transactionDetailsFromTransactionIdQuery,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()],
      'getTransactionDetailsFromTransactionId',
      excludeTransactionResults,
      nonce
    );
  }

  async getEthTransactionByHash(hash, excludeTransactionResults = [], limit = undefined) {
    const params = [hash];
    const transactionsFilter = this.getExcludeTransactionResultsCondition(excludeTransactionResults, params);
    const query = [
      TransactionService.ethereumTransactionDetailsQuery,
      `where ${EthereumTransaction.getFullName(EthereumTransaction.HASH)} = $1`,
      transactionsFilter,
      this.getOrderByQuery(
        OrderSpec.from(EthereumTransaction.getFullName(EthereumTransaction.CONSENSUS_TIMESTAMP), orderFilterValues.ASC)
      ),
      limit ? `limit ${limit}` : '',
    ].join('\n');

    const rows = await super.getRows(query, params, 'getEthereumTransactionByHash');
    return rows.map((row) => new EthereumTransaction(row));
  }

  async getEthTransactionByTimestamp(timestamp) {
    const query = [
      TransactionService.ethereumTransactionDetailsQuery,
      `where ${EthereumTransaction.getFullName(EthereumTransaction.CONSENSUS_TIMESTAMP)} = $1`,
    ].join('\n');

    const rows = await super.getRows(query, [timestamp], 'getEthereumTransactionByTimestamp');
    return rows.map((row) => new EthereumTransaction(row));
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
      and ${Transaction.NONCE} = $${params.length}`;
    }

    query += this.getExcludeTransactionResultsCondition(excludeTransactionResults, params);

    if (orderQuery !== undefined) {
      query += ` ${orderQuery}`;
    }

    if (limit !== undefined) {
      params.push(limit);
      query += ` ${this.getLimitQuery(params.length)}`;
    }

    const rows = await super.getRows(query, params, parentFunctionName);
    return rows.map((row) => new Transaction(row));
  }

  getExcludeTransactionResultsCondition = (excludeTransactionResults, params) => {
    if (_.isNil(excludeTransactionResults)) {
      return '';
    }

    if (!Array.isArray(excludeTransactionResults)) {
      excludeTransactionResults = [excludeTransactionResults];
    }

    if (excludeTransactionResults.length === 0) {
      return '';
    }

    const positions = excludeTransactionResults.reduce((previous, current) => {
      const length = params.push(current);
      previous.push(`$${length}`);
      return previous;
    }, []);
    return ` and ${Transaction.RESULT} not in (${positions})`;
  };
}

export default new TransactionService();
