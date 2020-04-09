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
const constants = require('./constants.js');
const utils = require('./utils.js');
const {DbError} = require('./errors/dbError');
const {NotFoundError} = require('./errors/notFoundError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

const topicMessageColumns = {
  CONSENSUS_TIMESTAMP: 'consensus_timestamp',
  MESSAGE: 'message',
  REALM_NUM: 'realm_num',
  RUNNING_HASH: 'running_hash',
  SEQUENCE_NUMBER: 'sequence_number',
  TOPIC_NUM: 'topic_num',
};

const columnMap = {
  sequencenumber: topicMessageColumns.SEQUENCE_NUMBER,
  timestamp: topicMessageColumns.CONSENSUS_TIMESTAMP,
};

/**
 * Verify consensusTimestamp meets seconds or seconds.upto 9 digits format
 */
const validateConsensusTimestampParam = function (consensusTimestamp) {
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    throw InvalidArgumentError.forParams(topicMessageColumns.CONSENSUS_TIMESTAMP);
  }
};

/**
 * Verify topicId and sequencenumber meet entity_num format
 */
const validateGetSequenceMessageParams = function (topicId, seqNum) {
  let badParams = [];
  if (!utils.isValidEntityNum(topicId)) {
    badParams.push(topicMessageColumns.TOPIC_NUM);
  }

  if (!utils.isValidNum(seqNum)) {
    badParams.push(topicMessageColumns.SEQUENCE_NUMBER);
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

/**
 * Verify topicId and sequencenumber meet entity_num and limit format
 */
const validateGetTopicMessagesParams = function (topicId) {
  if (!utils.isValidEntityNum(topicId)) {
    throw InvalidArgumentError.forParams(topicMessageColumns.TOPIC_NUM);
  }
};

const validateGetTopicMessagesRequest = (topicId, filters) => {
  validateGetTopicMessagesParams(topicId);

  // validate filters
  utils.validateAndParseFilters(filters);
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTopicMessageRow = function (row) {
  return {
    consensus_timestamp: utils.nsToSecNs(row[topicMessageColumns.CONSENSUS_TIMESTAMP]),
    topic_id: `${config.shard}.${row[topicMessageColumns.REALM_NUM]}.${row[topicMessageColumns.TOPIC_NUM]}`,
    message: utils.encodeBase64(row[topicMessageColumns.MESSAGE]),
    running_hash: utils.encodeBase64(row[topicMessageColumns.RUNNING_HASH]),
    sequence_number: parseInt(row[topicMessageColumns.SEQUENCE_NUMBER]),
  };
};

/**
 * Extracts and validates timestamp input, creates db query logic in preparation for db call to get message
 */
const processGetMessageByConsensusTimestampRequest = (req, res) => {
  const consensusTimestampParam = req.params.consensusTimestamp;
  validateConsensusTimestampParam(consensusTimestampParam);

  let consensusTimestamp = utils.parseTimestampParam(consensusTimestampParam);

  const pgSqlQuery = `SELECT * FROM topic_message WHERE consensus_timestamp = $1`;
  const pgSqlParams = [consensusTimestamp];

  return getMessage(pgSqlQuery, pgSqlParams).then((message) => {
    res.locals[constants.responseDataLabel] = message;
  });
};

/**
 * Extracts and validates topic and sequence params and creates db query statement in preparation for db call to get message
 */
const processGetMessageByTopicAndSequenceRequest = (req, res) => {
  const topicId = req.params.id;
  const seqNum = req.params.sequencenumber;
  validateGetSequenceMessageParams(topicId, seqNum);

  // handle topic stated as x.y.z vs z e.g. topic 7 vs topic 0.0.7. Defaults realm to 0 if not stated
  const entity = utils.parseEntityId(topicId);
  const pgSqlQuery =
    'select consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number' +
    ' from topic_message where realm_num = $1 and topic_num = $2 and sequence_number = $3 limit 1';
  const pgSqlParams = [entity.realm, entity.num, seqNum];

  return getMessage(pgSqlQuery, pgSqlParams).then((message) => {
    res.locals[constants.responseDataLabel] = message;
  });
};

const processGetTopicMessages = (req, res) => {
  // retrieve param and filters from request
  const topicId = req.params.id;
  const filters = utils.buildFilterObject(req.query);

  // validate params
  validateGetTopicMessagesRequest(topicId, filters);

  // build sql query validated param and filters
  let {query, params, order, limit} = extractSqlFromTopicMessagesRequest(topicId, filters);

  let topicMessagesResponse = {
    messages: [],
    links: {
      next: null,
    },
  };

  // get results and return formatted response
  return getMessages(query, params).then((messages) => {
    topicMessagesResponse.messages = messages;

    // populate next
    let lastTimeStamp =
      messages.length > 0 ? messages[messages.length - 1][topicMessageColumns.CONSENSUS_TIMESTAMP] : null;

    topicMessagesResponse.links.next = utils.getPaginationLink(
      req,
      topicMessagesResponse.messages.length !== limit,
      constants.filterKeys.TIMESTAMP,
      lastTimeStamp,
      order
    );

    res.locals[constants.responseDataLabel] = topicMessagesResponse;
  });
};

const extractSqlFromTopicMessagesRequest = (topicId, filters) => {
  const entity = utils.parseEntityId(topicId);
  let pgSqlQuery =
    'select consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number' +
    ' from topic_message where realm_num = $1 and topic_num = $2';
  let nextParamCount = 3;
  let pgSqlParams = [entity.realm, entity.num];

  // add filters
  let limit;
  let order = 'asc';
  for (let filter of filters) {
    if (filter.key === constants.filterKeys.LIMIT) {
      limit = filter.value;
      continue;
    }

    if (filter.key === constants.filterKeys.ORDER) {
      order = filter.value;
      continue;
    }

    pgSqlQuery += ` and ${columnMap[filter.key]}${filter.operator}$${nextParamCount++}`;
    pgSqlParams.push(filter.value);
  }

  // add order
  pgSqlQuery += ` order by ${topicMessageColumns.CONSENSUS_TIMESTAMP} ${order}`;

  // add limit
  pgSqlQuery += ` limit $${nextParamCount++}`;
  limit = limit === undefined ? config.api.maxLimit : limit;
  pgSqlParams.push(limit);

  // close query
  pgSqlQuery += ';';

  return utils.buildPgSqlObject(pgSqlQuery, pgSqlParams, order, limit);
};

/**
 * Retrieves topic message from
 */
const getMessage = async (pgSqlQuery, pgSqlParams) => {
  logger.trace(`getMessage query: ${pgSqlQuery}, params: ${pgSqlParams}`);

  return pool
    .query(pgSqlQuery, pgSqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      // Since consensusTimestamp is primary key of topic_message table, only 0 and 1 rows are possible cases.
      if (results.rowCount === 1) {
        logger.debug('getMessage returning single entry');
        return formatTopicMessageRow(results.rows[0]);
      } else {
        throw new NotFoundError();
      }
    });
};

const getMessages = async (pgSqlQuery, pgSqlParams) => {
  logger.trace(`getMessages query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  let messages = [];

  return pool
    .query(pgSqlQuery, pgSqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      for (let i = 0; i < results.rowCount; i++) {
        messages.push(formatTopicMessageRow(results.rows[i]));
      }

      logger.debug('getMessages returning ' + messages.length + ' entries');

      return messages;
    });
};

/**
 * Handler function for /message/:consensusTimestamp API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByConsensusTimestamp = async (req, res) => {
  logger.debug('--------------------  getMessageByConsensusTimestamp --------------------');
  logger.debug(`Client: [ ${req.ip} ] URL: ${req.originalUrl}`);
  return processGetMessageByConsensusTimestampRequest(req, res);
};

/**
 * Handler function for /:id/message/:sequencenumber API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByTopicAndSequenceRequest = async (req, res) => {
  logger.debug('--------------------  getMessageByTopicAndSequenceRequest --------------------');
  logger.debug(`Client: [ ${req.ip} ] URL: ${req.originalUrl}`);
  return processGetMessageByTopicAndSequenceRequest(req, res);
};

/**
 * Handler function for /topics/:id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns Promise for PostgreSQL query
 */
const getTopicMessages = async (req, res) => {
  logger.debug('--------------------  getTopicMessages --------------------');
  logger.debug(`Client: [ ${req.ip} ] URL: ${req.originalUrl}`);
  return processGetTopicMessages(req, res);
};

module.exports = {
  extractSqlFromTopicMessagesRequest: extractSqlFromTopicMessagesRequest,
  formatTopicMessageRow: formatTopicMessageRow,
  getMessageByConsensusTimestamp: getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest: getMessageByTopicAndSequenceRequest,
  getTopicMessages: getTopicMessages,
  topicMessageColumns: topicMessageColumns,
  validateConsensusTimestampParam: validateConsensusTimestampParam,
  validateGetSequenceMessageParams: validateGetSequenceMessageParams,
  validateGetTopicMessagesParams: validateGetTopicMessagesParams,
};
