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
  [constants.filterKeys.TIMESTAMP]: topicMessageColumns.CONSENSUS_TIMESTAMP,
};

const topicIdValidator = {
  get: (params) => params.id,
  name: topicMessageColumns.TOPIC_NUM,
  parse: EntityId.fromString,
  validate: (v) => EntityId.isValidEntityId(v),
};

const sequenceNumberValidator = {
  get: (params) => params.sequencenumber,
  name: topicMessageColumns.SEQUENCE_NUMBER,
  parse: (v) => v,
  validate: (v) => utils.isValidNum(v),
};

const consensusTimestampValidator = {
  get: (params) => params.consensusTimestamp,
  name: topicMessageColumns.CONSENSUS_TIMESTAMP,
  parse: utils.parseTimestampParam,
  validate: (v) => utils.isValidTimestampParam(v),
};

const validateRequestPathParams = (params, ...validators) => {
  const badParams = [];
  const result = {};

  validators.forEach((validator) => {
    const value = validator.get(params);
    if (!validator.validate(value)) {
      badParams.push(validator.name);
      return;
    }

    result[validator.name] = validator.parse(value);
  });

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }

  return result;
};

/**
 * Verify topicId exists and is a topic id
 *
 * @param {EntityId} topicId the topic ID object
 * @return {Promise<void>}
 */
const validateTopicId = async (topicId) => {
  const encodedId = topicId.getEncodedId();
  const pgSqlQuery = `SELECT tet.name
                      FROM entity te
                             JOIN t_entity_types tet
                                  ON te.type = tet.id
                      WHERE te.id = $1`;

  const {rows} = await utils.queryQuietly(pgSqlQuery, encodedId);
  if (_.isEmpty(rows)) {
    throw new NotFoundError(`No such topic id - ${topicId}`);
  }

  if (rows[0].name !== 'topic') {
    throw new InvalidArgumentError(`${topicId} is not a topic id`);
  }
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTopicMessageRow = (row, messageEncoding) => {
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
  const params = validateRequestPathParams(req.params, consensusTimestampValidator);

  const pgSqlQuery = `SELECT *
                      FROM topic_message
                      WHERE consensus_timestamp = $1`;
  const pgSqlParams = [params[consensusTimestampValidator.name]];
  res.locals[constants.responseDataLabel] = await getMessage(pgSqlQuery, pgSqlParams);
};

/**
 * Handler function for /:id/messages/:sequencenumber API.
 * Extracts and validates topic and sequence params and creates db query statement in preparation for db call to get
 * message
 *
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByTopicAndSequenceRequest = async (req, res) => {
  const params = validateRequestPathParams(req.params, topicIdValidator, sequenceNumberValidator);
  const topicId = params[topicIdValidator.name];
  await validateTopicId(topicId);

  // handle topic stated as x.y.z vs z e.g. topic 7 vs topic 0.0.7. Defaults realm to 0 if not stated
  const pgSqlQuery = `select *
                      from topic_message
                      where realm_num = $1
                        and topic_num = $2
                        and sequence_number = $3
                      limit 1`;
  const pgSqlParams = [topicId.realm, topicId.num, params[sequenceNumberValidator.name]];

  res.locals[constants.responseDataLabel] = await getMessage(pgSqlQuery, pgSqlParams);
};

const getAndValidateTopicIdRequestPathParam = (req) => {
  const topicIdString = req.params.id;
  return EntityId.fromString(topicIdString, topicMessageColumns.TOPIC_NUM);
};

/**
 * Handler function for /topics/:id API.
 * @returns {Promise} Promise for PostgreSQL query
 */
const getTopicMessages = async (req, res) => {
  // validate path param topic id and query params
  const pathParams = validateRequestPathParams(req.params, topicIdValidator);
  const topicId = pathParams[topicIdValidator.name];
  const filters = await utils.buildAndValidateFilters(req.query);

  // validate params
  await validateTopicId(topicId);

  // build sql query validated param and filters
  const {query, params, order, limit} = extractSqlFromTopicMessagesRequest(topicId, filters);

  const messageEncoding = req.query[constants.filterKeys.ENCODING];

  const topicMessagesResponse = {
    links: {
      next: null,
    },
  };

  // get results and return formatted response
  const messages = await getMessages(query, params);
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
                    where realm_num = $1
                      and topic_num = $2`;
  let nextParamCount = 3;
  const pgSqlParams = [topicId.realm, topicId.num];

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

  const {rows} = await utils.queryQuietly(pgSqlQuery, ...pgSqlParams);
  // Since consensusTimestamp is primary key of topic_message table, only 0 and 1 rows are possible cases.
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug('getMessage returning single entry');
  return formatTopicMessageRow(rows[0]);
};

const getMessages = async (pgSqlQuery, pgSqlParams) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getMessages query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const {rows} = await utils.queryQuietly(pgSqlQuery, ...pgSqlParams);
  logger.debug(`getMessages returning ${rows.length} entries`);
  return rows;
};

module.exports = {
  extractSqlFromTopicMessagesRequest,
  formatTopicMessageRow,
  getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest,
  getTopicMessages,
  // validateConsensusTimestampParam,
  // validateGetSequenceMessageParams,
  // validateGetTopicMessagesParams,
};
