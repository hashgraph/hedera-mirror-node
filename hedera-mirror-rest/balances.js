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

const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const {DbError} = require('./errors/dbError');

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = async (req, res) => {
  utils.validateReq(req);

  // Parse the filter parameters for credit/debit, account-numbers,
  // timestamp and pagination
  const [accountQuery, accountParams] = utils.parseAccountIdQueryParam(req.query, 'ab.account_id');

  // if the request has a timestamp=xxxx or timestamp=eq:xxxxx, then
  // modify that to be timestamp <= xxxx, so we return the latest balances
  // as of the user-supplied timestamp.
  if ('timestamp' in req.query) {
    const pattern = /^(eq:)?(\d*\.?\d*)$/;
    const replacement = 'lte:$2';
    if (Array.isArray(req.query.timestamp)) {
      for (let index = 0; index < req.query.timestamp.length; index++) {
        req.query.timestamp[index] = req.query.timestamp[index].replace(pattern, replacement);
      }
    } else {
      req.query.timestamp = req.query.timestamp.replace(pattern, replacement);
    }
  }

  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'ab.consensus_timestamp');
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'e.ed25519_public_key_hex');
  const joinEntities = pubKeyQuery !== ''; // Only need to join t_entites if we're selecting on publickey.
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req, 'desc');

  // Use the inner query to find the latest snapshot timestamp from the balance history table
  const innerQuery = `
      SELECT
        consensus_timestamp
      FROM account_balance ab
      WHERE ${tsQuery === '' ? '1=1' : tsQuery}
      ORDER BY consensus_timestamp DESC
      LIMIT 1`;

  let sqlQuery = `
      SELECT
        ab.consensus_timestamp,
        ab.account_id,
        ab.balance,
        string_agg(cast(tb.token_id AS VARCHAR), ',') AS token_ids,
        string_agg(cast(tb.balance AS VARCHAR), ',') AS token_balances
      FROM account_balance ab
      LEFT JOIN token_balance tb
        ON ab.consensus_timestamp = tb.consensus_timestamp
          AND ab.account_id = tb.account_id`;
  if (joinEntities) {
    sqlQuery += `
      JOIN t_entities e
        ON e.id = ab.account_id
          AND e.entity_shard = ${config.shard}
          AND e.fk_entity_type_id < ${utils.ENTITY_TYPE_FILE}`;
  }

  sqlQuery += `
      WHERE ab.consensus_timestamp = (${innerQuery})
        AND ${[accountQuery, pubKeyQuery, balanceQuery].map((q) => (q === '' ? '1=1' : q)).join(' AND ')}
      GROUP BY ab.consensus_timestamp, ab.account_id
      ORDER BY ab.account_id ${order}
      ${query}`;

  const sqlParams = tsParams.concat(accountParams).concat(pubKeyParams).concat(balanceParams).concat(params);
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);

  if (logger.isTraceEnabled()) {
    logger.trace(`getBalance query: ${pgSqlQuery} ${JSON.stringify(sqlParams)}`);
  }

  // Execute query
  return pool
    .query(pgSqlQuery, sqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((result) => {
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

      const entityIdStrFromEncodedId = (encodedId) => EntityId.fromEncodedId(encodedId).toString();
      ret.balances = result.rows.map((row) => {
        const accountBalance = {
          account: entityIdStrFromEncodedId(row.account_id),
          balance: Number(row.balance),
          tokens: [],
        };

        if (row.token_ids) {
          const tokenIds = row.token_ids.split(',');
          const tokenBalances = row.token_balances.split(',');
          accountBalance.tokens = tokenIds.map((tokenId, index) => {
            const tokenBalance = tokenBalances[index];
            return {
              token_id: entityIdStrFromEncodedId(tokenId),
              balance: Number(tokenBalance),
            };
          });
        }

        return accountBalance;
      });

      const anchorAccountId = ret.balances.length > 0 ? ret.balances[ret.balances.length - 1].account : 0;

      // Pagination links
      ret.links = {
        next: utils.getPaginationLink(req, ret.balances.length !== limit, 'account.id', anchorAccountId, order),
      };

      if (process.env.NODE_ENV === 'test') {
        ret.sqlQuery = result.sqlQuery;
      }

      logger.debug(`getBalances returning ${ret.balances.length} entries`);

      res.locals[constants.responseDataLabel] = ret;
    });
};

module.exports = {
  getBalances,
};
