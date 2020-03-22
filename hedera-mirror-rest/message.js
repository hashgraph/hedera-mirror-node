/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
const config = require('./config.js');
const utils = require('./utils.js');

const MESSAGE_NOT_FOUND = {message: 'hcs message not found'};

const validateParams = function(consensusTimestamp) {
  let badParams = [];
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    badParams.push({message: `Invalid parameter: consensusTimestamp`});
  }
  return utils.makeValidationResponse(badParams);
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTopicMessageRow = function(row) {
  return {
    consensus_timestamp: utils.nsToSecNs(row['consensus_timestamp']),
    topic_id: `${config.shard}.${row['realm_num']}.${row['topic_num']}`,
    message: utils.encodeBase64(row['message']),
    running_hash: utils.toHexString(row['running_hash']),
    sequence_number: parseInt(row['sequence_number'])
  };
};

const getMessage = function(consensusTimestampParam, httpResponse) {
  const validationResult = validateParams(consensusTimestampParam);
  if (!validationResult.isValid) {
    return new Promise((resolve, reject) => {
      httpResponse.status(validationResult.code).json(validationResult.contents._status);
      resolve();
    });
  }
  let consensusTimestamp = utils.parseTimestampParam(consensusTimestampParam);

  const pgSqlQuery = `SELECT * FROM topic_message WHERE consensus_timestamp = $1`;
  const pgSqlParams = [consensusTimestamp];
  return pool.query(pgSqlQuery, pgSqlParams).then(results => {
    // Since consensusTimestamp is primary key of topic_message table, only 0 and 1 rows are possible cases.
    if (results.rowCount === 1) {
      httpResponse.json(formatTopicMessageRow(results.rows[0]));
    } else {
      httpResponse.status(utils.httpStatusCodes.NOT_FOUND).json(MESSAGE_NOT_FOUND);
    }
  });
};

/**
 * Handler function for /message/:consensusTimestamp API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByConsensusTimestamp = function(req, res) {
  logger.debug('--------------------  getMessageByConsensusTimestamp --------------------');
  logger.debug(`Client: [ ${req.ip} ] URL: ${req.originalUrl}`);
  return getMessage(req.params.consensusTimestamp, res);
};

module.exports = {
  getMessageByConsensusTimestamp: getMessageByConsensusTimestamp
};
