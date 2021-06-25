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

const {InvalidArgumentError} = require('../errors/invalidArgumentError');

class TransactionTypes {
  /**
   * Store and verify transaction type maps
   */
  constructor(transactionTypeToProtoMap, transactionTypeProtoToNameMap) {
    if (transactionTypeToProtoMap === undefined || transactionTypeToProtoMap.size === 0) {
      throw new InvalidArgumentError(`Transaction type name to id map from db is empty`);
    }
    if (transactionTypeProtoToNameMap === undefined || transactionTypeProtoToNameMap.size === 0) {
      throw new InvalidArgumentError(`Transaction type id to name map from db is empty`);
    }

    this.transactionTypeToProtoMap = transactionTypeToProtoMap;
    this.transactionTypeProtoToNameMap = transactionTypeProtoToNameMap;
  }

  getId(transactionTypeName) {
    if (!_.isString(transactionTypeName)) {
      throw new InvalidArgumentError(`Invalid argument ${transactionTypeName} is not a string`);
    }

    const type = this.transactionTypeToProtoMap.get(transactionTypeName.toUpperCase());
    if (type === undefined) {
      throw new InvalidArgumentError(`Transaction type ${transactionTypeName.toUpperCase()} not found in db`);
    }
    return type;
  }

  getName(transactionTypeId) {
    if (!_.isNumber(transactionTypeId)) {
      throw new InvalidArgumentError(`Invalid argument ${transactionTypeId} is not a number`);
    }

    const type = this.transactionTypeProtoToNameMap.get(transactionTypeId);
    if (type === undefined) {
      throw new InvalidArgumentError(`Transaction type ${transactionTypeId} not found in db`);
    }
    return type;
  }
}

module.exports = TransactionTypes;
