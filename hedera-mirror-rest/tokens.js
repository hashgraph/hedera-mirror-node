/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

'use strict';

const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const {DbError} = require('./errors/dbError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');

// select columns
const sqlQueryColumns = {
  KEY: 'e.key',
  SYMBOL: 't.symbol',
  TOKEN_ID: 't.token_id',
  PUBLIC_KEY: 'e.ed25519_public_key_hex',
};

// query to column maps
const filterColumnMap = {
  publickey: sqlQueryColumns.PUBLIC_KEY,
  symbol: sqlQueryColumns.SYMBOL,
  'token.id': sqlQueryColumns.TOKEN_ID,
};

// sql queries
const tokensSelectQuery = `select t.token_id, symbol, e.key from token t`;
const accountIdJoinQuery = ` join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id`;
const entityIdJoinQuery = ` join t_entities e on e.id = t.token_id`;

/**
 * Given top level select columns and filters from request query, extract filters and create final sql query with
 * appropriate where clauses.
 */
const extractSqlFromTokenRequest = (pgSqlQuery, pgSqlParams, nextParamCount, filters) => {
  // add filters
  let limit = config.maxLimit;
  let order = 'asc';
  let applicableFilters = 0;
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

    const columnKey = filterColumnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    pgSqlQuery += applicableFilters === 0 ? ` where ` : ` and `;
    applicableFilters++;
    pgSqlQuery += `${filterColumnMap[filter.key]}${filter.operator}$${nextParamCount++}`;
    pgSqlParams.push(filter.value);
  }

  // add order
  pgSqlQuery += ` order by ${sqlQueryColumns.TOKEN_ID} ${order}`;

  // add limit
  pgSqlQuery += ` limit $${nextParamCount++}`;
  pgSqlParams.push(limit);

  // close query
  pgSqlQuery += ';';

  return utils.buildPgSqlObject(pgSqlQuery, pgSqlParams, order, limit);
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTokenRow = (row) => {
  return {
    token_id: EntityId.fromString(row.token_id).toString(),
    symbol: row.symbol,
    admin_key: utils.encodeKey(row.key),
  };
};

const getTokensRequest = async (req, res) => {
  // extract filters from query param
  const filters = utils.buildFilterObject(req.query);

  // validate filters
  utils.validateAndParseFilters(filters);

  let getTokensSqlQuery = tokensSelectQuery;
  let getTokenSqlParams = [];
  let nextParamCount = 1;

  // if account.id filter is present join on token_account
  const accountFilter = req.query[constants.filterKeys.ACCOUNT_ID];
  if (accountFilter) {
    getTokensSqlQuery += accountIdJoinQuery;
    getTokenSqlParams.push(accountFilter);
    nextParamCount++;
  }

  // add join with entities table to sql query
  getTokensSqlQuery += entityIdJoinQuery;

  // build final sql query
  const {query, params, order, limit} = extractSqlFromTokenRequest(
    getTokensSqlQuery,
    getTokenSqlParams,
    nextParamCount,
    filters
  );

  const tokensResponse = {
    tokens: [],
    links: {
      next: null,
    },
  };

  return getTokens(query, params).then((tokens) => {
    // format messages
    tokensResponse.tokens = tokens.map((m) => formatTokenRow(m));

    // populate next link
    const lastTokenId =
      tokensResponse.tokens.length > 0 ? tokensResponse.tokens[tokensResponse.tokens.length - 1].token_id : null;
    tokensResponse.links.next = utils.getPaginationLink(
      req,
      tokens.length !== limit,
      constants.filterKeys.TOKEN_ID,
      lastTokenId,
      order
    );

    res.locals[constants.responseDataLabel] = tokensResponse;
  });
};

const getTokens = async (pgSqlQuery, pgSqlParams) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokens query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const tokens = [];

  return pool
    .query(pgSqlQuery, pgSqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      for (let i = 0; i < results.rowCount; i++) {
        tokens.push(results.rows[i]);
      }

      logger.debug(`getTokens returning ${tokens.length} entries`);

      return tokens;
    });
};

// token balances select columns
const tokenBalancesSqlQueryColumns = {
  ACCOUNT_BALANCE: 'tb.balance',
  ACCOUNT_ID: 'tb.account_id',
  CONSENSUS_TIMESTAMP: 'tb.consensus_timestamp',
  ACCOUNT_PUBLICKEY: 'e.ed25519_public_key_hex',
  TOKEN_ID: 'tb.token_id',
};

