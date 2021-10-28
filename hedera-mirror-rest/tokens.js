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

const _ = require('lodash');

const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');

// errors
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {NotFoundError} = require('./errors/notFoundError');

// models
const {CustomFee, Nft, NftTransfer, Token, Transaction} = require('./model');

// middleware
const {httpStatusCodes} = require('./constants');

// services
const {NftService, TokenService, TransactionResultService, TransactionTypeService} = require('./service');

// view models
const {CustomFeeViewModel, NftViewModel, NftTransactionHistoryViewModel} = require('./viewmodel');

// select columns
const sqlQueryColumns = {
  DELETED: 'e.deleted',
  KEY: 'e.key',
  PUBLIC_KEY: 'e.public_key',
  SYMBOL: 't.symbol',
  TOKEN_ID: 't.token_id',
  TYPE: 't.type',
};

// query to column maps
const filterColumnMap = {
  publickey: sqlQueryColumns.PUBLIC_KEY,
  symbol: sqlQueryColumns.SYMBOL,
  [constants.filterKeys.TOKEN_ID]: sqlQueryColumns.TOKEN_ID,
  [constants.filterKeys.TOKEN_TYPE]: sqlQueryColumns.TYPE,
};

const nftQueryColumns = {
  ACCOUNT_ID: 'nft.account_id',
  CREATED_TIMESTAMP: 'nft.created_timestamp',
  DELETED: 'nft.deleted',
  SERIAL_NUMBER: 'nft.serial_number',
  TOKEN_ID: 'nft.token_id',
};

// query to column maps
const nftFilterColumnMap = {
  serialnumber: nftQueryColumns.SERIAL_NUMBER,
  [constants.filterKeys.ACCOUNT_ID]: nftQueryColumns.ACCOUNT_ID,
};

const nftSelectFields = [
  'nft.account_id',
  'nft.created_timestamp',
  'nft.deleted or e.deleted as deleted',
  'nft.metadata',
  'nft.modified_timestamp',
  'nft.serial_number',
  'nft.token_id',
];
const nftSelectQuery = ['select', nftSelectFields.join(',\n'), 'from nft'].join('\n');
const entityNftsJoinQuery = 'join entity e on e.id = nft.token_id';

// token discovery sql queries
const tokenAccountCte = `with ta as (
  select distinct on (account_id, token_id) *
  from token_account
  where account_id = $1
  order by account_id, token_id, modified_timestamp desc
)`;
const tokensSelectQuery = 'select t.token_id, symbol, e.key, t.type from token t';
const entityIdJoinQuery = 'join entity e on e.id = t.token_id';
const tokenAccountJoinQuery = 'join ta on ta.token_id = t.token_id';

// token info sql queries
const tokenInfoSelectFields = [
  'e.auto_renew_account_id',
  'e.auto_renew_period',
  't.created_timestamp',
  'decimals',
  'e.deleted',
  'e.expiration_timestamp',
  'fee_schedule_key',
  'freeze_default',
  'freeze_key',
  'initial_supply',
  'e.key',
  'kyc_key',
  'max_supply',
  'e.memo',
  't.modified_timestamp',
  'name',
  'pause_key',
  'pause_status',
  'supply_key',
  'supply_type',
  'symbol',
  'token_id',
  'total_supply',
  'treasury_account_id',
  't.type',
  'wipe_key',
];
const tokenIdMatchQuery = 'where token_id = $1';

/**
 * Given top level select columns and filters from request query, extract filters and create final sql query with
 * appropriate where clauses.
 */
