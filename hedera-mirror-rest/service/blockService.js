/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const BaseService = require('./baseService');
const {RecordFile} = require('../model');

/**
 * BlockService retrieval business logic
 */
class BlockService extends BaseService {
  constructor() {
    super();
  }

  buildWhereSqlStatement(whereQuery) {
    let where = 'where true=true';
    const params = [];
    for (const i in whereQuery) {
      const paramIndex = parseInt(i) + 1;
      where += ` and ${whereQuery[i][0].replace('?', '$' + paramIndex)} `;
      params.push(whereQuery[i][1][0]);
    }

    return {where, params};
  }

  async getBlocks(filters) {
    const {where, params} = this.buildWhereSqlStatement(filters.whereQuery);

    const query = `
      select * from ${RecordFile.tableName}
      ${where}
      order by index ${filters.order}
      limit ${filters.limit}
    `;
    const rows = await super.getRows(query, params, 'getBlocks');

    return rows.map((recordFile) => new RecordFile(recordFile));
  }
}

module.exports = new BlockService();
