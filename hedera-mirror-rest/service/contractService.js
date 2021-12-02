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
const RecordFileService = require('./recordFileService');
const {ContractResult, RecordFile, Transaction} = require('../model');
const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const {orderFilterValues} = require('../constants');
const {logger} = require('../stream/utils');

/**
 * Contract retrieval business logic
 */
class ContractService extends BaseService {
  constructor() {
    super();
  }

  static contractResultsByIdQuery = `select *
    from ${ContractResult.tableName} ${ContractResult.tableAlias}`;

  // contract results with additional details from record_file (block) and transaction tables from timestamp
  static contractResultsDetailedQuery = `select 
    ${ContractResult.getFullName(ContractResult.AMOUNT)},
    ${ContractResult.getFullName(ContractResult.BLOOM)},
    ${ContractResult.getFullName(ContractResult.CALL_RESULT)},
    ${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)},
    ${ContractResult.getFullName(ContractResult.CONTRACT_ID)},
    ${ContractResult.getFullName(ContractResult.CREATED_CONTRACT_IDS)},
    ${ContractResult.getFullName(ContractResult.ERROR_MESSAGE)},
    ${ContractResult.getFullName(ContractResult.FUNCTION_PARAMETERS)},
    ${ContractResult.getFullName(ContractResult.FUNCTION_RESULT)},
    ${ContractResult.getFullName(ContractResult.GAS_LIMIT)},
    ${ContractResult.getFullName(ContractResult.GAS_USED)},
    ${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)},
    ${Transaction.getFullName(Transaction.TRANSACTION_HASH)}
    from ${ContractResult.tableName} ${ContractResult.tableAlias}
    join ${Transaction.tableName} ${Transaction.tableAlias}  on 
    ${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)} = ${Transaction.getFullName(
    Transaction.CONSENSUS_TIMESTAMP
  )}`;

  async getContractResultsByIdAndFilters(
    whereConditions = [],
    whereParams = [],
    order = orderFilterValues.DESC,
    limit = defaultLimit
  ) {
    const [query, params] = this.getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit);
    const rows = await super.getRows(query, params, 'getContractResultsByIdAndFilters');
    return _.isEmpty(rows) ? [] : rows.map((cr) => new ContractResult(cr));
  }

  getContractResultsByIdAndFiltersQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;
    const query = [
      ContractService.contractResultsByIdQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP), order),
      super.getLimitQuery(whereParams.length + 1), // get limit param located at end of array
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  /**
   * Retrieves a detailed view of a contract result
   *
   * @param {string} contractId encoded contract ID
   * @param {string} timestamp consensus timestamp
   * @return {{contractResult: ContractResult, recordFile: RecordFile, transaction: Transaction}}
   */
  async getContractResultsByIdAndTimestamp(contractId, timestamp) {
    const recordFile = await RecordFileService.getRecordFileBlockDetailsFromTimestamp(timestamp);
    if (recordFile === null) {
      return null;
    }

    // get detailed contract results
    const whereParams = [contractId, timestamp];
    const whereConditions = [
      `${ContractResult.getFullName(ContractResult.CONTRACT_ID)} = $1`,
      `${ContractResult.getFullName(ContractResult.CONSENSUS_TIMESTAMP)} = $2`,
    ];
    const query = [ContractService.contractResultsDetailedQuery, `where ${whereConditions.join(' and ')}`].join('\n');
    const rows = await super.getRows(query, whereParams, 'getContractResultsByIdAndTimestamp');
    if (_.isEmpty(rows) || rows.length > 1) {
      logger.trace(`No matching contract results for contractId: ${contractId}, timestamp: ${timestamp}`);
      return null;
    }

    const result = rows[0];
    const contractResult = new ContractResult(result);
    const transaction = new Transaction(result);
    return {
      contractResult: contractResult,
      recordFile: recordFile,
      transaction: transaction,
    };
  }

  /**
   * Retrieves a detailed view of a contract result
   *
   * @param {number} validStartNs validStartNs
   * @param {number} payerAccountId payerAccountId
   * @return {{contractResult: ContractResult, recordFile: RecordFile, transaction: Transaction}}
   */
  async getContractResultsByTransactionId(validStartNs, payerAccountId) {
    const whereParams = [validStartNs, payerAccountId];
    const whereConditions = [
      `${Transaction.getFullName(Transaction.VALID_START_NS)} = $1`,
      `${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)} = $2`,
    ];
    const query = [ContractService.contractResultsDetailedQuery, `where ${whereConditions.join(' and ')}`].join('\n');
    const rows = await super.getRows(query, whereParams, 'getContractResultsByIdAndTimestamp');
    if (_.isEmpty(rows) || rows.length > 1) {
      return null;
    }

    const result = rows[0];
    const contractResult = new ContractResult(result);
    const transaction = new Transaction(result);

    const recordFile = await RecordFileService.getRecordFileBlockDetailsFromTimestamp(
      contractResult.consensusTimestamp
    );
    if (recordFile === null) {
      logger.trace(`No matching record file found for timestamp: ${timestamp}`);
      return null;
    }

    return {
      contractResult: contractResult,
      recordFile: recordFile,
      transaction: transaction,
    };
  }
}

module.exports = new ContractService();
