/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

  static getFileDataQuery = `select
         string_agg(
           ${FileData.FILE_DATA}, ''
           order by ${FileData.CONSENSUS_TIMESTAMP}
           ) data
        from ${FileData.tableName}
        where
           ${FileData.ENTITY_ID} = $1
        and ${FileData.CONSENSUS_TIMESTAMP} >= (
        select ${FileData.CONSENSUS_TIMESTAMP}
        from ${FileData.tableName}
        where ${FileData.ENTITY_ID} = $1
        and ${FileData.CONSENSUS_TIMESTAMP} <= $2
        and (${FileData.TRANSACTION_TYPE} = 17
             or ( ${FileData.TRANSACTION_TYPE} = 19
                  and
                  length(${FileData.FILE_DATA}) <> 0 ))
        order by ${FileData.CONSENSUS_TIMESTAMP} desc
        limit 1
        ) and ${FileData.CONSENSUS_TIMESTAMP} <= $2`;

  /**
   * The function returns the data for the fileId at the provided consensus timestamp.
   * @param fileId
   * @param timestamp
   * @return {data: string}
   */
  getFileData = async (fileId, timestamp) => {
    const params = [fileId, timestamp];
    const query = FileDataService.getFileDataQuery;
    const row = await super.getSingleRow(query, params, 'getFileData');
    return _.isNil(row) ? null : row.data;
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
