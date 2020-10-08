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

const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {DbError} = require('./errors/dbError');

/**
 * Handler function for /tokens/:id/balances API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTokenSupplyDistribution = async (req, res) => {
  let tokenId;
  try {
    tokenId = EntityId.fromString(req.params.id);
  } catch (err) {
    throw InvalidArgumentError.forParams('token.id');
  }

  // validate query parameters
  utils.validateReq(req);

  // transform the timestamp=xxxx or timestamp=eq:xxxx query in url to 'timestamp <= xxxx' SQL query condition
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'consensus_timestamp', {
    [utils.opsMap.eq]: utils.opsMap.lte,
  });
  const [accountQuery, accountParams] = utils.parseAccountIdQueryParam(req.query, 'tb.account_id');
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'tb.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'e.ed25519_public_key_hex');
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req, 'desc');

  // Use the inner query to find the latest snapshot timestamp from the table
  const innerTsQuery = `
      SELECT
        tb.consensus_timestamp
      FROM token_balance tb
      WHERE ${tsQuery === '' ? '1=1' : tsQuery}
      ORDER BY tb.consensus_timestamp DESC
      LIMIT 1`;

  const joinEntityClause =
    pubKeyQuery !== ''
      ? `JOIN t_entities e
      ON e.fk_entity_type_id = ${utils.ENTITY_TYPE_ACCOUNT}
        AND e.id = tb.account_id
        AND ${pubKeyQuery}`
      : '';

  const whereConditions = [`tb.consensus_timestamp = (${innerTsQuery})`, 'tb.token_id = ?', accountQuery, balanceQuery];
  const whereClause = `WHERE ${whereConditions.filter((q) => q !== '').join(' AND ')}`;

  const sqlQuery = `
    SELECT
      tb.consensus_timestamp,
      tb.account_id,
      tb.balance
    FROM token_balance tb
    ${joinEntityClause}
    ${whereClause}
    ORDER BY tb.account_id ${order}
    ${query}`;

  const tokenIdParams = [tokenId.getEncodedId().toString()];
  const sqlParams = pubKeyParams
    .concat(tsParams)
    .concat(tokenIdParams)
    .concat(accountParams)
    .concat(balanceParams)
    .concat(params);
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);

  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenSupplyDistribution query: ${pgSqlQuery} ${JSON.stringify(sqlParams)}`);
  }

  let result;
  try {
    result = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  const ret = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };

  if (result.rows.length > 0) {
    ret.timestamp = utils.nsToSecNs(result.rows[0].consensus_timestamp);
  }

  ret.balances = result.rows.map((row) => {
    return {
      account: EntityId.fromString(row.account_id).toString(),
      balance: Number(row.balance),
    };
  });

  const anchorAccountId = ret.balances.length > 0 ? ret.balances[ret.balances.length - 1].account : 0;

  // Pagination links
  ret.links = {
    next: utils.getPaginationLink(req, ret.balances.length !== limit, 'account.id', anchorAccountId, order),
  };

  if (process.env.NODE_ENV === 'test') {
    ret.sqlQuery = result.sqlQuery;
  }

  logger.debug(`getTokenSupplyDistribution returning ${ret.balances.length} entries`);
  res.locals[constants.responseDataLabel] = ret;
};

module.exports = {
  getTokenSupplyDistribution,
};
