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
const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const {DbError} = require('./errors/dbError');
const {NotFoundError} = require('./errors/notFoundError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

// make the cost estimation of using the index on (topic_id, consensus_timestamp) lower than that of the primary key so
// pg planner will choose the better index when querying topic messages by id
const topicMessagesByIdQueryHint = 'set local random_page_cost = 0';

const topicMessageColumns = {
  CONSENSUS_TIMESTAMP: 'consensus_timestamp',
  MESSAGE: 'message',
  RUNNING_HASH: 'running_hash',
  RUNNING_HASH_VERSION: 'running_hash_version',
  SEQUENCE_NUMBER: 'sequence_number',
  TOPIC_ID: 'topic_id',
};

const columnMap = {
  sequencenumber: topicMessageColumns.SEQUENCE_NUMBER,
  [constants.filterKeys.TIMESTAMP]: topicMessageColumns.CONSENSUS_TIMESTAMP,
};

/**
 * Verify consensusTimestamp meets seconds or seconds.upto 9 digits format
 */
const validateConsensusTimestampParam = (consensusTimestamp) => {
  if (!utils.isValidTimestampParam(consensusTimestamp)) {
    throw InvalidArgumentError.forParams(topicMessageColumns.CONSENSUS_TIMESTAMP);
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

  if (!utils.isValidNum(seqNum)) {
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
 * Verify topicId exists and is a topic id
 *
 * @param {EntityId} topicId the topic ID object
 * @param {string} origTopicIdStr the original topic ID string
 * @return {Promise<void>}
 */
const validateTopicId = async (topicId, origTopicIdStr) => {
  const encodedId = topicId.getEncodedId();
  const pgSqlQuery = `SELECT tet.name
                      FROM entity te
                             JOIN t_entity_types tet
                                  ON te.type = tet.id
                      WHERE te.id = $1`;

  const {rows} = await pool.queryQuietly(pgSqlQuery, encodedId);
  if (_.isEmpty(rows)) {
    throw new NotFoundError(`No such topic id - ${origTopicIdStr}`);
  }

  if (rows[0].name !== 'topic') {
    throw new InvalidArgumentError(`${origTopicIdStr} is not a topic id`);
  }
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTopicMessageRow = (row, messageEncoding) => {
  return {
    consensus_timestamp: utils.nsToSecNs(row[topicMessageColumns.CONSENSUS_TIMESTAMP]),
    topic_id: EntityId.fromEncodedId(row[topicMessageColumns.TOPIC_ID]).toString(),
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

  const consensusTimestamp = utils.parseTimestampParam(consensusTimestampParam);

  const pgSqlQuery = `SELECT *
                      FROM topic_message
                      WHERE consensus_timestamp = $1`;
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
  const topicId = EntityId.fromString(topicIdStr);
  await validateTopicId(topicId, topicIdStr);

  // handle topic stated as x.y.z vs z e.g. topic 7 vs topic 0.0.7.
  const pgSqlQuery = `select *
                      from topic_message
                      where topic_id = $1
                        and sequence_number = $2
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

  const topicId = EntityId.fromString(topicIdStr);
  await validateTopicId(topicId, topicIdStr);

  // build sql query validated param and filters
  const {query, params, order, limit} = extractSqlFromTopicMessagesRequest(topicId, filters);

  const messageEncoding = req.query[constants.filterKeys.ENCODING];

  const topicMessagesResponse = {
    links: {
      next: null,
    },
  };

  // get results and return formatted response
  const messages = await getMessages(query, params, topicMessagesByIdQueryHint);
  topicMessagesResponse.messages = messages.map((m) => formatTopicMessageRow(m, messageEncoding));

  // populate next
  const lastTimeStamp =
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
};

const extractSqlFromTopicMessagesRequest = (topicId, filters) => {
  let pgSqlQuery = `select *
                    from topic_message
                    where topic_id = $1`;
  let nextParamCount = 2;
  const pgSqlParams = [topicId.getEncodedId()];

  // add filters
  let limit;
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

  const {rows} = await pool.queryQuietly(pgSqlQuery, ...pgSqlParams);
  // Since consensusTimestamp is primary key of topic_message table, only 0 and 1 rows are possible cases.
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug('getMessage returning single entry');
  return formatTopicMessageRow(rows[0]);
};

const getMessages = async (pgSqlQuery, pgSqlParams, preQueryHint = undefined) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getMessages query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  let result;
  if (!preQueryHint) {
    result = await pool.queryQuietly(pgSqlQuery, ...pgSqlParams);
  } else {
    let client;
    try {
      client = await pool.connect();
      await client.query('begin');
      await client.query(preQueryHint);
      result = await client.query(pgSqlQuery, pgSqlParams);
      await client.query('commit');
    } catch (err) {
      if (client !== undefined) {
        await client.query('rollback');
      }
      throw new DbError(err.message);
    } finally {
      if (client !== undefined) {
        client.release();
      }
    }
  }

  logger.debug(`getMessages returning ${result.rows.length} entries`);
  return result.rows;
};

module.exports = {
  extractSqlFromTopicMessagesRequest,
  formatTopicMessageRow,
  getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest,
  getTopicMessages,
  validateConsensusTimestampParam,
  validateGetSequenceMessageParams,
  validateGetTopicMessagesParams,
};
