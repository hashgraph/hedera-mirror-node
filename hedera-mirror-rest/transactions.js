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

const utils = require('./utils');
const constants = require('./constants');
const EntityId = require('./entityId');
const TransactionId = require('./transactionId');
const {NotFoundError} = require('./errors/notFoundError');
const NftTransferModel = require('./models/nftTransferModel');
const NftTransferViewModel = require('./viewmodels/nftTransferViewModel');

/**
 * Gets the select clause with crypto transfers, token transfers, and nft transfers
 *
 * @param {boolean} includeNftTransferList - include the nft transfer list or not
 * @return {string}
 */
const getSelectClauseWithTransfers = (includeNftTransferList) => {
  // aggregate crypto transfers, token transfers, and nft transfers
  const aggregateCryptoTransferQuery = `
    select jsonb_agg(jsonb_build_object(
        'amount', amount,
        'entity_id', entity_id
      ) order by entity_id, amount
    )
    from crypto_transfer
    where crypto_transfer.consensus_timestamp = t.consensus_ns
  `;
  const aggregateTokenTransferQuery = `
    select jsonb_agg(jsonb_build_object(
        'account_id', account_id,
        'amount', amount,
        'token_id', token_id
      ))
    from token_transfer
    where token_transfer.consensus_timestamp = t.consensus_ns
  `;
  const aggregateNftTransferQuery = `
    select jsonb_agg(jsonb_build_object(
      'receiver_account_id', receiver_account_id,
      'sender_account_id', sender_account_id,
      'serial_number', serial_number,
      'token_id', token_id
      ))
    from nft_transfer
    where nft_transfer.consensus_timestamp = t.consensus_ns
  `;
  const fields = [
    't.payer_account_id',
    't.memo',
    't.consensus_ns',
    't.valid_start_ns',
    `coalesce(ttr.result, 'UNKNOWN') AS result`,
    `coalesce(ttt.name, 'UNKNOWN') AS name`,
    't.node_account_id',
    't.charged_tx_fee',
    't.valid_duration_seconds',
    't.max_fee',
    't.transaction_hash',
    't.scheduled',
    't.entity_id',
    't.transaction_bytes',
    `(${aggregateCryptoTransferQuery}) AS crypto_transfer_list`,
    `(${aggregateTokenTransferQuery}) AS token_transfer_list`,
  ];

  if (includeNftTransferList) {
    fields.push(`(${aggregateNftTransferQuery}) AS nft_transfer_list`);
  }

  return `SELECT
    ${fields.join(',\n')}
  `;
};

/**
 * Creates crypto transfer list from aggregated array of JSON objects in the query result. Note if the
 * cryptoTransferList is undefined, an empty array is returned.
 *
 * @param cryptoTransferList crypto transfer list
 * @return {{account: string, amount: Number}[]}
 */
const createCryptoTransferList = (cryptoTransferList) => {
  if (!cryptoTransferList) {
    return [];
  }

  return cryptoTransferList.map((transfer) => {
    const {entity_id: accountId, amount} = transfer;
    return {
      account: EntityId.fromEncodedId(accountId).toString(),
      amount,
    };
  });
};

/**
 * Creates token transfer list from aggregated array of JSON objects in the query result
 *
 * @param tokenTransferList token transfer list
 * @return {undefined|{amount: Number, account: string, token_id: string}[]}
 */
const createTokenTransferList = (tokenTransferList) => {
  if (!tokenTransferList) {
    return undefined;
  }

  return tokenTransferList.map((transfer) => {
    const {token_id: tokenId, account_id: accountId, amount} = transfer;
    return {
      token_id: EntityId.fromEncodedId(tokenId).toString(),
      account: EntityId.fromEncodedId(accountId).toString(),
      amount,
    };
  });
};

/**
 * Creates an nft transfer list from aggregated array of JSON objects in the query result
 *
 * @param nftTransferList nft transfer list
 * @return {undefined|{receiver_account_id: string, sender_account_id: string, serial_number: Number, token_id: string}[]}
 */
const createNftTransferList = (nftTransferList) => {
  if (!nftTransferList) {
    return undefined;
  }

  return nftTransferList.map((transfer) => {
    return NftTransferViewModel.fromDb(transfer);
  });
};

/**
 * Create transferlists from the output of SQL queries.
 *
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @return {{anchorSecNs: (String|number), transactions: {}}}
 */
