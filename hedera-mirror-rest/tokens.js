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
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {NotFoundError} = require('./errors/notFoundError');
const Nft = require('./models/nft');
const NftTransfer = require('./models/nftTransfer');
const Transaction = require('./models/transaction');

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
  'nft.deleted',
  'nft.metadata',
  'nft.modified_timestamp',
  'nft.serial_number',
  'nft.token_id',
];
const nftSelectQuery = ['select', nftSelectFields.join(',\n'), 'from nft'].join('\n');
const entityNftsJoinQuery = 'join entity e on e.id = nft.token_id';

// token discovery sql queries
const tokensSelectQuery = 'select t.token_id, symbol, e.key, t.type from token t';
const accountIdJoinQuery = 'join token_account ta on ta.account_id = $1 and t.token_id = ta.token_id';
const entityIdJoinQuery = 'join entity e on e.id = t.token_id';

// token info sql queries
const tokenInfoSelectFields = [
  'e.auto_renew_account_id',
  'e.auto_renew_period',
  't.created_timestamp',
  'decimals',
  'e.expiration_timestamp',
  'freeze_default',
  'freeze_key',
  'initial_supply',
  'e.key',
  'kyc_key',
  'max_supply',
  't.modified_timestamp',
  'name',
  'supply_key',
  'supply_type',
  'symbol',
  'token_id',
  'total_supply',
  'treasury_account_id',
  't.type',
  'wipe_key',
];
const tokenInfoSelectQuery = ['select', tokenInfoSelectFields.join(',\n'), 'from token t'].join('\n');
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

