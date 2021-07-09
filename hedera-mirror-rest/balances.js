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

const formatBalancesResult = (req, result, limit, order) => {
  const {rows, sqlQuery} = result;
  const ret = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };

  if (rows.length > 0) {
    ret.timestamp = utils.nsToSecNs(rows[0].consensus_timestamp);
  }

  ret.balances = rows.map((row) => {
    return {
      account: EntityId.fromEncodedId(row.account_id).toString(),
      balance: Number(row.balance),
      tokens: utils.parseTokenBalances(row.token_balances),
    };
  });

  const anchorAccountId = ret.balances.length > 0 ? ret.balances[ret.balances.length - 1].account : 0;

  // Pagination links
  ret.links = {
    next: utils.getPaginationLink(
      req,
      ret.balances.length !== limit,
      constants.filterKeys.ACCOUNT_ID,
      anchorAccountId,
      order
    ),
  };

  if (utils.isTestEnv()) {
    ret.sqlQuery = sqlQuery;
  }

  return ret;
};

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = async (req, res) => {
  utils.validateReq(req);

  // Parse the filter parameters for credit/debit, account-numbers, timestamp and pagination
  const [accountQuery, accountParams] = utils.parseAccountIdQueryParam(req.query, 'ab.account_id');
  // transform the timestamp=xxxx or timestamp=eq:xxxx query in url to 'timestamp <= xxxx' SQL query condition
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'ab.consensus_timestamp', {
    [utils.opsMap.eq]: utils.opsMap.lte,
  });
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'e.public_key');
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.DESC);

  // Use the inner query to find the latest snapshot timestamp from the balance history table
  const innerQuery = `
      SELECT
        ab.consensus_timestamp
      FROM account_balance ab
      WHERE ${tsQuery === '' ? '1=1' : tsQuery}
      ORDER BY ab.consensus_timestamp DESC
      LIMIT 1`;

  const whereClause = `
      WHERE ${[`ab.consensus_timestamp = (${innerQuery})`, accountQuery, pubKeyQuery, balanceQuery]
        .filter((q) => q !== '')
        .join(' AND ')}`;

  // Only need to join entity if we're selecting on publickey.
  const joinEntityClause =
    pubKeyQuery !== ''
      ? `
      JOIN entity e
        ON e.id = ab.account_id
          AND e.type < ${utils.ENTITY_TYPE_FILE}`
      : '';

  // token balances pairs are aggregated as an array of json objects {token_id, balance}
  const sqlQuery = `
      SELECT
        ab.consensus_timestamp,
        ab.account_id,
        ab.balance,
        json_agg(
          json_build_object(
            'token_id', tb.token_id::text,
            'balance', tb.balance
          ) order by tb.token_id ${order}
        ) FILTER (WHERE tb.token_id IS NOT NULL) AS token_balances
      FROM account_balance ab
      LEFT JOIN token_balance tb
        ON ab.consensus_timestamp = tb.consensus_timestamp
          AND ab.account_id = tb.account_id
      ${joinEntityClause}
      ${whereClause}
      GROUP BY ab.consensus_timestamp, ab.account_id
      ORDER BY ab.account_id ${order}
      ${query}`;

  const sqlParams = tsParams.concat(accountParams).concat(pubKeyParams).concat(balanceParams).concat(params);
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getBalance query: ${pgSqlQuery} ${JSON.stringify(sqlParams)}`);
  }

  // Execute query
  const result = await pool.queryQuietly(pgSqlQuery, ...sqlParams);
  res.locals[constants.responseDataLabel] = formatBalancesResult(req, result, limit, order);
  logger.debug(`getBalances returning ${result.rows.length} entries`);
};

module.exports = {
  getBalances,
};
