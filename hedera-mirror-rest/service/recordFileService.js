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

const _ = require('lodash');

const utils = require('../utils');
const BaseService = require('./baseService');
const {RecordFile} = require('../model');

const buildWhereSqlStatement = (whereQuery) => {
  let where = '';
  const params = [];
  for (let i = 1; i <= whereQuery.length; i++) {
    where += `${i === 1 ? 'where' : 'and'} ${whereQuery[i - 1].query} $${i} `;
    params.push(whereQuery[i - 1].param);
  }

  return {where, params};
};

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

  static recordFileBlockDetailsFromIndexQuery = `select
    ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName}
    where  ${RecordFile.INDEX} = $1
    limit 1`;

  static recordFileBlockDetailsFromHashQuery = `select
    ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName}
    where  ${RecordFile.HASH} like $1
    limit 1`;

  static blocksQuery = `select
    ${RecordFile.COUNT}, ${RecordFile.HASH}, ${RecordFile.NAME}, ${RecordFile.PREV_HASH}, ${RecordFile.BYTES},
    ${RecordFile.HAPI_VERSION_MAJOR}, ${RecordFile.HAPI_VERSION_MINOR}, ${RecordFile.HAPI_VERSION_PATCH},
    ${RecordFile.INDEX}, ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}
    from ${RecordFile.tableName}
  `;

  /**
   * Retrieves the recordFile containing the transaction of the given timestamp
   *
   * @param {string|Number|BigInt} timestamp consensus timestamp
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromTimestamp(timestamp) {
    const row = await super.getSingleRow(
      RecordFileService.recordFileBlockDetailsFromTimestampQuery,
      [timestamp],
      'getRecordFileBlockDetailsFromTimestamp'
    );

    return _.isNull(row) ? null : new RecordFile(row);
  }

  /**
   * Retrieves the recordFile with the given index
   *
   * @param {number} index Int8
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromIndex(index) {
    const row = await super.getSingleRow(
      RecordFileService.recordFileBlockDetailsFromIndexQuery,
      [index],
      'getRecordFileBlockDetailsFromIndex'
    );

    return _.isNull(row) ? null : new RecordFile(row);
  }

  /**
   * Retrieves the recordFile with the given index
   *
   * @param {string} hash
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromHash(hash) {
    const row = await super.getSingleRow(
      RecordFileService.recordFileBlockDetailsFromHashQuery,
      [`${hash}%`],
      'getRecordFileBlockDetailsFromHash'
    );

    return _.isNull(row) ? null : new RecordFile(row);
  }

  async getBlocks(filters) {
    const {where, params} = buildWhereSqlStatement(filters.whereQuery);

    const query =
      RecordFileService.blocksQuery +
      `
      ${where}
      order by ${RecordFile.INDEX} ${filters.order}
      limit ${filters.limit}
    `;
    const rows = await super.getRows(query, params, 'getBlocks');

    return rows.map((recordFile) => new RecordFile(recordFile));
  }

  async getByHashOrNumber(hash, number) {
    let whereStatement = '';
    let params = [];
    if (hash) {
      const hashWithPrefix = utils.addHexPrefix(hash);
      const hashWithoutPrefix = hashWithPrefix.substring(2);

      whereStatement += `${RecordFile.HASH} like $1`;
      params.push(hashWithoutPrefix + '%');
    } else {
      whereStatement += `${RecordFile.INDEX} = $1`;
      params.push(number);
    }

    const query =
      RecordFileService.blocksQuery +
      `
      where ${whereStatement}
    `;

    const row = await super.getSingleRow(query, params, 'getByHashOrNumber');

    return row ? new RecordFile(row) : null;
  }
}

module.exports = new RecordFileService();
