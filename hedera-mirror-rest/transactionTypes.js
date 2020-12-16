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

const transactionTypesQuery = 'select proto_id, name from t_transaction_types';

const transactionTypesMap = new Map();

const get = async (transactionTypeName) => {
  if (transactionTypesMap.size === 0) {
    loadTransactionTypes();
  }
  return transactionTypesMap.get(transactionTypeName.toUpperCase());
};

const loadTransactionTypes = () => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getTransactionTypes query: ${transactionTypesQuery}`);
  }
  pool
    .query(transactionTypesQuery)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      for (const row of results.rows) {
        transactionTypesMap.set(row.name, row.proto_id);
      }
    });
};

module.exports = {
  get,
};