const createTransferLists = (rows) => {
  const transactions = rows.map((row) => {
    const validStartTimestamp = row.valid_start_ns;
    return {
      charged_tx_fee: Number(row.charged_tx_fee),
      consensus_timestamp: utils.nsToSecNs(row.consensus_ns),
      entity_id: EntityId.fromEncodedId(row.entity_id, true).toString(),
      max_fee: utils.getNullableNumber(row.max_fee),
      memo_base64: utils.encodeBase64(row.memo),
      name: row.name,
      nft_transfers: createNftTransferList(row.nft_transfer_list),
      node: EntityId.fromEncodedId(row.node_account_id, true).toString(),
      result: row.result,
      scheduled: row.scheduled,
      token_transfers: createTokenTransferList(row.token_transfer_list),
      bytes: utils.encodeBase64(row.transaction_bytes),
      transaction_hash: utils.encodeBase64(row.transaction_hash),
      transaction_id: utils.createTransactionId(
        EntityId.fromEncodedId(row.payer_account_id).toString(),
        validStartTimestamp
      ),
      transfers: createCryptoTransferList(row.crypto_transfer_list),
      valid_duration_seconds: utils.getNullableNumber(row.valid_duration_seconds),
      valid_start_timestamp: utils.nsToSecNs(validStartTimestamp),
    };
  });

  const anchorSecNs = transactions.length > 0 ? transactions[transactions.length - 1].consensus_timestamp : 0;

  return {
    transactions,
    anchorSecNs,
  };
};

/**
 * Transactions queries are organized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function returns the outer query base on the consensus_timestamps list returned by the inner query.
 * Also see: getTransactionsInnerQuery function
 *
 * @param {String} innerQuery SQL query that provides a list of unique transactions that match the query criteria
 * @param {String} order Sorting order
 * @param {boolean} includeNftTransferList include the nft transfer list or not
 * @return {String} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = (innerQuery, order, includeNftTransferList) => {
  return `
    ${getSelectClauseWithTransfers(includeNftTransferList)}
    FROM ( ${innerQuery} ) AS tlist
       JOIN transaction t ON tlist.consensus_timestamp = t.consensus_ns
       LEFT OUTER JOIN t_transaction_results ttr ON ttr.proto_id = t.result
       LEFT OUTER JOIN t_transaction_types ttt ON ttt.proto_id = t.type
     ORDER BY t.consensus_ns ${order}`;
};

/**
 * Build the where clause from an array of query conditions
 *
 * @param {string} conditions Query conditions
 * @return {string} The where clause built from the query conditions
 */
const buildWhereClause = function (...conditions) {
  const clause = conditions.filter((q) => q !== '').join(' AND ');
  return clause === '' ? '' : `WHERE ${clause}`;
};

/**
 * Convert parameters to the named format.
 *
 * @param {String} query - the mysql query
 * @param {String} prefix - the prefix for the named parameters
 * @return {String} - The converted query
 */
const convertToNamedQuery = function (query, prefix) {
  let index = 0;
  return query.replace(/\?/g, () => {
    const namedParam = `?${prefix}${index}`;
    index += 1;
    return namedParam;
  });
};

/**
 * Get the query to find distinct timestamps for transactions matching the query filters from a transfers table when filtering by an account id
 *
 * @param {string} tableName - Name of the transfers table to query
 * @param {string} tableAlias - Alias to reference the table by
 * @param namedTsQuery - Transaction table timestamp query with named parameters
 * @param {string} timestampColumn - Name of the timestamp column for the table
 * @param resultTypeQuery - Transaction result query
 * @param transactionTypeQuery - Transaction type query
 * @param namedAccountQuery - Account query with named parameters
 * @param namedCreditDebitQuery - Credit/debit query
 * @param order - Sorting order
 * @param namedLimitQuery - Limit query with named parameters
 * @return {string} - The distinct timestamp query for the given table name
 */
const getTransferDistinctTimestampsQuery = function (
  tableName,
  tableAlias,
  namedTsQuery,
  timestampColumn,
  resultTypeQuery,
  transactionTypeQuery,
  namedAccountQuery,
  namedCreditDebitQuery,
  order,
  namedLimitQuery
) {
  const namedTransferTsQuery = namedTsQuery.replace(/t\.consensus_ns/g, `${tableAlias}.${timestampColumn}`);
  const joinClause =
    (resultTypeQuery || transactionTypeQuery) &&
    `JOIN transaction AS t ON ${tableAlias}.${timestampColumn} = t.consensus_ns`;
  const whereClause = buildWhereClause(
    namedAccountQuery,
    namedTransferTsQuery,
    resultTypeQuery,
    transactionTypeQuery,
    namedCreditDebitQuery
  );

  return `
    SELECT DISTINCT ${tableAlias}.${timestampColumn} AS consensus_timestamp
    FROM ${tableName} AS ${tableAlias} ${joinClause} ${whereClause}
    ORDER BY ${tableAlias}.consensus_timestamp ${order} ${namedLimitQuery}`;
};

