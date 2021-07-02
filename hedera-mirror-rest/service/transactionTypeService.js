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
const {TransactionType} = require('../model');

/**
 * Transaction types retrieval business logic
 */
class TransactionTypeService {
  constructor() {
    this.transactionTypeToProtoMap = new Map();
    this.transactionTypeProtoToNameMap = new Map();
  }

  static transactionTypesQuery = `select ${TransactionType.ENTITY_TYPE}, ${TransactionType.NAME}, ${TransactionType.PROTO_ID}
                                  from ${TransactionType.tableName}`;

  populateTransactionTypeMaps(transactionTypes) {
    this.transactionTypeToProtoMap = new Map();
    this.transactionTypeProtoToNameMap = new Map();

    transactionTypes.forEach((transactionType) => {
      this.transactionTypeToProtoMap.set(transactionType.name, transactionType);
      this.transactionTypeProtoToNameMap.set(transactionType.protoId, transactionType);
    });
  }

  async getTransactionTypes() {
    let rows;
    try {
      const result = await pool.query(TransactionTypeService.transactionTypesQuery);
      rows = result.rows;
    } catch (err) {
      this.promise = null;
      throw new DbError(err.message);
    }

    if (_.isEmpty(rows)) {
      return [];
    }

    return rows.map((row) => new TransactionType(row));
  }

  async loadTransactionTypes() {
    const transactionTypes = await this.getTransactionTypes();
    this.populateTransactionTypeMaps(transactionTypes);
  }

  getProtoId(transactionTypeName) {
    if (!_.isString(transactionTypeName)) {
      throw new InvalidArgumentError(`Invalid argument ${transactionTypeName} is not a string`);
    }

    const type = this.transactionTypeToProtoMap.get(transactionTypeName.toUpperCase());
    if (type === undefined) {
      throw new InvalidArgumentError(`Transaction type ${transactionTypeName.toUpperCase()} not found in db`);
    }
    return type.protoId;
  }

  getName(transactionTypeId) {
    if (!_.isNumber(transactionTypeId)) {
      throw new InvalidArgumentError(`Invalid argument ${transactionTypeId} is not a number`);
    }

    const type = this.transactionTypeProtoToNameMap.get(transactionTypeId);
    if (type === undefined) {
      throw new InvalidArgumentError(`Transaction type ${transactionTypeId} not found in db`);
    }
    return type.name;
  }
}

module.exports = new TransactionTypeService();
