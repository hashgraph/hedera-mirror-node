/*-
 * ‌
 * Hedera Mirror Node REST API
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

const {DbError} = require('./errors/dbError');

const transactionTypesQuery = 'select proto_id, name from t_transaction_types';

// Transaction Type (String) -> ProtoId (Integer)
const transactionTypesMap = new Map();

let promise;

const get = async (transactionTypeName) => {
  if (!promise) {
    promise = pool.query(transactionTypesQuery);
  }

  try {
    const result = await promise;
    if (transactionTypesMap.size === 0) {
      result.rows.forEach((row) => transactionTypesMap.set(row.name, row.proto_id));
    }
    return transactionTypesMap.get(transactionTypeName.toUpperCase());
  } catch (err) {
    throw new DbError(err.message);
  }
};

module.exports = {
  get,
};
