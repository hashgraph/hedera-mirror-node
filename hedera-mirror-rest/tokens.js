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
import {
  entityTypes,
  filterKeys,
  httpStatusCodes,
  orderFilterValues,
  responseDataLabel,
  tokenTypeFilter,
} from './constants';
import EntityId from './entityId';
import {InvalidArgumentError, NotFoundError} from './errors';
import {CustomFee, Entity, Nft, NftTransfer, Token, Transaction} from './model';
import {NftService} from './service';
import * as utils from './utils';
import {CustomFeeViewModel, NftTransactionHistoryViewModel, NftViewModel} from './viewmodel';

const {default: defaultLimit} = getResponseLimit();

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
  [filterKeys.TOKEN_ID]: sqlQueryColumns.TOKEN_ID,
  [filterKeys.TOKEN_TYPE]: sqlQueryColumns.TYPE,
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
  [filterKeys.ACCOUNT_ID]: nftQueryColumns.ACCOUNT_ID,
};

const nftSelectFields = [
  'nft.account_id',
  'nft.created_timestamp',
  'nft.delegating_spender',
  'nft.deleted or coalesce(e.deleted, false) as deleted',
  'nft.metadata',
  'nft.modified_timestamp',
  'nft.serial_number',
  'nft.spender',
  'nft.token_id',
];
const nftSelectQuery = ['select', nftSelectFields.join(',\n'), 'from nft'].join('\n');
const entityNftsJoinQuery = 'left join entity e on e.id = nft.token_id';

