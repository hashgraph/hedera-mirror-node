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

const {proto} = require('@hashgraph/proto');
const _ = require('lodash');

const BaseService = require('./baseService');
const {Contract, ExchangeRate, FileData} = require('../model');

/**
 * File data retrieval business logic
 */
class FileDataService extends BaseService {
  static exchangeRateFileId = 112;

  static latestFileDataContentQuery = `select ${FileData.CONSENSUS_TIMESTAMP}, encode(${FileData.FILE_DATA}, 'escape') contents 
    from ${FileData.tableName} where ${FileData.ENTITY_ID} = $1 and ${FileData.CONSENSUS_TIMESTAMP} < $2
    order by ${FileData.CONSENSUS_TIMESTAMP} desc limit 1`;

  // get most recent file by combining file data of matching entity since a given timestamp
  static fileDataQuery = `with latest_update as (
      select max(${FileData.CONSENSUS_TIMESTAMP}) as timestamp, ${FileData.ENTITY_ID}
      from ${FileData.tableName}
      where ${FileData.CONSENSUS_TIMESTAMP} < $1 and ${FileData.ENTITY_ID} = $2 and ${
    FileData.TRANSACTION_TYPE
  } in (16,17) and 
      group by ${FileData.ENTITY_ID}
    )
    select encode(string_agg (${FileData.getFullName(FileData.FILE_DATA)}, ',' order by ${FileData.getFullName(
    FileData.CONSENSUS_TIMESTAMP
  )}), 'hex') as contents,
      max(${FileData.getFullName(FileData.CONSENSUS_TIMESTAMP)}) as timestamp
    from ${FileData.tableName} ${FileData.tableAlias}
    join latest_update l on ${FileData.getFullName(FileData.ENTITY_ID)} = l.entity_id and ${FileData.getFullName(
    FileData.CONSENSUS_TIMESTAMP
  )} >= l.timestamp
    where ${FileData.getFullName(FileData.ENTITY_ID)} = $2 and length(${FileData.getFullName(FileData.FILE_DATA)}) <> 0
      and ${FileData.getFullName(FileData.TRANSACTION_TYPE)} in (16,17,19) and ${FileData.getFullName(
    FileData.CONSENSUS_TIMESTAMP
  )} >= l.timestamp
    group by ${FileData.getFullName(FileData.ENTITY_ID)}`;

  // contract byte code
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

  getFileContents = async (entity, timestamp) => {
    const row = await super.getSingleRow(
      FileDataService.latestFileDataContentQuery,
      [entity, timestamp],
      'getLatestFileContents'
    );
    return _.isNil(row) ? null : row;
  };

  getExchangeRate = async (timestamp) => {
    const row = await this.getFileContents(FileDataService.exchangeRateFileId, timestamp);

    return _.isNil(row) ? null : new ExchangeRate(row);
  };
}

module.exports = new FileDataService();