const extractSqlFromTokenRequest = (query, params, filters, conditions) => {
  // add filters
  let limit = config.maxLimit;
  let order = constants.orderFilterValues.ASC;
  conditions = conditions || [];
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

    // handle token type=ALL, valid param but not present in db
    if (
      filter.key === constants.filterKeys.TOKEN_TYPE &&
      filter.value === constants.tokenTypeFilter.ALL.toUpperCase()
    ) {
      continue;
    }

    const columnKey = filterColumnMap[filter.key];
    if (columnKey === undefined) {
      continue;
    }

    conditions.push(`${filterColumnMap[filter.key]}${filter.operator}$${params.push(filter.value)}`);
  }

  const whereQuery = conditions.length !== 0 ? `where ${conditions.join(' and ')}` : '';
  const orderQuery = `order by ${sqlQueryColumns.TOKEN_ID} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;
  query = [query, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, order, limit);
};

/**
 * Format row in postgres query's result to object which is directly returned to user as json.
 */
const formatTokenRow = (row) => {
  return {
    admin_key: utils.encodeKey(row.key),
    symbol: row.symbol,
    token_id: EntityId.fromEncodedId(row.token_id).toString(),
    type: row.type,
  };
};

/**
 * Creates custom fees object from an array of aggregated json objects
 *
 * @param customFees
 * @param tokenType
 * @return {{}|*}
 */
const createCustomFeesObject = (customFees, tokenType) => {
  if (!customFees) {
    return null;
  }

  const nonFixedFeesField = tokenType === Token.TYPE.FUNGIBLE_COMMON ? 'fractional_fees' : 'royalty_fees';
  const result = {
    created_timestamp: utils.nsToSecNs(customFees[0].created_timestamp),
    fixed_fees: [],
    [nonFixedFeesField]: [],
  };

  return customFees.reduce((customFeesObject, customFee) => {
    const model = new CustomFee(customFee);
    const viewModel = new CustomFeeViewModel(model);

    if (viewModel.hasFee()) {
      const fees = viewModel.isFixedFee() ? customFeesObject.fixed_fees : customFeesObject[nonFixedFeesField];
      fees.push(viewModel);
    }

    return customFeesObject;
  }, result);
};

const formatTokenInfoRow = (row) => {
  return {
    admin_key: utils.encodeKey(row.key),
    auto_renew_account: EntityId.fromEncodedId(row.auto_renew_account_id, true).toString(),
    auto_renew_period: row.auto_renew_period,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    custom_fees: createCustomFeesObject(row.custom_fees, row.type),
    decimals: row.decimals,
    deleted: row.deleted,
    expiry_timestamp: row.expiration_timestamp,
    fee_schedule_key: utils.encodeKey(row.fee_schedule_key),
    freeze_default: row.freeze_default,
    freeze_key: utils.encodeKey(row.freeze_key),
    initial_supply: row.initial_supply,
    kyc_key: utils.encodeKey(row.kyc_key),
    max_supply: row.max_supply,
    memo: row.memo,
    modified_timestamp: utils.nsToSecNs(row.modified_timestamp),
    name: row.name,
    pause_key: utils.encodeKey(row.pause_key),
    pause_status: row.pause_status,
    supply_key: utils.encodeKey(row.supply_key),
    supply_type: row.supply_type,
    symbol: row.symbol,
    token_id: EntityId.fromEncodedId(row.token_id).toString(),
    total_supply: row.total_supply,
    treasury_account_id: EntityId.fromEncodedId(row.treasury_account_id).toString(),
    type: row.type,
    wipe_key: utils.encodeKey(row.wipe_key),
  };
};

const validateTokenQueryFilter = (param, op, val) => {
  let ret = false;

  if (op === undefined || val === undefined) {
    return ret;
  }

  // Validate operator
  if (!utils.isValidOperatorQuery(op)) {
    return ret;
  }

  // Validate the value
  switch (param) {
    case constants.filterKeys.ACCOUNT_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.ENTITY_PUBLICKEY:
      // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
      ret = utils.isValidPublicKeyQuery(val);
      break;
    case constants.filterKeys.LIMIT:
      // Acceptable forms: upto 4 digits
      ret = utils.isValidLimitNum(val);
      break;
    case constants.filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = utils.isValidValueIgnoreCase(val, Object.values(constants.orderFilterValues));
      break;
    case constants.filterKeys.SERIAL_NUMBER:
      ret = utils.isValidNum(val);
      break;
    case constants.filterKeys.TOKEN_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.TOKEN_TYPE:
      ret = utils.isValidValueIgnoreCase(val, Object.values(constants.tokenTypeFilter));
      break;
    case constants.filterKeys.TIMESTAMP:
      ret = utils.isValidTimestampParam(val);
      break;
    default:
      // Every token parameter should be included here. Otherwise, it will not be accepted.
      break;
  }

  return ret;
};

const getTokensRequest = async (req, res) => {
  // validate filters, use custom check for tokens until validateAndParseFilters is optimized to handle
  // per resource unique param names
  const filters = utils.buildAndValidateFilters(req.query, validateTokenQueryFilter);

  const conditions = [];
  const getTokensSqlQuery = [tokensSelectQuery];
  const getTokenSqlParams = [];

  // if account.id filter is present join on token_account and filter dissociated tokens
  const accountId = req.query[constants.filterKeys.ACCOUNT_ID];
  if (accountId) {
    conditions.push('ta.associated is true');
    getTokensSqlQuery.unshift(tokenAccountCte);
    getTokensSqlQuery.push(tokenAccountJoinQuery);
    getTokenSqlParams.push(EntityId.fromString(accountId, constants.filterKeys.ACCOUNT_ID).getEncodedId());
  }

  // add join with entities table to sql query
  getTokensSqlQuery.push(entityIdJoinQuery);

  // build final sql query
  const {query, params, order, limit} = extractSqlFromTokenRequest(
    getTokensSqlQuery.join('\n'),
    getTokenSqlParams,
    filters,
    conditions
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

/**
 * Verify tokenId meets entity id format
 */
const validateTokenIdParam = (tokenId) => {
  if (!EntityId.isValidEntityId(tokenId)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.TOKENID);
  }
};

const getAndValidateTokenIdRequestPathParam = (req) => {
  const tokenIdString = req.params.tokenId;
  validateTokenIdParam(tokenIdString);
  return EntityId.fromString(tokenIdString, constants.filterKeys.TOKENID).getEncodedId();
};

/**
 * Validates the token info timestamp filter operator.
 *
 * @param {string} op
 * @return {boolean}
 */
const isValidTokenInfoTimestampFilterOp = (op) => {
  return op === 'eq' || op === 'lt' || op === 'lte';
};

/**
 * Validates token info request filters. Only timestamp filter is supported.
 *
 * @param param
 * @param op
 * @param val
 * @return {boolean}
 */
const validateTokenInfoFilter = (param, op, val) => {
  let ret = false;

  if (op === undefined || val === undefined) {
    return ret;
  }

  if (param === constants.filterKeys.TIMESTAMP) {
    ret = isValidTokenInfoTimestampFilterOp(op) && utils.isValidTimestampParam(val);
  }

  return ret;
};

/**
 * Transforms the timestamp filter operator: '=' maps to '<=', others stay the same
 *
 * @param {string} op
 */
const transformTimestampFilterOp = (op) => {
  const {opsMap} = utils;
  return op === opsMap.eq ? opsMap.lte : op;
};

/**
 * Gets the token info query
 *
 * @param {string} tokenId
 * @param {[]} filters
 * @return {{query: string, params: []}} the query string and params
 */
const extractSqlFromTokenInfoRequest = (tokenId, filters) => {
  const conditions = [`${CustomFee.TOKEN_ID} = $1`];
  const params = [tokenId];

  if (filters && filters.length !== 0) {
    // honor the last timestamp filter
    const filter = filters[filters.length - 1];
    const op = transformTimestampFilterOp(filter.operator);
    conditions.push(`${CustomFee.FILTER_MAP[filter.key]} ${op} $2`);
    params.push(filter.value);
  }

  const aggregateCustomFeeQuery = `
    select jsonb_agg(jsonb_build_object(
        'amount', ${CustomFee.AMOUNT},
        'amount_denominator', ${CustomFee.AMOUNT_DENOMINATOR},
        'collector_account_id', ${CustomFee.COLLECTOR_ACCOUNT_ID}::text,
        'created_timestamp', ${CustomFee.CREATED_TIMESTAMP}::text,
        'denominating_token_id', ${CustomFee.DENOMINATING_TOKEN_ID}::text,
        'maximum_amount', ${CustomFee.MAXIMUM_AMOUNT},
        'minimum_amount', ${CustomFee.MINIMUM_AMOUNT},
        'net_of_transfers', ${CustomFee.NET_OF_TRANSFERS},
        'royalty_denominator', ${CustomFee.ROYALTY_DENOMINATOR},
        'royalty_numerator', ${CustomFee.ROYALTY_NUMERATOR},
        'token_id', ${CustomFee.TOKEN_ID}::text
    ) order by ${CustomFee.COLLECTOR_ACCOUNT_ID}, ${CustomFee.DENOMINATING_TOKEN_ID}, ${CustomFee.AMOUNT}, ${
    CustomFee.ROYALTY_NUMERATOR
  })
    from ${CustomFee.tableName} ${CustomFee.tableAlias}
    where ${conditions.join(' and ')}
    group by ${CustomFee.CREATED_TIMESTAMP_FULL_NAME}
    order by ${CustomFee.CREATED_TIMESTAMP_FULL_NAME} desc
    limit 1
  `;

  const query = [
    'select',
    [...tokenInfoSelectFields, `(${aggregateCustomFeeQuery}) as custom_fees`].join(',\n'),
    'from token t',
    entityIdJoinQuery,
    tokenIdMatchQuery,
  ].join('\n');

  return {
    query,
    params,
  };
};

const getTokenInfoRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);

  // extract and validate filters from query param
  const filters = utils.buildAndValidateFilters(req.query, validateTokenInfoFilter);
  const {query, params} = extractSqlFromTokenInfoRequest(tokenId, filters);

  const row = await getTokenInfo(query, params);

  const tokenInfo = formatTokenInfoRow(row);
  if (!tokenInfo.custom_fees) {
    throw new NotFoundError();
  }

  res.locals[constants.responseDataLabel] = tokenInfo;
};

const getTokens = async (pgSqlQuery, pgSqlParams) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokens query: ${pgSqlQuery}, params: ${pgSqlParams}`);
  }

  const {rows} = await pool.queryQuietly(pgSqlQuery, pgSqlParams);
  logger.debug(`getTokens returning ${rows.length} entries`);
  return rows;
};