// token balances query to column maps
const tokenBalancesFilterColumnMap = {
  'account.balance': tokenBalancesSqlQueryColumns.ACCOUNT_BALANCE,
  'account.id': tokenBalancesSqlQueryColumns.ACCOUNT_ID,
};

const tokenBalancesSelectQuery = `
  select
    tb.consensus_timestamp,
    tb.account_id,
    tb.balance
  from token_balance tb`;

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {EntityId} tokenId token ID object
 * @param {string} pgSqlQuery initial pg SQL query string
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromTokenBalancesRequest = (tokenId, pgSqlQuery, filters) => {
  const {opsMap} = utils;

  let limit = config.maxLimit;
  let order = 'desc';
  let joinEntityClause = '';
  let whereClause = `where ${tokenBalancesSqlQueryColumns.TOKEN_ID} = $1`;
  let nextParamCount = 2;
  let tsQueryWhereClause = '';
  const pgSqlParams = [tokenId.getEncodedId()];

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.ACCOUNT_PUBLICKEY:
        joinEntityClause = `join t_entities e
          on e.fk_entity_type_id = ${utils.ENTITY_TYPE_ACCOUNT}
          and e.id = ${tokenBalancesSqlQueryColumns.ACCOUNT_ID}
          and ${tokenBalancesSqlQueryColumns.ACCOUNT_PUBLICKEY} = $${nextParamCount++}`;
        pgSqlParams.push(filter.value);
        break;
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      case constants.filterKeys.TIMESTAMP:
        // transform '=' operator for timestamp to '<='
        const op = filter.operator !== opsMap.eq ? filter.operator : opsMap.lte;
        tsQueryWhereClause = `where ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} ${op} $${nextParamCount++}`;
        pgSqlParams.push(filter.value);
        break;
      default:
        const columnKey = tokenBalancesFilterColumnMap[filter.key];
        if (!columnKey) {
          break;
        }

        whereClause = `${whereClause}
          and ${columnKey} ${filter.operator} $${nextParamCount++}`;
        pgSqlParams.push(filter.value);
        break;
    }
  }

  whereClause = `${whereClause}
    and ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} = (
      select
        ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP}
      from token_balance tb
      ${tsQueryWhereClause}
      order by ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} desc
      limit 1
    )`;
  const query = `${pgSqlQuery}
    ${joinEntityClause}
    ${whereClause}
    order by ${tokenBalancesSqlQueryColumns.ACCOUNT_ID} ${order}
    limit $${nextParamCount}`;
  pgSqlParams.push(limit);
  return utils.buildPgSqlObject(query, pgSqlParams, order, limit);
};

const formatTokenBalanceRow = (row) => {
  return {
    account: EntityId.fromString(row.account_id).toString(),
    balance: Number(row.balance),
  };
};

/**
 * Handler function for /tokens/:id/balances API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTokenBalances = async (req, res) => {
  let tokenId;
  try {
    tokenId = EntityId.fromString(req.params.id);
  } catch (err) {
    throw InvalidArgumentError.forParams('tokenId');
  }

  const filters = utils.buildFilterObject(req.query);
  utils.validateAndParseFilters(filters);

  const {query, params, limit, order} = extractSqlFromTokenBalancesRequest(tokenId, tokenBalancesSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenBalances query: ${query} ${JSON.stringify(params)}`);
  }

  return pool
    .query(query, params)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((result) => {
      const {rows} = result;
      const responseData = {
        timestamp: rows.length > 0 ? utils.nsToSecNs(rows[0].consensus_timestamp) : null,
        balances: rows.map((row) => formatTokenBalanceRow(row)),
        links: {
          next: null,
        },
      };

      const anchorAccountId =
        responseData.balances.length > 0 ? responseData.balances[responseData.balances.length - 1].account : 0;
      // Pagination links
      responseData.links.next = utils.getPaginationLink(
        req,
        responseData.balances.length !== limit,
        constants.filterKeys.ACCOUNT_ID,
        anchorAccountId,
        order
      );

      logger.debug(`getTokenBalances returning ${responseData.balances.length} entries`);
      res.locals[constants.responseDataLabel] = responseData;
    });
};

module.exports = {
  getTokensRequest,
  getTokenBalances,
};

if (process.env.NODE_ENV === 'test') {
  module.exports = Object.assign(module.exports, {
    extractSqlFromTokenRequest,
    formatTokenRow,
    tokensSelectQuery,
    accountIdJoinQuery,
    entityIdJoinQuery,
    tokenBalancesSelectQuery,
    extractSqlFromTokenBalancesRequest,
    formatTokenBalanceRow,
  });
}
