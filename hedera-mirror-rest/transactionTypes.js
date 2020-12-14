/*-
 * ‌
 * Hedera Mirror Node
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

const transactionTypes = new Map();

const loadTransactionTypes = function () {
  pool
    .query('SELECT proto_id, name FROM t_transaction_types')
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      // logger.info(results)
      for (const row of results.rows) {
        // logger.info(row);
        // logger.info(row.name);
        // transactionTypes[row.name] = name.proto_id;
        transactionTypes.set(row.name, row.proto_id);
      }
      // logger.info(transactionTypes2);
    })
    .then((q) => {
      logger.info('HI IS HERE');
      logger.info(transactionTypes);
    });
};

module.exports = {
  transactionTypes,
  loadTransactionTypes,
};