/**
 * Transactions queries are organized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function forms the inner query base based on all the query criteria specified in the REST URL
 * It selects a list of unique transactions (consensus_timestamps).
 * Also see: getTransactionsOuterQuery function
 *
 * @param {String} accountQuery SQL query that filters based on the account ids
 * @param {String} tsQuery SQL query that filters based on the timestamps for transaction table
 * @param {String} resultTypeQuery SQL query that filters based on the result types
 * @param {String} limitQuery SQL query that limits the number of unique transactions returned
 * @param {String} creditDebitQuery SQL query that filters for credit/debit transactions
 * @param {String} transactionTypeQuery SQL query that filters by transaction type
 * @param {String} order Sorting order
 * @return {String} innerQuery SQL query that filters transactions based on various types of queries
 */
const getTransactionsInnerQuery = function (
  accountQuery,
  tsQuery,
  resultTypeQuery,
  limitQuery,
  creditDebitQuery,
  transactionTypeQuery,
  order
) {
  // convert the mysql style '?' placeholder to the named parameter format, later the same named parameter is converted
  // to the same positional index, thus the caller only has to pass the value once for the same column
  const namedAccountQuery = convertToNamedQuery(accountQuery, 'acct');
  const namedTsQuery = convertToNamedQuery(tsQuery, 'ts');
  const namedLimitQuery = convertToNamedQuery(limitQuery, 'limit');
  const namedCreditDebitQuery = convertToNamedQuery(creditDebitQuery, 'cd');

  const transactionAccountQuery = namedAccountQuery.replace(/ctl\.entity_id/g, 't.payer_account_id');
  const transactionWhereClause = buildWhereClause(
    transactionAccountQuery,
    namedTsQuery,
    resultTypeQuery,
    transactionTypeQuery
  );
  const transactionOnlyQuery = `
    SELECT consensus_ns AS consensus_timestamp
    FROM transaction AS t
      ${transactionWhereClause}
    ORDER BY consensus_ns ${order} ${namedLimitQuery}`;

  if (creditDebitQuery || namedAccountQuery) {
    const ctlQuery = getTransferDistinctTimestampsQuery(
      'crypto_transfer',
      'ctl',
      namedTsQuery,
      'consensus_timestamp',
      resultTypeQuery,
      transactionTypeQuery,
      namedAccountQuery,
      namedCreditDebitQuery,
      order,
      namedLimitQuery
    );

    const namedTtlAccountQuery = namedAccountQuery.replace(/ctl\.entity_id/g, 'ttl.account_id');
    const namedTtlCreditDebitQuery = namedCreditDebitQuery.replace(/ctl\.amount/g, 'ttl.amount');
    const ttlQuery = getTransferDistinctTimestampsQuery(
      'token_transfer',
      'ttl',
      namedTsQuery,
      'consensus_timestamp',
      resultTypeQuery,
      transactionTypeQuery,
      namedTtlAccountQuery,
      namedTtlCreditDebitQuery,
      order,
      namedLimitQuery
    );

    if (creditDebitQuery) {
      // credit/debit filter applies to crypto_transfer.amount and token_transfer.amount, a full outer join is needed to get
      // transactions that only have a crypto_transfer or a token_transfer
      return `
        SELECT COALESCE(ctl.consensus_timestamp, ttl.consensus_timestamp) AS consensus_timestamp
        FROM (${ctlQuery}) AS ctl
               FULL OUTER JOIN (${ttlQuery}) as ttl
                               ON ctl.consensus_timestamp = ttl.consensus_timestamp
        ORDER BY consensus_timestamp ${order}
          ${namedLimitQuery}`;
    }

    // account filter applies to transaction.payer_account_id, crypto_transfer.entity_id, nft_transfer.account_id,
    // and token_transfer.account_id, a full outer join between the four tables is needed to get rows that may only exist in one.
    return `
      SELECT coalesce(t.consensus_timestamp, ctl.consensus_timestamp, ttl.consensus_timestamp) AS consensus_timestamp
      FROM (${transactionOnlyQuery}) AS t
             FULL OUTER JOIN (${ctlQuery}) AS ctl
                             ON t.consensus_timestamp = ctl.consensus_timestamp
             FULL OUTER JOIN (${ttlQuery}) AS ttl
                             ON coalesce(t.consensus_timestamp, ctl.consensus_timestamp) = ttl.consensus_timestamp
      ORDER BY consensus_timestamp ${order}
        ${namedLimitQuery}`;
  }

  return transactionOnlyQuery;
};

