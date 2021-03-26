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

const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const {NotFoundError} = require('./errors/notFoundError');

const scheduleSelectFields = [
  'e.key',
  's.consensus_timestamp',
  'creator_account_id',
  'executed_timestamp',
  'e.memo',
  'payer_account_id',
  's.schedule_id',
  'transaction_body',
  `json_agg(
    json_build_object(
      'consensus_timestamp', ts.consensus_timestamp::text,
      'public_key_prefix', encode(ts.public_key_prefix, 'base64'),
      'signature', encode(ts.signature, 'base64')
    ) order by ts.consensus_timestamp
  ) as signatures`,
];

// select columns
const sqlQueryColumns = {
  ACCOUNT: 'creator_account_id',
  SCHEDULE_ID: 's.schedule_id',
};

// query to column maps
const filterColumnMap = {
  [constants.filterKeys.ACCOUNT_ID]: sqlQueryColumns.ACCOUNT,
  [constants.filterKeys.SCHEDULE_ID]: sqlQueryColumns.SCHEDULE_ID,
};

const entityIdJoinQuery = 'join t_entities e on e.id = s.schedule_id';
const groupByQuery = 'group by e.key, e.memo, s.consensus_timestamp, s.schedule_id';
const scheduleIdMatchQuery = 'where s.schedule_id = $1';
const scheduleLimitQuery = (paramCount) => `limit $${paramCount}`;
const scheduleOrderQuery = (order) => `order by s.consensus_timestamp ${order}`;
const scheduleSelectQuery = ['select', scheduleSelectFields.join(',\n'), 'from schedule s'].join('\n');
const signatureJoinQuery = 'left join transaction_signature ts on ts.entity_id = s.schedule_id';

const getScheduleByIdQuery = [
  scheduleSelectQuery,
  entityIdJoinQuery,
  signatureJoinQuery,
  scheduleIdMatchQuery,
  groupByQuery,
].join('\n');

/**
 * Get the schedules list sql query to be used given the where clause, order and param count
 * @param whereQuery
 * @param order
 * @param count
 * @returns {string}
 */
const getSchedulesQuery = (whereQuery, order, count) => {
  return [
    scheduleSelectQuery,
    entityIdJoinQuery,
    signatureJoinQuery,
    whereQuery,
    groupByQuery,
    scheduleOrderQuery(order),
    scheduleLimitQuery(count),
  ].join('\n');
};

const formatScheduleRow = (row) => {
  const signatures = row.signatures
    .filter((signature) => signature.consensus_timestamp !== null)
    .map((signature) => {
      return {
        consensus_timestamp: utils.nsToSecNs(signature.consensus_timestamp),
        public_key_prefix: signature.public_key_prefix,
        signature: signature.signature,
      };
    });

  return {
    admin_key: utils.encodeKey(row.key),
    consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
    creator_account_id: EntityId.fromEncodedId(row.creator_account_id).toString(),
    executed_timestamp: row.executed_timestamp === null ? null : utils.nsToSecNs(row.executed_timestamp),
    memo: row.memo,
    payer_account_id: EntityId.fromEncodedId(row.payer_account_id).toString(),
    schedule_id: EntityId.fromEncodedId(row.schedule_id).toString(),
    signatures,
    transaction_body: utils.encodeBase64(row.transaction_body),
  };
};

/**
 * Handler function for /schedules/:id API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getScheduleById = async (req, res) => {
  const scheduleId = EntityId.fromString(req.params.id, constants.filterKeys.SCHEDULEID).getEncodedId();
  if (logger.isTraceEnabled()) {
    logger.trace(`getScheduleById query: ${getScheduleByIdQuery}, params: ${scheduleId}`);
  }
  const {rows} = await utils.queryQuietly(getScheduleByIdQuery, scheduleId);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  res.locals[constants.responseDataLabel] = formatScheduleRow(rows[0]);
};

/**
 * Extract the sql where clause, params, order and limit values to be used from the provided schedule query param filters
 * If no modifying filters are provided the default of no where clause, the config maxLimit and asc order will be returned
 * @param filters
 * @returns {{limit: Number, params: [*], filterQuery: string, order: string}}
 */
const extractSqlFromScheduleFilters = (filters) => {
  const filterQuery = {
    filterQuery: '',
    params: [config.maxLimit],
    order: constants.orderFilterValues.ASC,
    limit: config.maxLimit,
  };

  // if no filters return default filter of no where clause, maxLimit and asc order
  if (filters && filters.length === 0) {
    return filterQuery;
  }

  const pgSqlParams = [];
  let whereQuery = '';
  let applicableFilters = 0; // track the number of schedule specific filters
  let paramCount = 1; // track the param count used for substitution, not affected by order and executed params

  for (const filter of filters) {
    if (filter.key === constants.filterKeys.LIMIT) {
      filterQuery.limit = Number(filter.value);
      continue;
    }

    if (filter.key === constants.filterKeys.ORDER) {
      filterQuery.order = filter.value;
      continue;
    }

    const columnKey = filterColumnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    // add prefix. 'where' for the 1st param and 'and' for subsequent
    whereQuery += applicableFilters === 0 ? `where ` : ` and `;
    applicableFilters++;

    whereQuery += `${filterColumnMap[filter.key]}${filter.operator}$${paramCount}`;
    paramCount++;
    pgSqlParams.push(filter.value);
  }

  // add limit
  pgSqlParams.push(filterQuery.limit);

  filterQuery.filterQuery = whereQuery;
  filterQuery.params = pgSqlParams;

  return filterQuery;
};

/**
 * Get formatted schedule entities from db
 * @param pgSqlQuery
 * @param pgSqlParams
 * @returns {Promise<void>}
 */
const getScheduleEntities = async (pgSqlQuery, pgSqlParams) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getScheduleById query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const {rows} = await utils.queryQuietly(pgSqlQuery, ...pgSqlParams);
  logger.debug(`getScheduleEntities returning ${rows.length} entries`);

  return rows.map((m) => formatScheduleRow(m));
};

/**
 * Handler function for /schedules/:id API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getSchedules = async (req, res) => {
  // extract filters from query param
  const filters = utils.buildFilterObject(req.query);

  // validate filters
  await utils.validateAndParseFilters(filters);

  // get sql filter query, params, order and limit from query filters
  const {filterQuery, params, order, limit} = extractSqlFromScheduleFilters(filters);
  const schedulesQuery = getSchedulesQuery(filterQuery, order, params.length);

  const schedulesResponse = {
    schedules: [],
    links: {
      next: null,
    },
  };

  schedulesResponse.schedules = await getScheduleEntities(schedulesQuery, params);

  // populate next link
  const lastScheduleId =
    schedulesResponse.schedules.length > 0
      ? schedulesResponse.schedules[schedulesResponse.schedules.length - 1].schedule_id
      : null;

  schedulesResponse.links.next = utils.getPaginationLink(
    req,
    schedulesResponse.schedules.length !== limit,
    constants.filterKeys.SCHEDULE_ID,
    lastScheduleId,
    order
  );

  res.locals[constants.responseDataLabel] = schedulesResponse;
};

module.exports = {
  getScheduleById,
  getSchedules,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    extractSqlFromScheduleFilters,
    formatScheduleRow,
  });
}
