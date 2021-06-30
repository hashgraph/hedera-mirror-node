/*-
 * ‌
 * Hedera Mirror Node REST API
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

const {DbError} = require('./errors/dbError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

const transactionTypesQuery = 'select proto_id, name from t_transaction_types';

// Transaction Type (String) -> ProtoId (Integer)
const transactionTypeToProtoMap = new Map();
// Transaction ProtoId (Integer) -> Type (String)
const transactionTypeProtoToNameMap = new Map();

let promise;

const get = async () => {
  if (!promise) {
    if (logger.isTraceEnabled()) {
      logger.trace(`getTransactionTypes query: ${transactionTypesQuery}`);
    }
    promise = pool.query(transactionTypesQuery);
  }

  try {
    const result = await promise;
    if (transactionTypeToProtoMap.size === 0) {
      result.rows.forEach((row) => {
        transactionTypeToProtoMap.set(row.name, row.proto_id);
        transactionTypeProtoToNameMap.set(row.proto_id, row.name);
      });
    }
  } catch (err) {
    promise = null;
    throw new DbError(err.message);
  }
};

const getName = async (transactionTypeId) => {
  if (!_.isNumber(transactionTypeId)) {
    throw new InvalidArgumentError(`Invalid argument ${transactionTypeId} is not a number`);
  }

  if (transactionTypeToProtoMap.size === 0) {
    await get();
  }

  const type = transactionTypeProtoToNameMap.get(transactionTypeId);
  if (type === undefined) {
    throw new InvalidArgumentError(`Transaction type ${transactionTypeId} not found in db`);
  }
  return type;
};

const getId = async (transactionTypeName) => {
  if (!_.isString(transactionTypeName)) {
    throw new InvalidArgumentError(`Invalid argument ${transactionTypeName} is not a string`);
  }

  if (transactionTypeToProtoMap.size === 0) {
    await get();
  }

  const type = transactionTypeToProtoMap.get(transactionTypeName.toUpperCase());
  if (type === undefined) {
    throw new InvalidArgumentError(`Transaction type ${transactionTypeName.toUpperCase()} not found in db`);
  }
  return type;
};

module.exports = {
  getName,
  getId,
};
