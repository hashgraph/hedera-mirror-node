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

import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {InvalidArgumentError, NotFoundError} from './errors';
import {TopicMessage} from './model';
import * as utils from './utils';
import {TopicMessageViewModel} from './viewmodel';
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

const extractSqlForTopicMessagesLookup = (topicId, filters) => {
  let pgSqlQuery = `select lower(timestamp_range) as timestamp_start,upper(timestamp_range) as timestamp_end
                    from topic_message_lookup
                    where ${TopicMessageLookup.TOPIC_ID} = $1 `;
  const pgSqlParams = [topicId.getEncodedId()];

  // add filters
  let limit = defaultLimit;
  let order = constants.orderFilterValues.ASC;
  let equal = null;
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
      // validating seq number to have only one eq and no ne operators.
      if (filter.operator === utils.opsMap.eq) {
        if (!_.isNil(equal)) {
          throw new InvalidArgumentError(`Only one equal (eq) operator is allowed for ${filter.key}`);
        }
        equal = filter;
        lowerLimit = Number(filter.value);
        upperLimit = Number(filter.value);
        continue;
      }
      if (filter.operator === utils.opsMap.ne) {
        throw new InvalidArgumentError(`Not equal (ne) operator is not supported for ${filter.key}`);
      }

      // The lower limit is max of all lower limits
      if (filter.operator === utils.opsMap.gt) {
        lowerLimit = lowerLimit === undefined ? 0 : lowerLimit;
        lowerLimit = Math.max(lowerLimit, Number(filter.value) + 1);
      }
      if (filter.operator === utils.opsMap.gte) {
        lowerLimit = lowerLimit === undefined ? 0 : lowerLimit;
        lowerLimit = Math.max(lowerLimit, Number(filter.value));
      }

      // The upper limit is max of all upper limits
      if (filter.operator === utils.opsMap.lt) {
        upperLimit = upperLimit === undefined ? Number.MAX_SAFE_INTEGER : upperLimit;
        upperLimit = Math.min(upperLimit, Number(filter.value) - 1);
      }
      if (filter.operator === utils.opsMap.lte) {
        upperLimit = upperLimit === undefined ? Number.MAX_SAFE_INTEGER : upperLimit;
        upperLimit = Math.min(upperLimit, Number(filter.value));
      }
    }
  }
  // If the range built is empty shortcut with an empty response
  if (lowerLimit !== undefined && upperLimit !== undefined) {
    if (lowerLimit > upperLimit) {
      // need to return an empty response
      return null;
    }
  }
  // we add or subtract the limit depending on the order to obtain the range.
  if (upperLimit === undefined) {
    upperLimit = lowerLimit + Number(limit);
  } else if (lowerLimit === undefined) {
    lowerLimit = upperLimit - Number(limit);
  }

  pgSqlQuery += ` and  sequence_number_range && '[${lowerLimit},${upperLimit}]'::int8range`;
  pgSqlQuery += ` order by ${TopicMessageLookup.SEQUENCE_NUMBER_RANGE} ${order}`;

  pgSqlQuery += ` limit ${limit}`;

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
  let hasSequenceNumberForV2 = false;
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

    if (config.query.v2.topicMessageLookups && filter.key === constants.filterKeys.SEQUENCE_NUMBER) {
      // add sequence number range
      hasSequenceNumberForV2 = true;
      continue;
    }

    const columnKey = columnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    pgSqlQuery += ` and ${columnMap[filter.key]}${filter.operator}$${nextParamCount++}`;
    pgSqlParams.push(filter.value);
  }

  // Query the topic_message_lookup table only for V2
  if (config.query.v2.topicMessageLookups) {
    // If there is no sequence number in the request URL,
    // query the topic_message_lookup table for either the min or max sequence number depending on order.
    if (!hasSequenceNumberForV2) {
      if (order === constants.orderFilterValues.ASC) {
        pgSqlQuery += ` and sequence_number >= (select MIN(lower(sequence_number_range)) 
      from topic_message_lookup where ${TopicMessageLookup.TOPIC_ID} = $1)`;
      } else {
        pgSqlQuery += ` and sequence_number <= (select MAX(upper(sequence_number_range)) 
      from topic_message_lookup where ${TopicMessageLookup.TOPIC_ID} = $1)`;
      }
    } else {
      // this needs to be fixed and gotten values
      const rows = await getTopicMessageTimestamps(topicId, filters);
      if (rows === null) {
        return {};
      }
  if (rows.length) {
     const condition = rows.map((row) => `(consensus_timestamp >= ${...} and consensus_timestamp < ${...})`).join(' or ');
     pgSqlQuery += ` and (${condition})`;
   }
    }
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
  const pgObject = extractSqlForTopicMessagesLookup(topicId, filters);
  if (pgObject === null) {
    return null;
  }
  const params = pgObject.params;
  const query = pgObject.query;

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    throw new NotFoundError();
  }

  return _.isNil(rows) ? null : rows;
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
    extractSqlForTopicMessagesLookup,
    validateConsensusTimestampParam,
    validateGetSequenceMessageParams,
    validateGetTopicMessagesParams,
  });
}

export default topicmessage;