const formatTokenInfoRow = (row) => {
  return {
    admin_key: utils.encodeKey(row.key),
    auto_renew_account: EntityId.fromEncodedId(row.auto_renew_account_id, true).toString(),
    auto_renew_period: row.auto_renew_period,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    decimals: row.decimals,
    expiry_timestamp: row.expiration_timestamp,
    freeze_default: row.freeze_default,
    freeze_key: utils.encodeKey(row.freeze_key),
    initial_supply: row.initial_supply,
    kyc_key: utils.encodeKey(row.kyc_key),
    max_supply: row.max_supply,
    modified_timestamp: utils.nsToSecNs(row.modified_timestamp),
    name: row.name,
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

const tokenQueryFilterValidityChecks = (param, op, val) => {
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
      ret = false;
  }

  return ret;
};

/**
 * Verify param and filters meet expected format
 * Additionally update format to be persistence query compatible
 * @param filters
 * @returns {{code: number, contents: {_status: {messages: *}}, isValid: boolean}|{code: number, contents: string, isValid: boolean}}
 */
const validateTokensFilters = (filters) => {
  const badParams = [];

  for (const filter of filters) {
    if (!tokenQueryFilterValidityChecks(filter.key, filter.operator, filter.value)) {
      badParams.push(filter.key);
    }
  }

  if (badParams.length > 0) {
    throw InvalidArgumentError.forParams(badParams);
  }
};

const getValidatedFilters = (query) => {
  // extract filters from query param
  const filters = utils.buildFilterObject(query);

  // validate filters, use custom check for tokens until validateAndParseFilters is optimized to handle per resource unique param names
  validateTokensFilters(filters);
  utils.formatFilters(filters);
  return filters;
};

const getTokensRequest = async (req, res) => {
  // extract filters from query param
  const filters = getValidatedFilters(req.query);

  const conditions = [];
  const getTokensSqlQuery = [tokensSelectQuery];
  const getTokenSqlParams = [];

  // if account.id filter is present join on token_account and filter dissociated tokens
  const accountId = req.query[constants.filterKeys.ACCOUNT_ID];
  if (accountId) {
    conditions.push('ta.associated is true');
    getTokensSqlQuery.push(accountIdJoinQuery);
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
  const tokenIdString = req.params.id;
  validateTokenIdParam(tokenIdString);
  return EntityId.fromString(tokenIdString, constants.filterKeys.TOKENID).getEncodedId();
};

const getTokenInfoRequest = async (req, res) => {
  const tokenIdString = req.params.id;
  validateTokenIdParam(tokenIdString);
  const tokenId = getAndValidateTokenIdRequestPathParam(req);

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
  const {opsMap} = utils;

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
          on e.type = ${utils.ENTITY_TYPE_ACCOUNT}
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
        // transform '=' operator for timestamp to '<='
        const op = filter.operator !== opsMap.eq ? filter.operator : opsMap.lte;
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
 * Handler function for /tokens/:id/balances API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getTokenBalances = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
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
  const conditions = [
    `${nftQueryColumns.TOKEN_ID} = $1`,
    `${nftQueryColumns.DELETED} = false and ${sqlQueryColumns.DELETED} != true`,
  ];
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
  query = [query, whereQuery].filter((q) => q !== '').join('\n');

  return utils.buildPgSqlObject(query, params, '', '');
};

const formatNftRow = (row) => {
  return {
    account_id: EntityId.fromEncodedId(row.account_id).toString(),
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    deleted: row.deleted,
    metadata: utils.encodeBase64(row.metadata),
    modified_timestamp: utils.nsToSecNs(row.modified_timestamp),
    serial_number: Number(row.serial_number),
    token_id: EntityId.fromEncodedId(row.token_id).toString(),
  };
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
  const {serialnumber} = req.params;
  validateSerialNumberParam(serialnumber);
  return serialnumber;
};

/**
 * Handler function for /tokens/:id/nfts API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokensRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const filters = getValidatedFilters(req.query);

  const {query, params, limit, order} = extractSqlFromNftTokensRequest(tokenId, nftSelectQuery, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTokens query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await utils.queryQuietly(query, ...params);
  const response = {
    nfts: rows.map((row) => formatNftRow(row)),
    links: {
      next: null,
    },
  };

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
 * Handler function for /tokens/:id/nfts/:serialnumber API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTokenInfoRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialnumber = getAndValidateSerialNumberRequestPathParam(req);

  const {query, params} = extractSqlFromNftTokenInfoRequest(tokenId, serialnumber, nftSelectQuery);
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTokenInfo query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await utils.queryQuietly(query, ...params);
  if (rows.length !== 1) {
    throw new NotFoundError();
  }

  logger.debug(`getNftToken info returning single entry`);
  res.locals[constants.responseDataLabel] = formatNftRow(rows[0]);
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

/**
 * Extracts SQL query, params, order, and limit
 * Combine NFT CREATION and TRANSFERS details fron nft_transfer with DELETION details from nft
 *
 * @param {string} tokenId encoded token ID
 * @param {string} serialNumber nft serial number
 * @param {string} query initial pg SQL query string
 * @return {{query: string, limit: number, params: [], order: 'asc'|'desc'}}
 */
const extractSqlFromNftTransferHistoryRequest = (tokenId, serialNumber, transferQuery, deleteQuery, filters) => {
  let limit = config.maxLimit;
  let order = constants.orderFilterValues.DESC;
  const joinNftTransferClause = `join ${NftTransfer.tableName} ${NftTransfer.tableAlias} on ${Nft.nftQueryColumns.TOKEN_ID} = ${NftTransfer.nftTransferFullNameColumns.TOKEN_ID}
  and ${Nft.nftQueryColumns.SERIAL_NUMBER} = ${NftTransfer.nftTransferFullNameColumns.SERIAL_NUMBER}`;
  const joinTransactionClause = `join ${Transaction.tableName} ${Transaction.tableAlias} on ${NftTransfer.nftTransferFullNameColumns.CONSENSUS_TIMESTAMP} = ${Transaction.transactionQueryColumns.CONSENSUS_NS}`;
  const conditions = [`${Nft.nftQueryColumns.TOKEN_ID} = $1`, `${Nft.nftQueryColumns.SERIAL_NUMBER} = $2`];
  const params = [tokenId, serialNumber];

  const whereQuery = `where ${conditions.join('\nand ')}`;

  // get deleted case, use modifiedTimestamp where deleted = true
  const unionQuery = `union\n${deleteQuery}`;
  const deleteJoinTransactionClause = `join ${Transaction.tableName} ${Transaction.tableAlias} on ${Nft.nftQueryColumns.MODIFIED_TIMESTAMP} = ${Transaction.transactionQueryColumns.CONSENSUS_NS} and ${Nft.nftQueryColumns.DELETED} = true`;
  const deleteWhereCondition = `where ${conditions.join('\nand ')}`;

  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.LIMIT:
        limit = filter.value;
        break;
      case constants.filterKeys.ORDER:
        order = filter.value;
        break;
      default:
        break;
    }
  }

  const orderQuery = `order by ${NftTransfer.nftTransferColumns.CONSENSUS_TIMESTAMP} ${order}`;
  const limitQuery = `limit $${params.push(limit)}`;

  const finalQuery = [
    transferQuery,
    joinNftTransferClause,
    joinTransactionClause,
    whereQuery,
    unionQuery,
    deleteJoinTransactionClause,
    deleteWhereCondition,
    orderQuery,
    limitQuery,
  ]
    .filter((q) => q !== '')
    .join('\n');

  return utils.buildPgSqlObject(finalQuery, params, order, limit);
};

