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

// token discovery sql queries
const tokensSelectQuery = 'select t.token_id, symbol, e.key from token t';
const accountIdJoinQuery = 'join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id';
const entityIdJoinQuery = 'join t_entities e on e.id = t.token_id';

// token info sql queries
const tokenInfoSelectFields = [
  'symbol,token_id',
  'name',
  'decimals',
  'initial_supply',
  'total_supply',
  'treasury_account_id',
  'created_timestamp',
  'freeze_default',
  'e.key',
  'kyc_key',
  'freeze_key',
  'wipe_key',
  'supply_key',
  'e.exp_time_ns',
  'e.auto_renew_account_id',
  'e.auto_renew_period',
  'modified_timestamp',
];
const tokenInfoSelectQuery = ['select', tokenInfoSelectFields.join(',\n'), 'from token t'].join('\n');
const tokenIdMatchQuery = 'where token_id = $1';

/**
 * Given top level select columns and filters from request query, extract filters and create final sql query with
 * appropriate where clauses.
 */
const extractSqlFromTokenRequest = (pgSqlQuery, pgSqlParams, nextParamCount, filters, extraConditions) => {
  // add filters
  let limit = config.maxLimit;
  let order = constants.orderFilterValues.ASC;
  const whereConditions = extraConditions || [];
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

    whereConditions.push(`${filterColumnMap[filter.key]}${filter.operator}$${nextParamCount++}`);
    pgSqlParams.push(filter.value);
  }

  const whereQuery = whereConditions.length !== 0 ? `where ${whereConditions.join(' and ')}` : '';
  const orderQuery = `order by ${sqlQueryColumns.TOKEN_ID} ${order}`;
  const limitQuery = `limit $${nextParamCount++}`;
  pgSqlQuery = [pgSqlQuery, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');
  pgSqlParams.push(limit);

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

const formatTokenInfoRow = (row) => {
  return {
    admin_key: utils.encodeKey(row.key),
    auto_renew_account: EntityId.fromString(row.auto_renew_account_id, true).toString(),
    auto_renew_period: row.auto_renew_period,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    decimals: row.decimals,
    expiry_timestamp: row.exp_time_ns,
    freeze_default: row.freeze_default,
    freeze_key: utils.encodeKey(row.freeze_key),
    initial_supply: row.initial_supply,
    kyc_key: utils.encodeKey(row.kyc_key),
    modified_timestamp: utils.nsToSecNs(row.modified_timestamp),
    name: row.name,
    supply_key: utils.encodeKey(row.supply_key),
    symbol: row.symbol,
    token_id: EntityId.fromString(row.token_id).toString(),
    total_supply: row.total_supply,
    treasury_account_id: EntityId.fromString(row.treasury_account_id).toString(),
    wipe_key: utils.encodeKey(row.wipe_key),
  };
};

const getTokensRequest = async (req, res) => {
  // extract filters from query param
  const filters = utils.buildFilterObject(req.query);

  // validate filters
  await utils.validateAndParseFilters(filters);

  const extraConditions = [];
  const getTokensSqlQuery = [tokensSelectQuery];
  const getTokenSqlParams = [];
  let nextParamCount = 1;

  // if account.id filter is present join on token_account and filter dissociated tokens
  const accountId = req.query[constants.filterKeys.ACCOUNT_ID];
  if (accountId) {
    extraConditions.push('ta.associated is true');
    getTokensSqlQuery.push(accountIdJoinQuery);
    getTokenSqlParams.push(accountId);
    nextParamCount++;
  }

  // add join with entities table to sql query
  getTokensSqlQuery.push(entityIdJoinQuery);

  // build final sql query
  const {query, params, order, limit} = extractSqlFromTokenRequest(
    getTokensSqlQuery.join('\n'),
    getTokenSqlParams,
    nextParamCount,
    filters,
    extraConditions
  );

  const rows = await getTokens(query, params);
  const tokens = rows.map((m) => formatTokenRow(m));

  // populate next link
  const lastTokenId = tokens.length > 0 ? tokens[tokens.length - 1].token_id : null;
  const nextLink = utils.getPaginationLink(
    req,
    tokens.length !== limit,
    constants.filterKeys.TOKEN_ID,
    lastTokenId,
    order
  );

  res.locals[constants.responseDataLabel] = {
    tokens,
    links: {
      next: nextLink,
    },
  };
};

const getTokenInfoRequest = async (req, res) => {
  let tokenId = req.params.id;

  if (!utils.isValidEntityNum(tokenId)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.TOKENID);
  }

  // ensure encoded format is used
  tokenId = EntityId.fromString(tokenId).getEncodedId();

  // concatenate queries to produce final sql query
  const pgSqlQuery = [tokenInfoSelectQuery, entityIdJoinQuery, tokenIdMatchQuery].join('\n');
  const row = await getToken(pgSqlQuery, tokenId);
  res.locals[constants.responseDataLabel] = formatTokenInfoRow(row);
};

