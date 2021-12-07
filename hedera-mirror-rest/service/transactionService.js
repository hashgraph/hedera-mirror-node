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

  static transactionContractDetailsFromTimestampQuery = `select 
    ${Transaction.PAYER_ACCOUNT_ID}, ${Transaction.TRANSACTION_HASH}
    from ${Transaction.tableName} 
    where ${Transaction.CONSENSUS_TIMESTAMP} = $1`;

  static transactionContractDetailsFromTransactionIdQuery = `select 
    ${Transaction.CONSENSUS_TIMESTAMP}, ${Transaction.PAYER_ACCOUNT_ID}, ${Transaction.TRANSACTION_HASH}
    from ${Transaction.tableName} 
    where ${Transaction.PAYER_ACCOUNT_ID} = $1 and ${Transaction.VALID_START_NS} = $2`;

  /**
   * Retrieves the transaction for the given timestamp
   *
   * @param {string} timestamp encoded contract ID
   * @return {Promise<Transaction>} transaction subset
   */
  async getTransactionContractDetailsFromTimestamp(timestamp) {
    const row = await super.getSingleRow(
      TransactionService.transactionContractDetailsFromTimestampQuery,
      [timestamp],
      'getTransactionContractDetailsFromTimestamp'
    );

    return _.isNull(row) ? null : new Transaction(row);
  }

  /**
   * Retrieves the transaction based on the transaction id
   *
   * @param {TransactionId} transactionId transactionId
   * @return {Promise<Transaction>} transaction subset
   */
  async getTransactionContractDetailsFromTransactionId(transactionId) {
    const row = await super.getSingleRow(
      TransactionService.transactionContractDetailsFromTransactionIdQuery,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()],
      'transactionContractDetailsFromTransactionId'
    );

    return _.isNull(row) ? null : new Transaction(row);
  }
}

module.exports = new TransactionService();