const formatNftHistoryRow = (row) => {
  return {
    consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
    transaction_id: utils.createTransactionId(
      EntityId.fromEncodedId(row.payer_account_id).toString(),
      row.valid_start_ns
    ),
    receiver_account_id: EntityId.fromEncodedId(row.receiver_account_id, true).toString(),
    sender_account_id: EntityId.fromEncodedId(row.sender_account_id, true).toString(),
    type: global.transactionTypes.getName(row.type),
  };
};

const nftTransferHistorySelectFields = [
  NftTransfer.nftTransferFullNameColumns.CONSENSUS_TIMESTAMP,
  Transaction.transactionQueryColumns.PAYER_ACCOUNT_ID,
  Transaction.transactionQueryColumns.VALID_START_NS,
  NftTransfer.nftTransferFullNameColumns.RECEIVER_ACCOUNT_ID,
  NftTransfer.nftTransferFullNameColumns.SENDER_ACCOUNT_ID,
  Transaction.transactionQueryColumns.TYPE,
];
const nftTransferHistorySelectQuery = [
  'select',
  nftTransferHistorySelectFields.join(',\n'),
  `from ${Nft.tableName}`,
].join('\n');

const nftDeleteHistorySelectFields = [
  `${Nft.nftQueryColumns.MODIFIED_TIMESTAMP} as ${NftTransfer.nftTransferColumns.CONSENSUS_TIMESTAMP}`,
  Transaction.transactionQueryColumns.PAYER_ACCOUNT_ID,
  Transaction.transactionQueryColumns.VALID_START_NS,
  `null as ${NftTransfer.nftTransferColumns.RECEIVER_ACCOUNT_ID}`,
  `${Nft.nftQueryColumns.ACCOUNT_ID} as ${NftTransfer.nftTransferColumns.SENDER_ACCOUNT_ID}`,
  Transaction.transactionQueryColumns.TYPE,
];
const nftDeleteHistorySelectQuery = ['select', nftDeleteHistorySelectFields.join(',\n'), `from ${Nft.tableName}`].join(
  '\n'
);

/**
 * Handler function for /api/v1/tokens/{id}/nfts/{serialNumber}/transactions API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 */
const getNftTransferHistoryRequest = async (req, res) => {
  const tokenId = getAndValidateTokenIdRequestPathParam(req);
  const serialnumber = getAndValidateSerialNumberRequestPathParam(req);

  const filters = getValidatedFilters(req.query);

  const {query, params, limit, order} = extractSqlFromNftTransferHistoryRequest(
    tokenId,
    serialnumber,
    nftTransferHistorySelectQuery,
    nftDeleteHistorySelectQuery,
    filters
  );
  if (logger.isTraceEnabled()) {
    logger.trace(`getNftTransferHistory query: ${query} ${JSON.stringify(params)}`);
  }

  const {rows} = await utils.queryQuietly(query, ...params);
  const response = {
    transactions: rows.map((row) => formatNftHistoryRow(row)),
    links: {
      next: null,
    },
  };

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
    accountIdJoinQuery,
    entityIdJoinQuery,
    extractSqlFromNftTransferHistoryRequest,
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
    tokenBalancesSelectQuery,
    tokensSelectQuery,
    validateSerialNumberParam,
    validateTokenIdParam,
    validateTokensFilters,
  });
}
