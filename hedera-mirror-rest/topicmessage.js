/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const {NotFoundError} = require('./errors/notFoundError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {TopicMessage} = require('./model');
const {TopicMessageViewModel} = require('./viewmodel');

const columnMap = {
  sequencenumber: TopicMessage.SEQUENCE_NUMBER,
  [constants.filterKeys.TIMESTAMP]: TopicMessage.CONSENSUS_TIMESTAMP,
};

/**
 * Verify consensusTimestamp meets seconds or seconds.upto 9 digits format
 */
const validateConsensusTimestampParam = (consensusTimestamp) => {
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    throw InvalidArgumentError.forParams(TopicMessage.CONSENSUS_TIMESTAMP);
  }
};

/**
 * Verify topicId and sequencenumber meet entity num format
 */
const validateGetSequenceMessageParams = (topicId, seqNum) => {
  const badParams = [];
  if (!EntityId.isValidEntityId(topicId)) {
    badParams.push(constants.filterKeys.TOPIC_ID);
  }

  if (!utils.isPositiveLong(seqNum)) {
    badParams.push(constants.filterKeys.SEQUENCE_NUMBER);
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

/**
 * Verify topicId and sequencenumber meet entity num and limit format
 */
const validateGetTopicMessagesParams = (topicId) => {
  if (!EntityId.isValidEntityId(topicId)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.TOPIC_ID);
  }
};

/**
 * Handler function for /messages/:consensusTimestamp API.
 * Extracts and validates timestamp input, creates db query logic in preparation for db call to get message
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByConsensusTimestamp = async (req, res) => {
  const consensusTimestampParam = req.params.consensusTimestamp;
  validateConsensusTimestampParam(consensusTimestampParam);

  const consensusTimestamp = utils.parseTimestampParam(consensusTimestampParam);

  const pgSqlQuery = `select *
                      from ${TopicMessage.tableName}
                      where ${TopicMessage.CONSENSUS_TIMESTAMP} = $1`;
  const pgSqlParams = [consensusTimestamp];

  res.locals[constants.responseDataLabel] = await getMessage(pgSqlQuery, pgSqlParams);
};

/**
 * Handler function for /:id/messages/:sequenceNumber API.
 * Extracts and validates topic and sequence params and creates db query statement in preparation for db call to get
 * message
 *
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByTopicAndSequenceRequest = async (req, res) => {
  const topicIdStr = req.params.topicId;
  const seqNum = req.params.sequenceNumber;
  validateGetSequenceMessageParams(topicIdStr, seqNum);
  const topicId = EntityId.parse(topicIdStr);

  // handle topic stated as x.y.z vs z e.g. topic 7 vs topic 0.0.7.
  const pgSqlQuery = `select *
                      from ${TopicMessage.tableName}
                      where ${TopicMessage.TOPIC_ID} = $1
                        and ${TopicMessage.SEQUENCE_NUMBER} = $2
                      limit 1`;
  const pgSqlParams = [topicId.getEncodedId(), seqNum];

  res.locals[constants.responseDataLabel] = await getMessage(pgSqlQuery, pgSqlParams);
};

/**
 * Handler function for /topics/:topicId API.
 * @returns {Promise} Promise for PostgreSQL query
 */
const getTopicMessages = async (req, res) => {
  const topicIdStr = req.params.topicId;
  validateGetTopicMessagesParams(topicIdStr);
  const filters = utils.buildAndValidateFilters(req.query);

  const topicId = EntityId.parse(topicIdStr);

  // build sql query validated param and filters
  const {query, params, order, limit} = extractSqlFromTopicMessagesRequest(topicId, filters);

  const messageEncoding = req.query[constants.filterKeys.ENCODING];

  const topicMessagesResponse = {
    links: {
      next: null,
    },
  };

  // get results and return formatted response
  // set random_page_cost to 0 to make the cost estimation of using the index on (topic_id, consensus_timestamp)
  // lower than that of the primary key so pg planner will choose the better index when querying topic messages by id
  const messages = await getMessages(query, params, constants.zeroRandomPageCostQueryHint);
  topicMessagesResponse.messages = messages.map((m) => new TopicMessageViewModel(m, messageEncoding));

  // populate next
  const lastTimeStamp =
    topicMessagesResponse.messages.length > 0
      ? topicMessagesResponse.messages[topicMessagesResponse.messages.length - 1][TopicMessage.CONSENSUS_TIMESTAMP]
      : null;

  topicMessagesResponse.links.next = utils.getPaginationLink(
    req,
    topicMessagesResponse.messages.length !== limit,
    constants.filterKeys.TIMESTAMP,
    lastTimeStamp,
    order
  );

  res.locals[constants.responseDataLabel] = topicMessagesResponse;
};

const extractSqlFromTopicMessagesRequest = (topicId, filters) => {
  let pgSqlQuery = `select *
                    from ${TopicMessage.tableName}
                    where ${TopicMessage.TOPIC_ID} = $1`;
  let nextParamCount = 2;
  const pgSqlParams = [topicId.getEncodedId()];

  // add filters
  let limit = defaultLimit;
  let order = constants.orderFilterValues.ASC;
  for (const filter of filters) {
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
  pgSqlQuery += ` order by ${TopicMessage.CONSENSUS_TIMESTAMP} ${order}`;

  // add limit
  pgSqlQuery += ` limit $${nextParamCount++}`;
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

  const {rows} = await pool.queryQuietly(pgSqlQuery, pgSqlParams);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }
  const messages = rows.map((tm) => new TopicMessage(tm));
  // Since consensusTimestamp is primary key of topic_message table, only 0 and 1 rows are possible cases.

  logger.debug('getMessage returning single entry');

  return new TopicMessageViewModel(messages[0], messageEncoding);
};

const getMessages = async (pgSqlQuery, pgSqlParams, preQueryHint) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getMessages query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const {rows} = await pool.queryQuietly(pgSqlQuery, pgSqlParams, preQueryHint);
  logger.debug(`getMessages returning ${rows.length} entries`);
  return rows.map((row) => new TopicMessage(row));
};

module.exports = {
  extractSqlFromTopicMessagesRequest,
  getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest,
  getTopicMessages,
  validateConsensusTimestampParam,
  validateGetSequenceMessageParams,
  validateGetTopicMessagesParams,
};
