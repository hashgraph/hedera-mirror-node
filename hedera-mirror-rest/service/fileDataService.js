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

const BaseService = require('./baseService');
const {Contract, ExchangeRate, FileData} = require('../model');
const utils = require('../utils');

/**
 * File data retrieval business logic
 */
class FileDataService extends BaseService {
  static exchangeRateFileId = 112;

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
      string_agg(encode(${FileData.getFullName(FileData.FILE_DATA)}, 'escape'), '' order by ${FileData.getFullName(
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

  // contract byte code
  // the query finds the file content valid at the contract's created timestamp T by aggregating the contents of all the
  // file* txs from the latest FileCreate or FileUpdate transaction before T, to T
  // Note the 'contract' relation is the cte not the 'contract' table
  static contractInitCodeFileDataQuery = `select
      string_agg(
          ${FileData.getFullName(FileData.FILE_DATA)}, ''
          order by ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)}
      ) bytecode
    from ${FileData.tableName} ${FileData.tableAlias}
    join ${Contract.tableName} ${Contract.tableAlias}
      on ${Contract.getFullName(Contract.FILE_ID)} = ${FileData.getFullName(FileData.ENTITY_ID)}
    where ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} >= (
      select ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)}
      from ${FileData.tableName} ${FileData.tableAlias}
      join ${Contract.tableName} ${Contract.tableAlias}
        on ${Contract.getFullName(Contract.FILE_ID)} = ${FileData.getFullName(FileData.ENTITY_ID)}
          and ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} <= ${Contract.getFullName(
    Contract.CREATED_TIMESTAMP
  )}
      where ${FileData.getFullName(FileData.TRANSACTION_TYPE)} = 17
        or (${FileData.getFullName(FileData.TRANSACTION_TYPE)} = 19 and length(${FileData.getFullName(
    FileData.FILE_DATA
  )}) <> 0)
      order by ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} desc
      limit 1
    ) and ${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)} <= ${Contract.getFullName(Contract.CREATED_TIMESTAMP)}
      and ${Contract.getFullName(Contract.FILE_ID)} is not null`;

  getContractInitCodeFiledataQuery = () => {
    return FileDataService.contractInitCodeFileDataQuery;
  };

  getLatestFileContentsQuery = (innerWhere = '') => {
    const outerWhere = innerWhere.replace('and ', `and ${FileData.tableAlias}.`);
    return FileDataService.latestFileContentsQuery
      .replace(FileDataService.filterInnerPlaceholder, innerWhere)
      .replace(FileDataService.filterOuterPlaceholder, outerWhere);
  };

  getLatestFileDataContents = async (fileId, filterQueries) => {
    const {where, params} = super.buildWhereSqlStatement(filterQueries.whereQuery, [fileId]);
    return await super.getSingleRow(this.getLatestFileContentsQuery(where), params, 'getLatestFileContents');
  };

  getExchangeRate = async (filterQueries) => {
    const row = await this.getLatestFileDataContents(FileDataService.exchangeRateFileId, filterQueries);

    return _.isNil(row) ? null : new ExchangeRate(row);
  };
}

module.exports = new FileDataService();
