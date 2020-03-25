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

/**
 * Verify consensusTimestamp meets seconds or seconds.upto 9 digits format
 */
const validateConsensusTimestampParam = function(consensusTimestamp) {
  let badParams = [];
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    badParams.push({message: `Invalid parameter: consensusTimestamp`});
  }
  return utils.makeValidationResponse(badParams);
};

/**
 * Verify topicId and seqNum meet entity_num format
 */
const validateGetSequenceMessageParams = function(topicId, seqNum) {
  let badParams = [];
  if (!utils.isValidEntityNum(topicId)) {
    badParams.push(utils.getInvalidParameterMessageObject('topic_num'));
  }

  if (!utils.isValidEntityNum(seqNum)) {
    badParams.push(utils.getInvalidParameterMessageObject('sequence_number'));
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
    running_hash: utils.encodeBase64(row['running_hash']),
    sequence_number: parseInt(row['sequence_number'])
  };
};

/**
 * Extracts and validates timestamp input, creates db query logic in preparation for db call to get message
 */
const processGetMessageByConsensusTimestampRequest = (params, httpResponse) => {
  const consensusTimestampParam = params.consensusTimestamp;
  const validationResult = validateConsensusTimestampParam(consensusTimestampParam);
  if (!validationResult.isValid) {
    return new Promise((resolve, reject) => {
      httpResponse.status(validationResult.code).json(validationResult.contents);
      resolve();
    });
  }

  let consensusTimestamp = utils.parseTimestampParam(consensusTimestampParam);

  const pgSqlQuery = `SELECT * FROM topic_message WHERE consensus_timestamp = $1`;
  const pgSqlParams = [consensusTimestamp];

  return getMessage(pgSqlQuery, pgSqlParams, httpResponse);
};

/**
 * Extracts and validates topic and sequence params and creates db query statement in preparation for db call to get message
 */
const processGetMessageByTopicAndSequenceRequest = (params, httpResponse) => {
  const topicId = params.id;
  const seqNum = params.seqnum;
  const validationResult = validateGetSequenceMessageParams(topicId, seqNum);
  if (!validationResult.isValid) {
    return new Promise((resolve, reject) => {
      httpResponse.status(validationResult.code).json(validationResult.contents);
      resolve();
    });
  }

  // handle topic stated as x.y.z vs z e.g. topic 7 vs topic 0.0.7. Defaults realm to 0 if not stated
  const entity = utils.parseEntityId(topicId);
  const pgSqlQuery =
    'select consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number' +
    ' from topic_message where realm_num = $1 and topic_num = $2 and sequence_number = $3 limit 1';
  const pgSqlParams = [entity.realm, entity.num, seqNum];

  return getMessage(pgSqlQuery, pgSqlParams, httpResponse);
};

/**
 * Retrieves topic message from
 */
const getMessage = function(pgSqlQuery, pgSqlParams, httpResponse) {
  return pool.query(pgSqlQuery, pgSqlParams).then(results => {
    // Since consensusTimestamp is primary key of topic_message table, only 0 and 1 rows are possible cases.
    if (results.rowCount === 1) {
      httpResponse.json(formatTopicMessageRow(results.rows[0]));
    } else {
      httpResponse
        .status(utils.httpStatusCodes.NOT_FOUND)
        .json(utils.createSingleErrorJsonResponse(utils.httpErrorMessages.NOT_FOUND));
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
  return processGetMessageByConsensusTimestampRequest(req.params, res);
};

/**
 * Handler function for /:id/message/:seqnum API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByTopicAndSequenceRequest = function(req, res) {
  logger.debug('--------------------  getMessageByTopicAndSequenceRequest --------------------');
  logger.debug(`Client: [ ${req.ip} ] URL: ${req.originalUrl}`);
  return processGetMessageByTopicAndSequenceRequest(req.params, res);
};

module.exports = {
  formatTopicMessageRow: formatTopicMessageRow,
  getMessageByConsensusTimestamp: getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest: getMessageByTopicAndSequenceRequest,
  validateConsensusTimestampParam: validateConsensusTimestampParam,
  validateGetSequenceMessageParams: validateGetSequenceMessageParams
};
