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
import {Transaction} from '../model';

/**
 * Transaction retrieval business logic
 */
class TransactionService extends BaseService {
  constructor() {
    super();
  }

  static transactionDetailsFromTransactionIdQuery = `select
      ${Transaction.CONSENSUS_TIMESTAMP}
    from ${Transaction.tableName}
    where ${Transaction.PAYER_ACCOUNT_ID} = $1
      and ${Transaction.VALID_START_NS} = $2`;

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

    if (excludeTransactionResults !== undefined) {
      if (Array.isArray(excludeTransactionResults)) {
        if (excludeTransactionResults.length > 0) {
          const start = params.length + 1;
          params.push(...excludeTransactionResults);
          const positions = _.range(start, params.length + 1).map((p) => `$${p}`);
          query += ` and ${Transaction.RESULT} not in (${positions})`;
        }
      } else {
        params.push(excludeTransactionResults);
        query += ` and ${Transaction.RESULT} <> $${params.length}`;
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

    return rows.map((row) => new Transaction(row));
  }
}

export default new TransactionService();
