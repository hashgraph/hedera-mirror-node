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
  'entity.publickey': sqlQueryColumns.PUBLIC_KEY,
  symbol: sqlQueryColumns.SYMBOL,
  'token.id': sqlQueryColumns.TOKEN_ID,
};

const tokensSelectQuery = `select t.token_id, symbol, e.key from token t`;
const accountIdJoinQuery = ` join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id`;
const entityIdJoinQuery = ` join t_entities e on e.id = t.token_id`;

const extractSqlFromTokenRequest = (pgSqlQuery, pgSqlParams, nextParamCount, orderBy, filters) => {
  // add filters
  let limit;
  let order = 'asc';
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

    pgSqlQuery += pgSqlParams.length == 0 ? ` where ` : ` and `;
    pgSqlQuery += `${filterColumnMap[filter.key]}${filter.operator}$${nextParamCount++}`;
    pgSqlParams.push(filter.value);
  }

  // add order
  pgSqlQuery += ` order by ${orderBy} ${order}`;

  // add limit
  pgSqlQuery += ` limit $${nextParamCount++}`;
  limit = limit === undefined ? config.maxLimit : limit;
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
    token_id: EntityId.fromEncodedId(row.token_id).toString(),
    symbol: row.symbol,
    admin_key: utils.encodeKey(row.key),
  };
};

/**
 * Verify topicId and sequencenumber meet entity_num format
 */
const validateTokenFilters = (accountId, filters) => {
  if (accountId && !utils.isValidEntityNum(accountId)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.ACCOUNT_ID);
  }

  utils.validateAndParseFilters(filters);
};

const getTokensRequest = async (req, res) => {
  const filters = utils.buildFilterObject(req.query);

  // if account.id filter is present join on token_account
  const accountFilter = req.query[constants.filterKeys.ACCOUNT_ID];
  let getTokensSqlQuery = tokensSelectQuery;
  let getTokenSqlParams = [];
  let nextParamCount = 1;

  if (accountFilter) {
    getTokensSqlQuery += accountIdJoinQuery;
    getTokenSqlParams.push(accountFilter);
    nextParamCount++;
  }

  getTokensSqlQuery += entityIdJoinQuery;

  // validate filters
  validateTokenFilters(accountFilter, filters);

  // build sql query validated param and filters
  const {query, params, order, limit} = extractSqlFromTokenRequest(
    `${getTokensSqlQuery}`,
    getTokenSqlParams,
    nextParamCount,
    sqlQueryColumns.TOKEN_ID,
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

    // populate next
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

module.exports = {
  getTokensRequest,
};

if (process.env.NODE_ENV === 'test') {
  module.exports = Object.assign(module.exports, {
    extractSqlFromTokenRequest,
    formatTokenRow,
    tokensSelectQuery,
    accountIdJoinQuery,
    entityIdJoinQuery,
  });
}
