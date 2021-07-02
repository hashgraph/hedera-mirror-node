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

const {DbError} = require('../errors/dbError');
const {InvalidArgumentError} = require('../errors/invalidArgumentError');
const {TransactionResult} = require('../model');

/**
 * Transaction results retrieval business logic
 */
class TransactionResultService {
  constructor() {
    this.transactionResultToProtoMap = new Map();
    this.transactionResultProtoToResultMap = new Map();
  }

  static transactionResultsQuery = `select ${TransactionResult.PROTO_ID}, ${TransactionResult.RESULT}
                                    from ${TransactionResult.tableName}`;

  populateTransactionResultMaps(transactionResults) {
    this.transactionResultToProtoMap = new Map();
    this.transactionResultProtoToResultMap = new Map();

    transactionResults.forEach((transactionResult) => {
      this.transactionResultToProtoMap.set(transactionResult.result, transactionResult);
      this.transactionResultProtoToResultMap.set(transactionResult.protoId, transactionResult);
    });
  }

  async getTransactionResults() {
    let rows;
    try {
      const result = await pool.query(TransactionResultService.transactionResultsQuery);
      rows = result.rows;
    } catch (err) {
      this.promise = null;
      throw new DbError(err.message);
    }

    if (_.isEmpty(rows)) {
      return [];
    }

    return rows.map((row) => new TransactionResult(row));
  }

  async loadTransactionResults() {
    const transactionTypes = await this.getTransactionResults();
    this.populateTransactionResultMaps(transactionTypes);
  }

  getProtoId(transactionResultName) {
    if (!_.isString(transactionResultName)) {
      throw new InvalidArgumentError(`Invalid argument ${transactionResultName} is not a string`);
    }

    const type = this.transactionResultToProtoMap.get(transactionResultName.toUpperCase());
    if (type === undefined) {
      throw new InvalidArgumentError(`Transaction type ${transactionResultName.toUpperCase()} not found in db`);
    }
    return type.protoId;
  }

  getResult(transactionResultId) {
    if (!_.isNumber(transactionResultId)) {
      throw new InvalidArgumentError(`Invalid argument ${transactionResultId} is not a number`);
    }

    const type = this.transactionResultProtoToResultMap.get(transactionResultId);
    if (type === undefined) {
      throw new InvalidArgumentError(`Transaction type ${transactionResultId} not found in db`);
    }
    return type.result;
  }
}

module.exports = new TransactionResultService();
