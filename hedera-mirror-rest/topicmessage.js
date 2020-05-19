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
  RUNNING_HASH_VERSION: 'running_hash_version',
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
const formatTopicMessageRow = function (row, messageEncoding) {
  return {
    consensus_timestamp: utils.nsToSecNs(row[topicMessageColumns.CONSENSUS_TIMESTAMP]),
    topic_id: `${config.shard}.${row[topicMessageColumns.REALM_NUM]}.${row[topicMessageColumns.TOPIC_NUM]}`,
    message: utils.encodeBinary(row[topicMessageColumns.MESSAGE], messageEncoding),
    running_hash: utils.encodeBase64(row[topicMessageColumns.RUNNING_HASH]),
    running_hash_version: parseInt(row[topicMessageColumns.RUNNING_HASH_VERSION]),
    sequence_number: parseInt(row[topicMessageColumns.SEQUENCE_NUMBER]),
  };
};

/**
 * Handler function for /messages/:consensusTimestamp API.
 * Extracts and validates timestamp input, creates db query logic in preparation for db call to get message
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByConsensusTimestamp = async (req, res) => {
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
 * Handler function for /:id/messages/:sequencenumber API.
 * Extracts and validates topic and sequence params and creates db query statement in preparation for db call to get message
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByTopicAndSequenceRequest = async (req, res) => {
  const topicId = req.params.id;
  const seqNum = req.params.sequencenumber;
  validateGetSequenceMessageParams(topicId, seqNum);

  // handle topic stated as x.y.z vs z e.g. topic 7 vs topic 0.0.7. Defaults realm to 0 if not stated
  const entity = utils.parseEntityId(topicId);
  const pgSqlQuery = `select * from topic_message where realm_num = $1 and topic_num = $2 and sequence_number = $3 limit 1`;
  const pgSqlParams = [entity.realm, entity.num, seqNum];

  return getMessage(pgSqlQuery, pgSqlParams).then((message) => {
    res.locals[constants.responseDataLabel] = message;
  });
};

/**
 * Handler function for /topics/:id API.
 * @returns {Promise} Promise for PostgreSQL query
 */
const getTopicMessages = async (req, res) => {
  // retrieve param and filters from request
  const topicId = req.params.id;
  const filters = utils.buildFilterObject(req.query);

  // validate params
  validateGetTopicMessagesRequest(topicId, filters);

  // build sql query validated param and filters
  let {query, params, order, limit} = extractSqlFromTopicMessagesRequest(topicId, filters);

  const messageEncoding = req.query[constants.filterKeys.ENCODING];

  let topicMessagesResponse = {
    messages: [],
    links: {
      next: null,
    },
  };

  // get results and return formatted response
  return getMessages(query, params).then((messages) => {
    // format messages
    topicMessagesResponse.messages = messages.map((m) => formatTopicMessageRow(m, messageEncoding));

    // populate next
    let lastTimeStamp =
      topicMessagesResponse.messages.length > 0
        ? topicMessagesResponse.messages[topicMessagesResponse.messages.length - 1][
            topicMessageColumns.CONSENSUS_TIMESTAMP
          ]
        : null;

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
  let pgSqlQuery = `select * from topic_message where realm_num = $1 and topic_num = $2`;
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

    // handle keys that do not require formatting first
    if (filter.key === constants.filterKeys.ORDER) {
      order = filter.value;
      continue;
    }

    const columnKey = columnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    pgSqlQuery += ` and ${columnMap[filter.key]}${filter.operator}$${nextParamCount++}`;
    pgSqlParams.push(filter.value);
  }

  // add order
  pgSqlQuery += ` order by ${topicMessageColumns.CONSENSUS_TIMESTAMP} ${order}`;

  // add limit
  pgSqlQuery += ` limit $${nextParamCount++}`;
  limit = limit === undefined ? config.maxLimit : limit;
  pgSqlParams.push(limit);

  // close query
  pgSqlQuery += ';';

  return utils.buildPgSqlObject(pgSqlQuery, pgSqlParams, order, limit);
};

/**
 * Retrieves topic message from
 */
const getMessage = async (pgSqlQuery, pgSqlParams) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getMessage query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

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
  if (logger.isTraceEnabled()) {
    logger.trace(`getMessages query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  let messages = [];

  return pool
    .query(pgSqlQuery, pgSqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      for (let i = 0; i < results.rowCount; i++) {
        messages.push(results.rows[i]);
      }

      logger.debug('getMessages returning ' + messages.length + ' entries');

      return messages;
    });
};

module.exports = {
  extractSqlFromTopicMessagesRequest: extractSqlFromTopicMessagesRequest,
  formatTopicMessageRow: formatTopicMessageRow,
  getMessageByConsensusTimestamp: getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest: getMessageByTopicAndSequenceRequest,
  getTopicMessages: getTopicMessages,
  validateConsensusTimestampParam: validateConsensusTimestampParam,
  validateGetSequenceMessageParams: validateGetSequenceMessageParams,
  validateGetTopicMessagesParams: validateGetTopicMessagesParams,
};
