/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

import Bound from './controllers/bound.js';
import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {InvalidArgumentError, NotFoundError} from './errors';
import {TopicMessage} from './model';
import * as utils from './utils';
import {TopicMessageViewModel} from './viewmodel';
import {filterKeys} from './constants';
import _ from 'lodash';
import config from './config.js';
import TopicMessageLookup from './model/topicMessageLookup.js';

const {default: defaultLimit} = getResponseLimit();

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
  if (!EntityId.isValidEntityId(topicId, false)) {
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
  if (!EntityId.isValidEntityId(topicId, false)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.TOPIC_ID);
  }
};

/**
 * Handler function for /messages/:consensusTimestamp API.
 * Extracts and validates timestamp input, creates db query logic in preparation for db call to get message
 * @return {Promise} Promise for PostgreSQL query
 */
const getMessageByConsensusTimestamp = async (req, res) => {
  utils.validateReq(req);
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
  utils.validateReq(req);
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
  const filters = utils.buildAndValidateFilters(req.query, acceptedTopicsParameters);

  const topicId = EntityId.parse(topicIdStr);

  // build sql query validated param and filters
  const {query, params, order, limit} = await extractSqlFromTopicMessagesRequest(topicId, filters);

  const messageEncoding = req.query[constants.filterKeys.ENCODING];

  const topicMessagesResponse = {
    links: {
      next: null,
    },
  };

  // get results and return formatted response
  // if limit is not 1, set random_page_cost to 0 to make the cost estimation of using the index on
  // (topic_id, consensus_timestamp) lower than that of the primary key so pg planner will choose the better index
  // when querying topic messages by id
  const queryHint = limit !== 1 ? constants.zeroRandomPageCostQueryHint : undefined;
  const messages = await getMessages(query, params, queryHint);
  topicMessagesResponse.messages = messages.map((m) => new TopicMessageViewModel(m, messageEncoding));

  // populate next
  const lastTimeStamp =
    topicMessagesResponse.messages.length > 0
      ? topicMessagesResponse.messages[topicMessagesResponse.messages.length - 1][TopicMessage.CONSENSUS_TIMESTAMP]
      : null;

  topicMessagesResponse.links.next = utils.getPaginationLink(
    req,
    topicMessagesResponse.messages.length !== limit,
    {
      [constants.filterKeys.TIMESTAMP]: lastTimeStamp,
    },
    order
  );

  res.locals[constants.responseDataLabel] = topicMessagesResponse;
};

const extractSqlForTopicMessagesLookup = async (topicId, filters) => {
  let pgSqlQuery = `select numrange(min(lower(timestamp_range)), max(upper(timestamp_range)))
                    from topic_message_lookup
                    where ${TopicMessageLookup.TOPIC_ID} = $1 `;
  const pgSqlParams = [topicId.getEncodedId()];

  // add filters
  let limit = defaultLimit;
  let order = constants.orderFilterValues.ASC;
  let hasSequenceNumber = false;
  const bound = new Bound(filterKeys.SERIAL_NUMBER, 'serial_number');
  let lowerLimit = undefined;
  let upperLimit = undefined;
  for (const filter of filters) {
    if (filter.key === constants.filterKeys.LIMIT) {
      limit = filter.value;
      continue;
    }

    if (filter.key === constants.filterKeys.ORDER) {
      order = filter.value;
      continue;
    }

    if (filter.key === constants.filterKeys.SEQUENCE_NUMBER) {
      // validating seq number for invalid operators
      bound.parse(filter);

      if (utils.gtGte.includes(filter.operator)) {
        lowerLimit = Number(filter.value);
      }
      if (utils.ltLte.includes(filter.operator)) {
        upperLimit = Number(filter.value);
      }
      // check for null
      if (lowerLimit !== undefined && upperLimit !== undefined) {
        if (lowerLimit === upperLimit) {
          // need to return an empty response
          return null;
        }
      }
      console.log(`lowerLimit is ${lowerLimit}`);
      // we add or subtract the limit depending on the order to obtain the range.
      if (upperLimit === undefined && order === constants.orderFilterValues.ASC) {
        upperLimit = lowerLimit + limit;
        console.log(`upperLimit is ${upperLimit}`);
      } else if (lowerLimit === undefined && order === constants.orderFilterValues.DESC) {
        lowerLimit = upperLimit - limit;
      }

      // add sequence number range
      hasSequenceNumber = true;
    }
  }
  if (!hasSequenceNumber) {
    pgSqlQuery += `  group by ${TopicMessageLookup.PARTITION} order by ${TopicMessageLookup.PARTITION} ${order}`;
    // add limit
    pgSqlQuery += ` limit ${limit}`;
  } else {
    pgSqlQuery += ` and  sequence_number_range && '[${lowerLimit},${upperLimit})'::int8range`;
  }
  // close query
  pgSqlQuery += ';';
  return utils.buildPgSqlObject(pgSqlQuery, pgSqlParams, order, limit);
};
const extractSqlFromTopicMessagesRequest = async (topicId, filters) => {
  let pgSqlQuery = `select *
                    from ${TopicMessage.tableName}
                    where ${TopicMessage.TOPIC_ID} = $1`;
  let nextParamCount = 2;
  const pgSqlParams = [topicId.getEncodedId()];
  // add filters
  let limit = defaultLimit;
  let order = constants.orderFilterValues.ASC;

  if (config.query.v2.topicMessageLookups) {
    const timestamp_range = await getTopicMessageTimestamps(topicId, filters);
    if (timestamp_range === null) {
      return {};
    }
    pgSqlQuery += ` and ${TopicMessage.CONSENSUS_TIMESTAMP} >= ${timestamp_range.numrange.begin}  and ${TopicMessage.CONSENSUS_TIMESTAMP} < ${timestamp_range.numrange.end}`;
  }

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

    if (filter.key === constants.filterKeys.SEQUENCE_NUMBER) {
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

const getTopicMessageTimestamps = async (topicId, filters) => {
  const pgObject = await extractSqlForTopicMessagesLookup(topicId, filters);
  if (pgObject === null) {
    return null;
  }
  const params = pgObject.params;
  const query = pgObject.query;

  const row = await pool.queryQuietly(query, params);
  if (row.rows.length !== 1) {
    throw new NotFoundError();
  }

  return _.isNil(row) ? null : row.rows[0];
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

  return new TopicMessageViewModel(messages[0]);
};

const getMessages = async (pgSqlQuery, pgSqlParams, preQueryHint) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getMessages query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const {rows} = await pool.queryQuietly(pgSqlQuery, pgSqlParams, preQueryHint);
  logger.debug(`getMessages returning ${rows.length} entries`);
  return rows.map((row) => new TopicMessage(row));
};

const topicmessage = {
  getMessageByConsensusTimestamp,
  getMessageByTopicAndSequenceRequest,
  getTopicMessages,
};

const acceptedTopicsParameters = new Set([
  constants.filterKeys.ENCODING,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.SEQUENCE_NUMBER,
  constants.filterKeys.TIMESTAMP,
]);

if (utils.isTestEnv()) {
  Object.assign(topicmessage, {
    extractSqlFromTopicMessagesRequest,
    validateConsensusTimestampParam,
    validateGetSequenceMessageParams,
    validateGetTopicMessagesParams,
  });
}

export default topicmessage;
