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

const {DbError} = require('./errors/dbError');
const {NotFoundError} = require('./errors/notFoundError');

const readinessQuery = `select true
                        from address_book
                        limit 1;`;

/**
 * Function to determine readiness of application.
 *
 * @returns {Promise<void>}
 */
const readinessCheck = async () => {
  return pool
    .query(readinessQuery)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      if (results.rowCount !== 1) {
        throw new NotFoundError('Application readiness check failed');
      }
    });
};

/**
 * Function to determine liveness of application.
 *
 * @returns {Promise<void>}
 */
const livenessCheck = async () => {};

/**
 * Allows for a graceful shutdown.
 *
 * @returns {Promise<*>}
 */
const beforeShutdown = async () => {
  logger.info(`Closing connection pool`);
  return pool.end();
};

module.exports = {
  readinessCheck,
  livenessCheck,
  beforeShutdown,
};