const getTokens = async (pgSqlQuery, pgSqlParams) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokens query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const {rows} = await utils.queryQuietly(pgSqlQuery, ...pgSqlParams);
  logger.debug(`getTokens returning ${rows.length} entries`);
  return rows;
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

const tokenBalancesSelectFields = ['tb.consensus_timestamp', 'tb.account_id', 'tb.balance'];
const tokenBalancesSelectQuery = ['select', tokenBalancesSelectFields.join(',\n'), 'from token_balance tb'].join('\n');

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
  let order = constants.orderFilterValues.DESC;
  let joinEntityClause = '';
  const whereConditions = [`${tokenBalancesSqlQueryColumns.TOKEN_ID} = $1`];
  let nextParamCount = 2;
  const pgSqlParams = [tokenId.getEncodedId()];
  const tsQueryWhereConditions = [];

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
        tsQueryWhereConditions.push(`${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} ${op} $${nextParamCount++}`);
        pgSqlParams.push(filter.value);
        break;
      default:
        const columnKey = tokenBalancesFilterColumnMap[filter.key];
        if (!columnKey) {
          break;
        }

        whereConditions.push(`${columnKey} ${filter.operator} $${nextParamCount++}`);
        pgSqlParams.push(filter.value);
        break;
    }
  }

  const tsQueryWhereClause = tsQueryWhereConditions.length !== 0 ? `where ${tsQueryWhereConditions.join(' and ')}` : '';
  const tsQuery = `select ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP}
    from token_balance tb
    ${tsQueryWhereClause}
    order by ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} desc
    limit 1`;
  whereConditions.push(`tb.consensus_timestamp = (${tsQuery})`);

  const whereQuery = `where ${whereConditions.join('\nand ')}`;
  const orderQuery = `order by ${tokenBalancesSqlQueryColumns.ACCOUNT_ID} ${order}`;
  const limitQuery = `limit $${nextParamCount}`;

  const query = [pgSqlQuery, joinEntityClause, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');
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
 */
const getTokenBalances = async (req, res) => {
  let tokenId;
  try {
    tokenId = EntityId.fromString(req.params.id);
  } catch (err) {
    throw InvalidArgumentError.forParams('tokenId');
  }

  const filters = utils.buildFilterObject(req.query);
  await utils.validateAndParseFilters(filters);

  const {query, params, limit, order} = extractSqlFromTokenBalancesRequest(tokenId, tokenBalancesSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenBalances query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await utils.queryQuietly(query, ...params);
  const response = {
    timestamp: rows.length > 0 ? utils.nsToSecNs(rows[0].consensus_timestamp) : null,
    balances: rows.map((row) => formatTokenBalanceRow(row)),
    links: {
      next: null,
    },
  };

  // Pagination links
  const anchorAccountId = response.balances.length > 0 ? response.balances[response.balances.length - 1].account : 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.balances.length !== limit,
    constants.filterKeys.ACCOUNT_ID,
    anchorAccountId,
    order
  );

  logger.debug(`getTokenBalances returning ${response.balances.length} entries`);
  res.locals[constants.responseDataLabel] = response;
};

const getToken = async (pgSqlQuery, tokenId) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenInfo query: ${pgSqlQuery}, params: ${tokenId}`);
  }

  const {rows} = await utils.queryQuietly(pgSqlQuery, tokenId);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug('getToken returning single entry');
  return rows[0];
};

module.exports = {
  getTokenInfoRequest,
  getTokensRequest,
  getTokenBalances,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    extractSqlFromTokenRequest,
    formatTokenRow,
    formatTokenInfoRow,
    tokensSelectQuery,
    accountIdJoinQuery,
    entityIdJoinQuery,
    tokenBalancesSelectQuery,
    extractSqlFromTokenBalancesRequest,
    formatTokenBalanceRow,
  });
}
