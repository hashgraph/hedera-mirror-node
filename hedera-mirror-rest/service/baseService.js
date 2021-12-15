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

/**
 * Base service class that other services should inherit from for their retrieval business logic
 */
class BaseService {
  getOrderByQuery(column, order) {
    return `order by ${column} ${order}`;
  }

  getLimitQuery(limitParamCount) {
    return `limit $${limitParamCount}`;
  }

  async getRows(query, params, functionName = '') {
    if (logger.isTraceEnabled()) {
      logger.trace(`${functionName} query: ${query}, params: ${params}`);
    }
    const {rows} = await pool.queryQuietly(query, params);
    if (logger.isTraceEnabled()) {
      logger.trace(`${functionName} ${rows.length} entries`);
    }
    return rows;
  }

  async getSingleRow(query, params, functionName = '') {
    if (logger.isTraceEnabled()) {
      logger.trace(`${functionName} query: ${query}, params: ${params}`);
    }
    const rows = await this.getRows(query, params, functionName);
    if (_.isEmpty(rows) || rows.length > 1) {
      return null;
    }

    return rows[0];
  }
}

module.exports = BaseService;
