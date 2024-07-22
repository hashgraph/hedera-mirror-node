/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
 *
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
 */

import _ from 'lodash';
import BaseService from './baseService';
import {RecordFile} from '../model';
import {orderFilterValues} from '../constants.js';

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

  static recordFileBlockDetailsFromTimestampArrayQuery = `select
      ${RecordFile.CONSENSUS_END},
      ${RecordFile.CONSENSUS_START},
      ${RecordFile.INDEX},
      ${RecordFile.HASH},
      ${RecordFile.GAS_USED}
    from ${RecordFile.tableName}
    where ${RecordFile.CONSENSUS_END} in (
      select
       (select ${RecordFile.CONSENSUS_END} from ${RecordFile.tableName} where ${RecordFile.CONSENSUS_END} >= timestamp order by ${RecordFile.CONSENSUS_END} limit 1) as consensus_end
    from (select unnest($1::bigint[]) as timestamp) as tmp
      group by consensus_end
    )`;

  static recordFileBlockDetailsFromTimestampQuery = `select
    ${RecordFile.CONSENSUS_END}, ${RecordFile.GAS_USED}, ${RecordFile.HASH}, ${RecordFile.INDEX}
    from ${RecordFile.tableName}
    where  ${RecordFile.CONSENSUS_END} >= $1
    order by ${RecordFile.CONSENSUS_END}
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
    ${RecordFile.COUNT}, ${RecordFile.HASH}, ${RecordFile.NAME}, ${RecordFile.PREV_HASH},
    ${RecordFile.HAPI_VERSION_MAJOR}, ${RecordFile.HAPI_VERSION_MINOR}, ${RecordFile.HAPI_VERSION_PATCH},
    ${RecordFile.INDEX}, ${RecordFile.CONSENSUS_START}, ${RecordFile.CONSENSUS_END}, ${RecordFile.GAS_USED},
    ${RecordFile.LOGS_BLOOM}, coalesce(${RecordFile.SIZE}, length(${RecordFile.BYTES})) as size
    from ${RecordFile.tableName}
  `;

  /**
   * Retrieves the recordFile containing the transaction of the given timestamp
   *
   * @param {string|Number|BigInt} timestamp consensus timestamp
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromTimestamp(timestamp) {
    const row = await super.getSingleRow(RecordFileService.recordFileBlockDetailsFromTimestampQuery, [timestamp]);

    return _.isNull(row) ? null : new RecordFile(row);
  }

  /**
   * Retrieves the recordFiles containing the transactions of the given timestamps
   *
   * The timestamps must be ordered, either ACS or DESC.
   *
   * @param {(string|Number|BigInt)[]} timestamps consensus timestamp array
   * @return {Promise<Map>} A map from the consensus timestamp to its record file
   */
  async getRecordFileBlockDetailsFromTimestampArray(timestamps) {
    const recordFileMap = new Map();
    const timestampOrder = this.getTimestampOrder(timestamps);
    let query = RecordFileService.recordFileBlockDetailsFromTimestampArrayQuery;
    query += ` order by consensus_end ${timestampOrder}`;

    const rows = await super.getRows(query, [timestamps], 'getRecordFileBlockDetailsFromTimestampArray');

    let index = 0;
    for (const row of rows) {
      const recordFile = new RecordFile(row);
      const {consensusEnd, consensusStart} = recordFile;
      for (; index < timestamps.length; index++) {
        const timestamp = timestamps[index];
        if (consensusEnd >= timestamp && consensusStart <= timestamp) {
          recordFileMap.set(timestamp, recordFile);
        } else if (
          (timestampOrder === orderFilterValues.ASC && timestamp > consensusEnd) ||
          (timestampOrder === orderFilterValues.DESC && timestamp < consensusStart)
        ) {
          break;
        }
      }
    }

    return recordFileMap;
  }

  /**
   * Retrieves the recordFile with the given index
   *
   * @param {number} index Int8
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromIndex(index) {
    const row = await super.getSingleRow(RecordFileService.recordFileBlockDetailsFromIndexQuery, [index]);

    return _.isNull(row) ? null : new RecordFile(row);
  }

  /**
   * Retrieves the recordFile with the given index
   *
   * @param {string} hash
   * @return {Promise<RecordFile>} recordFile subset
   */
  async getRecordFileBlockDetailsFromHash(hash) {
    const row = await super.getSingleRow(RecordFileService.recordFileBlockDetailsFromHashQuery, [`${hash}%`]);

    return _.isNull(row) ? null : new RecordFile(row);
  }

  async getBlocks(filters) {
    const {where, params} = buildWhereSqlStatement(filters.whereQuery);

    const query =
      RecordFileService.blocksQuery +
      `
      ${where}
      order by ${filters.orderBy} ${filters.order}
      limit ${filters.limit}
    `;
    const rows = await super.getRows(query, params);
    return rows.map((recordFile) => new RecordFile(recordFile));
  }

  async getByHashOrNumber(hash, number) {
    let whereStatement = '';
    const params = [];
    if (hash) {
      hash = hash.toLowerCase();
      whereStatement += `${RecordFile.HASH} like $1`;
      params.push(hash + '%');
    } else {
      whereStatement += `${RecordFile.INDEX} = $1`;
      params.push(number);
    }

    const query = `${RecordFileService.blocksQuery} where ${whereStatement}`;
    const row = await super.getSingleRow(query, params);
    return row ? new RecordFile(row) : null;
  }

  getTimestampOrder(timestamps) {
    if (timestamps.length === 0) {
      return orderFilterValues.ASC;
    }

    const first = timestamps[0];
    const last = timestamps[timestamps.length - 1];
    return first > last ? orderFilterValues.DESC : orderFilterValues.ASC;
  }
}

export default new RecordFileService();