// token discovery sql queries
const tokenAccountCte = `with ta as (
  select *
  from token_account
  where account_id = $1
  order by token_id
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
  let limit = defaultLimit;
  let order = orderFilterValues.ASC;
  conditions = conditions || [];
  for (const filter of filters) {
    if (filter.key === filterKeys.LIMIT) {
      limit = filter.value;
      continue;
    }

    // handle keys that do not require formatting first
    if (filter.key === filterKeys.ORDER) {
      order = filter.value;
      continue;
    }

    // handle token type=ALL, valid param but not present in db
    if (filter.key === filterKeys.TOKEN_TYPE && filter.value === tokenTypeFilter.ALL.toUpperCase()) {
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
    token_id: EntityId.parse(row.token_id).toString(),
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
    auto_renew_account: EntityId.parse(row.auto_renew_account_id, {isNullable: true}).toString(),
    auto_renew_period: row.auto_renew_period,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    custom_fees: createCustomFeesObject(row.custom_fees, row.type),
    decimals: `${row.decimals}`,
    deleted: row.deleted,
    expiry_timestamp: utils.calculateExpiryTimestamp(
      row.auto_renew_period,
      row.created_timestamp,
      row.expiration_timestamp
    ),
    fee_schedule_key: utils.encodeKey(row.fee_schedule_key),
    freeze_default: row.freeze_default,
    freeze_key: utils.encodeKey(row.freeze_key),
    initial_supply: `${row.initial_supply}`,
    kyc_key: utils.encodeKey(row.kyc_key),
    max_supply: `${row.max_supply}`,
    memo: row.memo,
    modified_timestamp: utils.nsToSecNs(row.modified_timestamp),
    name: row.name,
    pause_key: utils.encodeKey(row.pause_key),
    pause_status: row.pause_status,
    supply_key: utils.encodeKey(row.supply_key),
    supply_type: row.supply_type,
    symbol: row.symbol,
    token_id: EntityId.parse(row.token_id).toString(),
    total_supply: `${row.total_supply}`,
    treasury_account_id: EntityId.parse(row.treasury_account_id).toString(),
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
    case filterKeys.ACCOUNT_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case filterKeys.ENTITY_PUBLICKEY:
      // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
      ret = utils.isValidPublicKeyQuery(val);
      break;
    case filterKeys.LIMIT:
      ret = utils.isPositiveLong(val);
      break;
    case filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = utils.isValidValueIgnoreCase(val, Object.values(orderFilterValues));
      break;
    case filterKeys.SERIAL_NUMBER:
      ret = utils.isPositiveLong(val);
      break;
    case filterKeys.TOKEN_ID:
      ret = EntityId.isValidEntityId(val, false);
      break;
    case filterKeys.TOKEN_TYPE:
      ret = utils.isValidValueIgnoreCase(val, Object.values(tokenTypeFilter));
      break;
    case filterKeys.TIMESTAMP:
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
  const filters = utils.buildAndValidateFilters(req.query, acceptedTokenParameters, validateTokenQueryFilter);

  const conditions = [];
  const getTokensSqlQuery = [tokensSelectQuery];
  const getTokenSqlParams = [];

  // if account.id filter is present join on token_account and filter dissociated tokens
  const accountId = req.query[filterKeys.ACCOUNT_ID];
  if (accountId) {
    conditions.push('ta.associated is true');
    getTokensSqlQuery.unshift(tokenAccountCte);
    getTokensSqlQuery.push(tokenAccountJoinQuery);
    getTokenSqlParams.push(EntityId.parse(accountId, {paramName: filterKeys.ACCOUNT_ID}).getEncodedId());
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
    {
      [filterKeys.TOKEN_ID]: lastTokenId,
    },
    order
  );

  res.locals[responseDataLabel] = {
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
  if (!EntityId.isValidEntityId(tokenId, false)) {
    throw InvalidArgumentError.forParams(filterKeys.TOKENID);
  }
};

const getAndValidateTokenIdRequestPathParam = (req) => {
  const tokenIdString = req.params.tokenId;
  validateTokenIdParam(tokenIdString);
  return EntityId.parse(tokenIdString, {paramName: filterKeys.TOKENID}).getEncodedId();
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

  if (param === filterKeys.TIMESTAMP) {
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
                       'all_collectors_are_exempt', ${CustomFee.ALL_COLLECTORS_ARE_EXEMPT},
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
                       )
                     order by ${CustomFee.COLLECTOR_ACCOUNT_ID}, ${CustomFee.DENOMINATING_TOKEN_ID}, ${
    CustomFee.AMOUNT
  }, ${CustomFee.ROYALTY_NUMERATOR})
    from ${CustomFee.tableName} ${CustomFee.tableAlias}
    where ${conditions.join(' and ')}
    group by ${CustomFee.getFullName(CustomFee.CREATED_TIMESTAMP)}
    order by ${CustomFee.getFullName(CustomFee.CREATED_TIMESTAMP)} desc
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
  const filters = utils.buildAndValidateFilters(req.query, acceptedSingleTokenParameters, validateTokenInfoFilter);
  const {query, params} = extractSqlFromTokenInfoRequest(tokenId, filters);

  const row = await getTokenInfo(query, params);

  const tokenInfo = formatTokenInfoRow(row);
  if (!tokenInfo.custom_fees) {
    throw new NotFoundError();
  }

  res.locals[responseDataLabel] = tokenInfo;
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
  [filterKeys.ACCOUNT_BALANCE]: tokenBalancesSqlQueryColumns.ACCOUNT_BALANCE,
  [filterKeys.ACCOUNT_ID]: tokenBalancesSqlQueryColumns.ACCOUNT_ID,
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
  let limit = defaultLimit;
  let order = orderFilterValues.DESC;
  let joinEntityClause = '';
  const conditions = [`${tokenBalancesSqlQueryColumns.TOKEN_ID} = $1`];
  const params = [tokenId];
  const tsQueryConditions = [];

  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.ACCOUNT_PUBLICKEY:
        joinEntityClause = `join entity e
          on e.type = '${entityTypes.ACCOUNT}'
          and e.id = ${tokenBalancesSqlQueryColumns.ACCOUNT_ID}
          and ${tokenBalancesSqlQueryColumns.ACCOUNT_PUBLICKEY} = $${params.push(filter.value)}`;
        break;
      case filterKeys.LIMIT:
        limit = filter.value;
        break;
      case filterKeys.ORDER:
        order = filter.value;
        break;
      case filterKeys.TIMESTAMP:
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
    account: EntityId.parse(row.account_id).toString(),
    balance: row.balance,
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
  const filters = utils.buildAndValidateFilters(req.query, acceptedTokenBalancesParameters);

  const {query, params, limit, order} = extractSqlFromTokenBalancesRequest(tokenId, tokenBalancesSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTokenBalances query: ${query} ${utils.JSONStringify(params)}`);
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
    {
      [filterKeys.ACCOUNT_ID]: anchorAccountId,
    },
    order
  );

  logger.debug(`getTokenBalances returning ${response.balances.length} entries`);
  res.locals[responseDataLabel] = response;
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
  let limit = defaultLimit;
  let order = orderFilterValues.DESC;
  const conditions = [`${nftQueryColumns.TOKEN_ID} = $1`];
  const params = [tokenId];

  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.LIMIT:
        limit = filter.value;
        break;
      case filterKeys.ORDER:
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
  if (!utils.isPositiveLong(serialNumber)) {
    throw InvalidArgumentError.forParams(filterKeys.SERIAL_NUMBER);
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
  const filters = utils.buildAndValidateFilters(req.query, acceptedNftsParameters, validateTokenQueryFilter);

  const {query, params, limit, order} = extractSqlFromNftTokensRequest(tokenId, nftSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTokens query: ${query} ${utils.JSONStringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  const response = {
    nfts: [],
    links: {
      next: null,
    },
  };

  response.nfts = rows.map((row) => {
    const nftModel = new Nft(row);
    return new NftViewModel(nftModel);
  });

  // Pagination links
  const anchorSerialNumber = response.nfts.length > 0 ? response.nfts[response.nfts.length - 1].serial_number : 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.nfts.length !== limit,
    {
      [filterKeys.SERIAL_NUMBER]: anchorSerialNumber,
    },
    order
  );

  logger.debug(`getNftTokens returning ${response.nfts.length} entries`);
  res.locals[responseDataLabel] = response;
};

/**
 * Handler function for /tokens/:tokenId/nfts/:serialNumber API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokenInfoRequest = async (req, res) => {
  utils.validateReq(req);
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialNumber = getAndValidateSerialNumberRequestPathParam(req);

  const {query, params} = extractSqlFromNftTokenInfoRequest(tokenId, serialNumber, nftSelectQuery);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTokenInfo query: ${query} ${utils.JSONStringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug(`getNftToken info returning single entry`);
  const nftModel = new Nft(rows[0]);
  res.locals[responseDataLabel] = new NftViewModel(nftModel);
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

/**
 * Extracts SQL query, params, order, and limit
 * Combine NFT transfers (mint, transfer, wipe account, burn) details from nft_transfer with DELETION details from nft
 *
 * @param {string} tokenId encoded token ID
 * @param {string} serialNumber nft serial number
 * @param {[]} filters parsed and validated filters
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTransferHistoryRequest = (tokenId, serialNumber, filters) => {
  let limit = defaultLimit;
  let order = orderFilterValues.DESC;

  const params = [tokenId, serialNumber];
  // if the nft token class is deleted, the lower of the timestamp_range is the consensus timestamp of the token
  // delete transaction
  const tokenDeleteTimestampQuery = `select lower(${Entity.TIMESTAMP_RANGE})
                                     from ${Entity.tableName}
                                     where ${Entity.ID} = $1
                                       and ${Entity.DELETED} is true`;
  const tokenDeleteConditions = [`${Transaction.CONSENSUS_TIMESTAMP} = (${tokenDeleteTimestampQuery})`];
  const transferConditions = [
    `${NftTransfer.getFullName(NftTransfer.TOKEN_ID)} = $1`,
    `${NftTransfer.getFullName(NftTransfer.SERIAL_NUMBER)} = $2`,
  ];

  // add applicable filters
  const transferTimestampColumn = NftTransfer.getFullName(NftTransfer.CONSENSUS_TIMESTAMP);
  for (const filter of filters) {
    switch (filter.key) {
      case filterKeys.LIMIT:
        limit = filter.value;
        break;
      case filterKeys.ORDER:
        order = filter.value;
        break;
      case filterKeys.TIMESTAMP:
        const paramCount = params.push(filter.value);
        transferConditions.push(`${transferTimestampColumn} ${filter.operator} $${paramCount}`);
        tokenDeleteConditions.push(`${Transaction.CONSENSUS_TIMESTAMP} ${filter.operator} $${paramCount}`);
        break;
      default:
        break;
    }
  }

  // the query planner may not be able to push the limit in the main query to the cte, thus adding the order and limit
  // here to optimize the performance for nfts with a lot of transfers
  params.push(limit);
  const limitQuery = `limit $${params.length}`;
  const serialTransferCte = `serial_transfers as (
    select ${nftTransferHistoryCteSelectFields.join(',\n')}
    from ${NftTransfer.tableName} ${NftTransfer.tableAlias}
    where ${transferConditions.join(' and ')}
    order by ${NftTransfer.CONSENSUS_TIMESTAMP} ${order}
    ${limitQuery}
  )`;

  const joinTransactionClause = `join ${Transaction.tableName} ${Transaction.tableAlias}
    on ${transferTimestampColumn} = ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)}`;
  const tokenTransactionCte = `token_transactions as (
    select ${nftTransferHistorySelectFields.join(',\n')}
    from serial_transfers ${NftTransfer.tableAlias}
    ${joinTransactionClause}
  )`;

  const tokenDeletionCte = `token_deletion as (
    select
      ${Transaction.CONSENSUS_TIMESTAMP},
      ${Transaction.NONCE},
      ${Transaction.PAYER_ACCOUNT_ID},
      ${Transaction.TYPE},
      ${Transaction.VALID_START_NS}
    from ${Transaction.tableName}
    where ${tokenDeleteConditions.join(' and ')}
  )`;

  const query = `with ${serialTransferCte}, ${tokenTransactionCte}, ${tokenDeletionCte}
    select * from token_transactions
    union
    select
      ${Transaction.CONSENSUS_TIMESTAMP},
      null as ${NftTransfer.RECEIVER_ACCOUNT_ID},
      null as ${NftTransfer.SENDER_ACCOUNT_ID},
      null as ${NftTransfer.IS_APPROVAL},
      ${Transaction.NONCE},
      ${Transaction.PAYER_ACCOUNT_ID},
      ${Transaction.TYPE},
      ${Transaction.VALID_START_NS}
    from token_deletion
    order by ${Transaction.CONSENSUS_TIMESTAMP} ${order}
    ${limitQuery}`;

  return utils.buildPgSqlObject(query, params, order, limit);
};

const formatNftHistoryRow = (row) => {
  const nftTransferModel = new NftTransfer(row);
  const transactionModel = new Transaction(row);
  return new NftTransactionHistoryViewModel(nftTransferModel, transactionModel);
};

const nftTransferHistorySelectFields = [
  NftTransfer.getFullName(NftTransfer.CONSENSUS_TIMESTAMP),
  NftTransfer.getFullName(NftTransfer.RECEIVER_ACCOUNT_ID),
  NftTransfer.getFullName(NftTransfer.SENDER_ACCOUNT_ID),
  NftTransfer.getFullName(NftTransfer.IS_APPROVAL),
  Transaction.getFullName(Transaction.NONCE),
  Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID),
  Transaction.getFullName(Transaction.TYPE),
  Transaction.getFullName(Transaction.VALID_START_NS),
];

const nftTransferHistoryCteSelectFields = [
  NftTransfer.CONSENSUS_TIMESTAMP,
  NftTransfer.RECEIVER_ACCOUNT_ID,
  NftTransfer.SENDER_ACCOUNT_ID,
  NftTransfer.TOKEN_ID,
  NftTransfer.IS_APPROVAL,
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

  const filters = utils.buildAndValidateFilters(req.query, acceptedNftTransferHistoryParameters, validateTokenQueryFilter);

  const {query, params, limit, order} = extractSqlFromNftTransferHistoryRequest(tokenId, serialNumber, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTransferHistory query: ${query} ${utils.JSONStringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  const response = {
    transactions: [],
    links: {
      next: null,
    },
  };

  response.transactions = rows.map((row) => formatNftHistoryRow(row));

  // Pagination links
  const anchorTimestamp =
    response.transactions.length > 0 ? response.transactions[response.transactions.length - 1].consensus_timestamp : 0;
  response.links.next = utils.getPaginationLink(
    req,
    response.transactions.length !== limit,
    {
      [filterKeys.TIMESTAMP]: anchorTimestamp,
    },
    order
  );

  logger.debug(`getNftTransferHistory returning ${response.transactions.length} entries`);
  res.locals[responseDataLabel] = response;

  if (response.transactions.length > 0) {
    // check if nft exists
    const nft = await NftService.getNft(tokenId, serialNumber);
    if (nft === null) {
      // set 206 partial response
      res.locals.statusCode = httpStatusCodes.PARTIAL_CONTENT.code;
      logger.debug(`getNftTransferHistory returning partial content`);
    }
  }
};

const tokens = {
  getNftTokenInfoRequest,
  getNftTokensRequest,
  getNftTransferHistoryRequest,
  getTokenBalances,
  getTokenInfoRequest,
  getTokensRequest,
};

const acceptedNftsParameters = new Set([
  filterKeys.ACCOUNT_ID,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.SERIAL_NUMBER
]);

const acceptedNftTransferHistoryParameters = new Set([
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TIMESTAMP
]);

const acceptedTokenParameters = new Set([
  filterKeys.ACCOUNT_ID,
  filterKeys.ENTITY_PUBLICKEY,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TOKEN_ID,
  filterKeys.TOKEN_TYPE
]);

const acceptedSingleTokenParameters = new Set([
  filterKeys.TIMESTAMP
]);

const acceptedTokenBalancesParameters = new Set([
  filterKeys.ACCOUNT_ID,
  filterKeys.ACCOUNT_BALANCE,
  filterKeys.ACCOUNT_PUBLICKEY,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TIMESTAMP
]);

if (utils.isTestEnv()) {
  Object.assign(tokens, {
    entityIdJoinQuery,
    extractSqlFromNftTokenInfoRequest,
    extractSqlFromNftTokensRequest,
    extractSqlFromNftTransferHistoryRequest,
    extractSqlFromTokenBalancesRequest,
    extractSqlFromTokenInfoRequest,
    extractSqlFromTokenRequest,
    formatNftHistoryRow,
    formatTokenBalanceRow,
    formatTokenInfoRow,
    formatTokenRow,
    nftSelectQuery,
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

export default tokens;