// token balances select columns
const tokenBalancesSqlQueryColumns = {
  ACCOUNT_BALANCE: 'tb.balance',
  ACCOUNT_ID: 'tb.account_id',
  CONSENSUS_TIMESTAMP: 'tb.consensus_timestamp',
  ACCOUNT_PUBLICKEY: 'e.public_key',
  TOKEN_ID: 'tb.token_id',
};

// token balances query to column maps
const tokenBalancesFilterColumnMap = {
  [constants.filterKeys.ACCOUNT_BALANCE]: tokenBalancesSqlQueryColumns.ACCOUNT_BALANCE,
  [constants.filterKeys.ACCOUNT_ID]: tokenBalancesSqlQueryColumns.ACCOUNT_ID,
};

const tokenBalancesSelectFields = ['tb.consensus_timestamp', 'tb.account_id', 'tb.balance'];
const tokenBalancesSelectQuery = ['select', tokenBalancesSelectFields.join(',\n'), 'from token_balance tb'].join('\n');

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {string} tokenId encoded token ID
 * @param {string} query initial pg SQL query string
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromTokenBalancesRequest = (tokenId, query, filters) => {
  let limit = config.maxLimit;
  let order = constants.orderFilterValues.DESC;
  let joinEntityClause = '';
  const conditions = [`${tokenBalancesSqlQueryColumns.TOKEN_ID} = $1`];
  const params = [tokenId];
  const tsQueryConditions = [];

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.ACCOUNT_PUBLICKEY:
        joinEntityClause = `join entity e
          on e.type = '${constants.entityTypes.ACCOUNT}'
          and e.id = ${tokenBalancesSqlQueryColumns.ACCOUNT_ID}
          and ${tokenBalancesSqlQueryColumns.ACCOUNT_PUBLICKEY} = $${params.push(filter.value)}`;
        break;
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      case constants.filterKeys.TIMESTAMP:
        const op = transformTimestampFilterOp(filter.operator);
        params.push(filter.value);
        tsQueryConditions.push(`${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} ${op} $${params.length}`);
        break;
      default:
        const columnKey = tokenBalancesFilterColumnMap[filter.key];
        if (!columnKey) {
          break;
        }

        conditions.push(`${columnKey} ${filter.operator} $${params.push(filter.value)}`);
        break;
    }
  }

  const tsQueryWhereClause = tsQueryConditions.length !== 0 ? `where ${tsQueryConditions.join(' and ')}` : '';
  const tsQuery = `select ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP}
                   from token_balance tb
                     ${tsQueryWhereClause}
                   order by ${tokenBalancesSqlQueryColumns.CONSENSUS_TIMESTAMP} desc
                   limit 1`;
  conditions.push(`tb.consensus_timestamp = (${tsQuery})`);

  const whereQuery = `where ${conditions.join('\nand ')}`;
  const orderQuery = `order by ${tokenBalancesSqlQueryColumns.ACCOUNT_ID} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;
  query = [query, joinEntityClause, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, order, limit);
};

const formatTokenBalanceRow = (row) => {
  return {
    account: EntityId.fromEncodedId(row.account_id).toString(),
    balance: Number(row.balance),
  };
};

/**
 * Handler function for /tokens/:tokenId/balances API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getTokenBalances = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const filters = utils.buildAndValidateFilters(req.query);

  const {query, params, limit, order} = extractSqlFromTokenBalancesRequest(tokenId, tokenBalancesSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenBalances query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
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

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {string} tokenId encoded token ID
 * @param {string} query initial pg SQL query string
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTokensRequest = (tokenId, query, filters) => {
  let limit = config.maxLimit;
  let order = constants.orderFilterValues.DESC;
  const conditions = [`${nftQueryColumns.TOKEN_ID} = $1`];
  const params = [tokenId];

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      default:
        const columnKey = nftFilterColumnMap[filter.key];
        if (!columnKey) {
          break;
        }

        conditions.push(`${columnKey} ${filter.operator} $${params.push(filter.value)}`);
        break;
    }
  }

  const whereQuery = `where ${conditions.join('\nand ')}`;
  const orderQuery = `order by ${nftQueryColumns.SERIAL_NUMBER} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;
  query = [query, entityNftsJoinQuery, whereQuery, orderQuery, limitQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, order, limit);
};

