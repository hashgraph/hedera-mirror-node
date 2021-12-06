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

const BaseService = require('./baseService');
const {RecordFile} = require('../model');

/**
 * RecordFile retrieval business logic
 */
class RecordFileService extends BaseService {
  constructor() {
    super();
  }

  static recordFileBlockDetailsFromTimestampQuery = `select 
    ${RecordFile.CONSENSUS_END}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName} 
    where  ${RecordFile.CONSENSUS_END} >= $1
    order by ${RecordFile.CONSENSUS_END} asc
    limit 1`;

  /**
   * Retrieves the recordFile containing the transaction of the given timestamp
   *
   * @param {string} timestamp encoded contract ID
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromTimestamp(timestamp) {
    const rows = await super.getRows(
      RecordFileService.recordFileBlockDetailsFromTimestampQuery,
      [timestamp],
      'getRecordFileBlockDetailsFromTimestamp'
    );
    if (_.isEmpty(rows) || rows.length > 1) {
      return null;
    }

    return new RecordFile(rows[0]);
  }
}

module.exports = new RecordFileService();
