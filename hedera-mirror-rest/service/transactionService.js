/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

/**
 * Transaction retrieval business logic
 */
class TransactionService extends BaseService {
  constructor() {
    super();
  }

  static transactionDetailsFromTimestampQuery = `select
      ${Transaction.PAYER_ACCOUNT_ID}, ${Transaction.TRANSACTION_HASH}
    from ${Transaction.tableName}
    where ${Transaction.CONSENSUS_TIMESTAMP} = $1`;

  static transactionDetailsFromTransactionIdQuery = `select
      ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.PAYER_ACCOUNT_ID}, ${Transaction.TRANSACTION_HASH}
    from ${Transaction.tableName}
    where ${Transaction.PAYER_ACCOUNT_ID} = $1
      and ${Transaction.VALID_START_NS} = $2`;
  static transactionDetailsFromTransactionIdAndNonceQuery = `
    ${TransactionService.transactionDetailsFromTransactionIdQuery}
      and ${Transaction.NONCE} = $3`;

  /**
   * Retrieves the transaction for the given timestamp
   *
   * @param {string} timestamp consensus timestamp
   * @return {Promise<Transaction>} transaction subset
   */
  async getTransactionDetailsFromTimestamp(timestamp) {
    const row = await super.getSingleRow(
      TransactionService.transactionDetailsFromTimestampQuery,
      [timestamp],
      'getTransactionDetailsFromTimestamp'
    );

    return _.isNull(row) ? null : new Transaction(row);
  }

  /**
   * Retrieves the transaction based on the transaction id and its nonce
   *
   * @param {TransactionId} transactionId transactionId
   * @param {Number} nonce nonce of the transaction
   * @param {Number[]|Number} excludeTransactionResults Transaction results to exclude, can be a list or a single result
   * @return {Promise<Transaction[]>} transactions subset
   */
  async getTransactionDetailsFromTransactionIdAndNonce(
    transactionId,
    nonce = undefined,
    excludeTransactionResults = []
  ) {
    const params = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()];
    let query = TransactionService.transactionDetailsFromTransactionIdQuery;

    if (nonce !== undefined) {
      params.push(nonce);
      query = TransactionService.transactionDetailsFromTransactionIdAndNonceQuery;
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

    const rows = await super.getRows(query, params, 'getTransactionDetailsFromTransactionIdAndNonce');
    return rows.map((row) => new Transaction(row));
  }
}

module.exports = new TransactionService();