/**
 * Extracts SQL query, params, order, and limit
 *
 * @param {string} tokenId encoded token ID
 * @param {string} serialNumber nft serial number
 * @param {string} query initial pg SQL query string
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTokenInfoRequest = (tokenId, serialNumber, query) => {
  // filter for token and serialNumber
  const conditions = [`${nftQueryColumns.TOKEN_ID} = $1`, `${nftQueryColumns.SERIAL_NUMBER} = $2`];
  const params = [tokenId, serialNumber];

  const whereQuery = `where ${conditions.join('\nand ')}`;
  query = [query, entityNftsJoinQuery, whereQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, '', '');
};

/**
 * Verify serialnumber meets expected integer format and range
 */
const validateSerialNumberParam = (serialNumber) => {
  if (!utils.isValidNum(serialNumber)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.SERIAL_NUMBER);
  }
};

const getAndValidateSerialNumberRequestPathParam = (req) => {
  const {serialNumber} = req.params;
  validateSerialNumberParam(serialNumber);
  return serialNumber;
};

/**
 * Handler function for /tokens/:tokenId/nfts API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokensRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const filters = utils.buildAndValidateFilters(req.query, validateTokenQueryFilter);

  // verify token exists
  const token = await TokenService.getToken(tokenId);
  if (token === null) {
    throw new NotFoundError(`No such token id - ${req.params.tokenId}`);
  }

  const {query, params, limit, order} = extractSqlFromNftTokensRequest(tokenId, nftSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTokens query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  const response = {
    nfts: [],
    links: {
      next: null,
    },
  };

  // handle filter case with no returns
  if (_.isEmpty(rows) && !_.isEmpty(filters)) {
    res.locals.statusCode = httpStatusCodes.NO_CONTENT.code;
    res.locals[constants.responseDataLabel] = response;
    logger.debug(`getNftTokens returning no content`);
    return;
  }

  response.nfts = rows.map((row) => {
    const nftModel = new Nft(row);
    return new NftViewModel(nftModel);
  });

  // Pagination links
  const anchorSerialNumber = response.nfts.length > 0 ? response.nfts[response.nfts.length - 1].serial_number : 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.nfts.length !== limit,
    constants.filterKeys.SERIAL_NUMBER,
    anchorSerialNumber,
    order
  );

  logger.debug(`getNftTokens returning ${response.nfts.length} entries`);
  res.locals[constants.responseDataLabel] = response;
};

/**
 * Handler function for /tokens/:tokenId/nfts/:serialNumber API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokenInfoRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialNumber = getAndValidateSerialNumberRequestPathParam(req);

  // verify token exists
  const token = await TokenService.getToken(tokenId);
  if (token === null) {
    throw new NotFoundError(`No such token id - ${req.params.tokenId}`);
  }

  const {query, params} = extractSqlFromNftTokenInfoRequest(tokenId, serialNumber, nftSelectQuery);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTokenInfo query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug(`getNftToken info returning single entry`);
  const nftModel = new Nft(rows[0]);
  res.locals[constants.responseDataLabel] = new NftViewModel(nftModel);
};

const getTokenInfo = async (query, params) => {
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenInfo query: ${query}, params: ${params}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug('getToken returning single entry');
  return rows[0];
};

const tokenDeleteTransactionType = 'TOKENDELETION';
const successTransactionResult = 'SUCCESS';

/**
 * Extracts SQL query, params, order, and limit
 * Combine NFT CREATION and TRANSFERS details fron nft_transfer with DELETION details from nft
 *
 * @param {string} tokenId encoded token ID
 * @param {string} serialNumber nft serial number
 * @param {string} transferQuery pg SQL query for transfers
 * @param {string} deleteQuery pg SQL query for deletion transaction
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTransferHistoryRequest = (tokenId, serialNumber, transferQuery, deleteQuery, filters) => {
  let limit = config.maxLimit;
  let order = constants.orderFilterValues.DESC;

  const params = [tokenId, serialNumber];
  const transferConditions = [`${NftTransfer.TOKEN_ID_FULL_NAME} = $1`, `${NftTransfer.SERIAL_NUMBER_FULL_NAME} = $2`];

  const deleteConditions = [
    `${Transaction.ENTITY_ID_FULL_NAME} = $1`,
    `${Transaction.TYPE_FULL_NAME} = ${TransactionTypeService.getProtoId(tokenDeleteTransactionType)}`,
    `${Transaction.RESULT_FULL_NAME} = ${TransactionResultService.getProtoId(successTransactionResult)}`,
  ];

  // add applicable filters
  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      default:
        const transferColumnKey = NftTransfer.FILTER_MAP[filter.key];
        const transactionColumnKey = Transaction.FILTER_MAP[filter.key];

        if (filter.key === constants.filterKeys.TIMESTAMP) {
          if (!transferColumnKey || !transactionColumnKey) {
            break;
          }

          const paramCount = params.push(filter.value);
          transferConditions.push(`${transferColumnKey} ${filter.operator} $${paramCount}`);
          deleteConditions.push(`${transactionColumnKey} ${filter.operator} $${paramCount}`);
        }

        break;
    }
  }

  const joinTransactionClause = `join ${Transaction.tableName} ${Transaction.tableAlias}
    on ${NftTransfer.CONSENSUS_TIMESTAMP_FULL_NAME} = ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME}`;

  const transferWhereQuery = `where ${transferConditions.join('\nand ')}`;

  const serialTransferCte = `serial_transfers as (
    select ${nftTransferHistoryCteSelectFields.join(',\n')}
    from ${NftTransfer.tableName} ${NftTransfer.tableAlias}
    ${transferWhereQuery}
  )`;

  const tokenTransactionCte = `token_transactions as (
    select ${nftTransferHistorySelectFields.join(',\n')}
    from serial_transfers ${NftTransfer.tableAlias}
    ${joinTransactionClause} and ${NftTransfer.TOKEN_ID_FULL_NAME} = ${Transaction.ENTITY_ID_FULL_NAME}
  )`;

  const tokenTransferCte = `token_transfers as (
    select ${nftTransferHistorySelectFields.join(',\n')}
    from serial_transfers ${NftTransfer.tableAlias}
    ${joinTransactionClause} and ${Transaction.ENTITY_ID_FULL_NAME} is null
  )`;

  const cteQuery = `with ${serialTransferCte}, ${tokenTransactionCte}, ${tokenTransferCte}
  select * from token_transactions
  union
  select * from token_transfers`;

  const unionQuery = `union\n${deleteQuery}`;

  const deleteWhereCondition = `where ${deleteConditions.join('\nand ')}`;

  const orderQuery = `order by ${NftTransfer.CONSENSUS_TIMESTAMP} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;

  const finalQuery = [cteQuery, unionQuery, deleteWhereCondition, orderQuery, limitQuery]
    .filter((q) => q !== '')
    .join('\n');

  return utils.buildPgSqlObject(finalQuery, params, order, limit);
};

const formatNftHistoryRow = (row) => {
  const nftTransferModel = new NftTransfer(row);
  const transactionModel = new Transaction(row);
  return new NftTransactionHistoryViewModel(nftTransferModel, transactionModel);
};

const nftTransferHistorySelectFields = [
  NftTransfer.CONSENSUS_TIMESTAMP_FULL_NAME,
  Transaction.PAYER_ACCOUNT_ID_FULL_NAME,
  Transaction.VALID_START_NS_FULL_NAME,
  NftTransfer.RECEIVER_ACCOUNT_ID_FULL_NAME,
  NftTransfer.SENDER_ACCOUNT_ID_FULL_NAME,
  Transaction.TYPE_FULL_NAME,
];
const nftTransferHistorySelectQuery = [
  'select',
  nftTransferHistorySelectFields.join(',\n'),
  `from ${NftTransfer.tableName} ${NftTransfer.tableAlias}`,
].join('\n');

const nftDeleteHistorySelectFields = [
  `${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} as ${NftTransfer.CONSENSUS_TIMESTAMP}`,
  Transaction.PAYER_ACCOUNT_ID_FULL_NAME,
  Transaction.VALID_START_NS_FULL_NAME,
  `null as ${NftTransfer.RECEIVER_ACCOUNT_ID}`,
  `null as ${NftTransfer.SENDER_ACCOUNT_ID}`,
  Transaction.TYPE_FULL_NAME,
];
const nftDeleteHistorySelectQuery = [
  'select',
  nftDeleteHistorySelectFields.join(',\n'),
  `from ${Transaction.tableName} ${Transaction.tableAlias}`,
].join('\n');

const nftTransferHistoryCteSelectFields = [
  NftTransfer.CONSENSUS_TIMESTAMP_FULL_NAME,
  NftTransfer.RECEIVER_ACCOUNT_ID_FULL_NAME,
  NftTransfer.SENDER_ACCOUNT_ID_FULL_NAME,
  NftTransfer.TOKEN_ID_FULL_NAME,
];

/**
 * Handler function for /api/v1/tokens/{tokenId}/nfts/{serialNumber}/transactions API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTransferHistoryRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialNumber = getAndValidateSerialNumberRequestPathParam(req);

  const filters = utils.buildAndValidateFilters(req.query, validateTokenQueryFilter);

  const {query, params, limit, order} = extractSqlFromNftTransferHistoryRequest(
    tokenId,
    serialNumber,
    nftTransferHistorySelectQuery,
    nftDeleteHistorySelectQuery,
    filters
  );
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTransferHistory query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  const response = {
    transactions: [],
    links: {
      next: null,
    },
  };

  if (_.isEmpty(rows)) {
    if (_.isEmpty(filters)) {
      throw new NotFoundError(); // 404 if no transactions are present
    } else {
      // 204 if no transactions are present but filters were applied
      res.locals.statusCode = httpStatusCodes.NO_CONTENT.code;
      res.locals[constants.responseDataLabel] = response;
      logger.debug(`getNftTransferHistory returning no content`);
      return;
    }
  }

  response.transactions = rows.map((row) => formatNftHistoryRow(row));

  // Pagination links
  const anchorTimestamp =
    response.transactions.length > 0 ? response.transactions[response.transactions.length - 1].consensus_timestamp : 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.transactions.length !== limit,
    constants.filterKeys.TIMESTAMP,
    anchorTimestamp,
    order
  );

  logger.debug(`getNftTransferHistory returning ${response.transactions.length} entries`);
  res.locals[constants.responseDataLabel] = response;

  // check if nft exists
  const nft = await NftService.getNft(tokenId, serialNumber);
  if (nft === null) {
    // set 206 partial response
    res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
    logger.debug(`getNftTransferHistory returning partial content`);
  }
};

module.exports = {
  getNftTokenInfoRequest,
  getNftTokensRequest,
  getNftTransferHistoryRequest,
  getTokenInfoRequest,
  getTokensRequest,
  getTokenBalances,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    entityIdJoinQuery,
    extractSqlFromNftTransferHistoryRequest,
    extractSqlFromTokenInfoRequest,
    extractSqlFromTokenRequest,
    extractSqlFromNftTokenInfoRequest,
    extractSqlFromNftTokensRequest,
    extractSqlFromTokenBalancesRequest,
    formatTokenBalanceRow,
    formatTokenInfoRow,
    formatTokenRow,
    nftSelectQuery,
    nftDeleteHistorySelectQuery,
    nftTransferHistorySelectQuery,
    tokenAccountCte,
    tokenAccountJoinQuery,
    tokenBalancesSelectQuery,
    tokensSelectQuery,
    validateSerialNumberParam,
    validateTokenIdParam,
    validateTokenInfoFilter,
    validateTokenQueryFilter,
  });
}
