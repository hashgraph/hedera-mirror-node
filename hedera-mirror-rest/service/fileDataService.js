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

import _ from 'lodash';

import BaseService from './baseService';
import {ExchangeRate, FileData, FeeSchedule} from '../model';

/**
 * File data retrieval business logic
 */
class FileDataService extends BaseService {
  static exchangeRateFileId = 112;
  static feeScheduleFileId = 111;

  // placeholders to support where filtering for inner and outer calls
  static filterInnerPlaceholder = '<filterInnerPlaceHolder>';
  static filterOuterPlaceholder = '<filterOuterPlaceHolder>';

  // retrieve the largest timestamp of the most recent create/update operation on the file
  // using this timestamp retrieve all recent file operations and combine contents for applicable file
  static latestFileContentsQuery = `with latest_create as (
      select max(${FileData.CONSENSUS_TIMESTAMP}) as ${FileData.CONSENSUS_TIMESTAMP}
      from ${FileData.tableName}
      where ${FileData.ENTITY_ID} = $1 and ${FileData.TRANSACTION_TYPE} in (17, 19) ${
    FileDataService.filterInnerPlaceholder
  }
      group by ${FileData.ENTITY_ID}
      order by ${FileData.CONSENSUS_TIMESTAMP} desc
    )
    select
      max(${FileData.tableAlias}.${FileData.CONSENSUS_TIMESTAMP}) as ${FileData.CONSENSUS_TIMESTAMP},
      string_agg(${FileData.getFullName(FileData.FILE_DATA)}, '' order by ${FileData.getFullName(
    FileData.CONSENSUS_TIMESTAMP
  )}) as ${FileData.FILE_DATA}
    from ${FileData.tableName} ${FileData.tableAlias}
    join latest_create l on ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} >= l.${FileData.CONSENSUS_TIMESTAMP}
    where ${FileData.getFullName(FileData.ENTITY_ID)} = $1 and ${FileData.getFullName(
    FileData.TRANSACTION_TYPE
  )} in (16,17, 19)
      and ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} >= l.${FileData.CONSENSUS_TIMESTAMP} ${
    FileDataService.filterOuterPlaceholder
  }
    group by ${FileData.getFullName(FileData.ENTITY_ID)}`;

  /**
   * The function returns the query and params to get the active file content for the fileId at the provided consensus timestamp.
   * @param fileId
   * @param timestamp
   * @return {query: string, params: any[]}
   */
  getFileData = (fileId, timestamp) => {
    const params = [fileId, timestamp];

    return {
      query: [
        `select
         string_agg(
           f.file_data, ''
           order by f.consensus_timestamp
           ) data
        from file_data f
        where
           f.entity_id = $1
        and f.consensus_timestamp >= (
        select f.consensus_timestamp
        from file_data f
        where f.entity_id = $1
        and f.consensus_timestamp <= $2
        and (f.transaction_type = 17
             or ( f.transaction_type = 19
                  and
                  length(f.file_data) <> 0 ))
        order by f.consensus_timestamp desc
        limit 1
        ) and f.consensus_timestamp <= $2`,
      ].join('\n'),
      params,
    };
  };


  getLatestFileContentsQuery = (innerWhere = '') => {
    const outerWhere = innerWhere.replace('and ', `and ${FileData.tableAlias}.`);
    return FileDataService.latestFileContentsQuery
      .replace(FileDataService.filterInnerPlaceholder, innerWhere)
      .replace(FileDataService.filterOuterPlaceholder, outerWhere);
  };

  getLatestFileDataContents = async (fileId, filterQueries) => {
    const {where, params} = super.buildWhereSqlStatement(filterQueries.whereQuery, [fileId]);
    return super.getSingleRow(this.getLatestFileContentsQuery(where), params, 'getLatestFileContents');
  };

  getExchangeRate = async (filterQueries) => {
    const row = await this.getLatestFileDataContents(FileDataService.exchangeRateFileId, filterQueries);
    return _.isNil(row) ? null : new ExchangeRate(row);
  };

  getFeeSchedule = async (filterQueries) => {
    const row = await this.getLatestFileDataContents(FileDataService.feeScheduleFileId, filterQueries);
    return _.isNil(row) ? null : new FeeSchedule(row);
  };
}

export default new FileDataService();