const reqToSql = async function (req) {
  // Parse the filter parameters for account-numbers, timestamp, credit/debit, and pagination (limit)
  const parsedQueryParams = req.query;
  const [accountQuery, accountParams] = utils.parseAccountIdQueryParam(parsedQueryParams, 'ctl.entity_id');
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_ns');
  const [creditDebitQuery, creditDebitParams] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  const resultTypeQuery = utils.parseResultParams(req, 't.result');
  const transactionTypeQuery = utils.getTransactionTypeQuery(parsedQueryParams);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  const sqlParams = accountParams.concat(tsParams).concat(creditDebitParams).concat(params);
  const includeNftTransferList = false;

  const innerQuery = getTransactionsInnerQuery(
    accountQuery,
    tsQuery,
    resultTypeQuery,
    query,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );
  const sqlQuery = getTransactionsOuterQuery(innerQuery, order, includeNftTransferList);

  return {
    limit,
    query: utils.convertMySqlStyleQueryToPostgres(sqlQuery),
    order,
    params: sqlParams,
  };
};

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTransactions = async (req, res) => {
  // Validate query parameters first
  await utils.validateReq(req);

  const query = await reqToSql(req);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTransactions query: ${query.query} ${JSON.stringify(query.params)}`);
  }

  // Execute query
  const {rows, sqlQuery} = await utils.queryQuietly(query.query, ...query.params);
  const transferList = createTransferLists(rows);
  const ret = {
    transactions: transferList.transactions,
  };

  ret.links = {
    next: utils.getPaginationLink(
      req,
      ret.transactions.length !== query.limit,
      constants.filterKeys.TIMESTAMP,
      transferList.anchorSecNs,
      query.order
    ),
  };

  if (utils.isTestEnv()) {
    ret.sqlQuery = sqlQuery;
  }

  logger.debug(`getTransactions returning ${ret.transactions.length} entries`);
  res.locals[constants.responseDataLabel] = ret;
};

/**
 * Gets the scheduled db query from the scheduled param in the HTTP request query. The last scheduled value is honored.
 * If not present, returns empty string.
 *
 * @param {Object} query the HTTP request query
 * @return {string}
 */
const getScheduledQuery = (query) => {
  const scheduledValues = query[constants.filterKeys.SCHEDULED];
  if (scheduledValues === undefined) {
    return '';
  }

  let scheduled = scheduledValues;
  if (Array.isArray(scheduledValues)) {
    scheduled = scheduledValues[scheduledValues.length - 1];
  }

  return `t.scheduled = ${scheduled}`;
};

/**
 * Handler function for /transactions/:transaction_id API.
 * @param {Request} req HTTP request object
 * @return {} None.
 */
const getOneTransaction = async (req, res) => {
  await utils.validateReq(req);

  const transactionId = TransactionId.fromString(req.params.id);
  const scheduledQuery = getScheduledQuery(req.query);
  const sqlParams = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()];
  const whereClause = buildWhereClause('t.payer_account_id = ?', 't.valid_start_ns = ?', scheduledQuery);
  const includeNftTransferList = true;

  const sqlQuery = `
    ${getSelectClauseWithTransfers(includeNftTransferList)}
    FROM transaction t
    JOIN t_transaction_results ttr ON ttr.proto_id = t.result
    JOIN t_transaction_types ttt ON ttt.proto_id = t.type
    ${whereClause}
    ORDER BY consensus_ns ASC`;

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery);
  if (logger.isTraceEnabled()) {
    logger.trace(`getOneTransaction query: ${pgSqlQuery} ${JSON.stringify(sqlParams)}`);
  }

  // Execute query
  const {rows} = await utils.queryQuietly(pgSqlQuery, ...sqlParams);
  if (rows.length === 0) {
    throw new NotFoundError('Not found');
  }

  const transferList = createTransferLists(rows);
  logger.debug(`getOneTransaction returning ${transferList.transactions.length} entries`);
  res.locals[constants.responseDataLabel] = {
    transactions: transferList.transactions,
  };
};

module.exports = {
  getTransactions,
  getOneTransaction,
  createTransferLists,
  reqToSql,
  buildWhereClause,
  getTransactionsInnerQuery,
  getTransactionsOuterQuery,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    createCryptoTransferList,
    createNftTransferList,
    createTokenTransferList,
    createTransferLists,
  });
}
