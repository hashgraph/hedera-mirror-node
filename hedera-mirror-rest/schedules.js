/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {NotFoundError} from './errors';
import {SignatureType} from './model';
import * as utils from './utils';

const {default: defaultLimit} = getResponseLimit();

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

const commonSelectFields = [
  's.consensus_timestamp',
  's.creator_account_id',
  'e.deleted',
  's.executed_timestamp',
  's.expiration_time',
  'e.key',
  'e.memo',
  's.payer_account_id',
  's.schedule_id',
  's.transaction_body',
  's.wait_for_expiry',
].join(',\n');
const transactionSignatureJsonAgg = `
  json_agg(json_build_object(
    'consensus_timestamp', ts.consensus_timestamp,
    'public_key_prefix', encode(ts.public_key_prefix, 'base64'),
    'signature', encode(ts.signature, 'base64'),
    'type', ts.type
  ) order by ts.consensus_timestamp)`;
const getScheduleByIdQuery = `
  select
    ${commonSelectFields},
    ${transactionSignatureJsonAgg} as signatures
  from schedule s
  left join entity e on e.id = s.schedule_id
  left join transaction_signature ts on ts.entity_id = s.schedule_id
  where s.schedule_id = $1
  group by s.schedule_id, e.id`;
const schedulesMainQuery = `
  select
    ${commonSelectFields},
    (
      select ${transactionSignatureJsonAgg}
      from transaction_signature ts
      where ts.entity_id = s.schedule_id
    ) as signatures
  from schedule s
  left join entity e on e.id = s.schedule_id`;

const scheduleLimitQuery = (paramCount) => `limit $${paramCount}`;
const scheduleOrderQuery = (order) => `order by s.schedule_id ${order}`;

/**
 * Get the schedules list sql query to be used given the where clause, order and param count
 * @param whereQuery
 * @param order
 * @param count
 * @returns {string}
 */
const getSchedulesQuery = (whereQuery, order, count) => {
  return [schedulesMainQuery, whereQuery, scheduleOrderQuery(order), scheduleLimitQuery(count)].join('\n');
};

const formatScheduleRow = (row) => {
  const signatures = row.signatures
    ? row.signatures
        .filter((signature) => signature.consensus_timestamp !== null)
        .map((signature) => ({
          consensus_timestamp: utils.nsToSecNs(signature.consensus_timestamp),
          public_key_prefix: signature.public_key_prefix,
          signature: signature.signature,
          type: SignatureType.getName(signature.type),
        }))
    : [];

  return {
    admin_key: utils.encodeKey(row.key),
    deleted: row.deleted,
    consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
    creator_account_id: EntityId.parse(row.creator_account_id).toString(),
    executed_timestamp: utils.nsToSecNs(row.executed_timestamp),
    expiration_time: utils.nsToSecNs(row.expiration_time),
    memo: row.memo,
    payer_account_id: EntityId.parse(row.payer_account_id).toString(),
    schedule_id: EntityId.parse(row.schedule_id).toString(),
    signatures,
    transaction_body: utils.encodeBase64(row.transaction_body),
    wait_for_expiry: row.wait_for_expiry,
  };
};

/**
 * Handler function for /schedules/:scheduleId API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getScheduleById = async (req, res) => {
  utils.validateReq(req);
  const parseOptions = {allowEvmAddress: false, paramName: constants.filterKeys.SCHEDULEID};
  const scheduleId = EntityId.parse(req.params.scheduleId, parseOptions).getEncodedId();
  if (logger.isTraceEnabled()) {
    logger.trace(`getScheduleById query: ${getScheduleByIdQuery}, params: ${scheduleId}`);
  }
  const {rows} = await pool.queryQuietly(getScheduleByIdQuery, scheduleId);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  res.locals[constants.responseDataLabel] = formatScheduleRow(rows[0]);
};

/**
 * Extract the sql where clause, params, order and limit values from the provided schedule query param filters
 * If no modifying filters are provided the default of no where clause, the defaultLimit and asc order will be returned
 * @param filters
 * @returns {{limit: Number, params: [*], filterQuery: string, order: string}}
 */
const extractSqlFromScheduleFilters = (filters) => {
  const filterQuery = {
    filterQuery: '',
    params: [defaultLimit],
    order: constants.orderFilterValues.ASC,
    limit: defaultLimit,
  };

  // if no filters return default filter of no where clause, defaultLimit and asc order
  if (filters && filters.length === 0) {
    return filterQuery;
  }

  const pgSqlParams = [];
  let whereQuery = '';
  let applicableFilters = 0; // track the number of schedule specific filters
  let paramCount = 1; // track the param count used for substitution, not affected by order and executed params

  for (const filter of filters) {
    if (filter.key === constants.filterKeys.LIMIT) {
      filterQuery.limit = filter.value;
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

  const {rows} = await pool.queryQuietly(pgSqlQuery, pgSqlParams);
  logger.debug(`getScheduleEntities returning ${rows.length} entries`);

  return rows.map((m) => formatScheduleRow(m));
};

/**
 * Handler function for /schedules API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getSchedules = async (req, res) => {
  // extract filters from query param
  const filters = utils.buildAndValidateFilters(req.query, acceptedSchedulesParameters);

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
    {
      [constants.filterKeys.SCHEDULE_ID]: lastScheduleId,
    },
    order
  );

  res.locals[constants.responseDataLabel] = schedulesResponse;
};

const schedules = {
  getScheduleById,
  getSchedules,
};

const acceptedSchedulesParameters = new Set([
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.SCHEDULE_ID,
]);

if (utils.isTestEnv()) {
  Object.assign(schedules, {
    extractSqlFromScheduleFilters,
    formatScheduleRow,
  });
}

export default schedules;
