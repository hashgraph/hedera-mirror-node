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

/**
 * Gets the select clause with token transfers sorted by token_id and account_id in the specified order
 *
 * @param {string} order sorting order
 * @return {string}
 */
const getSelectClauseWithTokenTransferOrder = (order) => {
  // token transfers are aggregated as an array of json objects {token_id, account_id, amount}
  return `SELECT
       t.payer_account_id,
       t.memo,
       t.consensus_ns,
       t.valid_start_ns,
       coalesce(ttr.result, 'UNKNOWN') AS result,
       coalesce(ttt.name, 'UNKNOWN') AS name,
       t.node_account_id,
       ctl.entity_id AS ctl_entity_id,
       ctl.amount AS amount,
       json_agg(
         json_build_object(
           'token_id', ttl.token_id::text,
           'account_id', ttl.account_id::text,
           'amount', ttl.amount
         ) ORDER BY
             ttl.token_id ${order || ''},
             ttl.account_id ${order || ''}
       ) FILTER (WHERE ttl.token_id IS NOT NULL) AS token_transfer_list,
       t.charged_tx_fee,
       t.valid_duration_seconds,
       t.max_fee,
       t.transaction_hash,
       t.scheduled`;
};

/**
 * Creates token transfer list from aggregated array of JSON objects in the query result
 *
 * @param tokenTransferList token transfer list string
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
 * Create transferlists from the output of SQL queries. The SQL table has different
 * rows for each of the transfers in a single transaction. This function collates all
 * transfers into a single list.
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @return {{anchorSecNs: (String|number), transactions: {}}}
 */
const createTransferLists = (rows) => {
  // If the transaction has a transferlist (i.e. list of individual trasnfers, it
  // will show up as separate rows. Combine those into a single transferlist for
  // a given consensus_ns (Note that there could be two records for the same
  // transaction-id where one would pass and others could fail as duplicates)
  const transactions = {};

  for (const row of rows) {
    if (!(row.consensus_ns in transactions)) {
      const validStartTimestamp = row.valid_start_ns;
      transactions[row.consensus_ns] = {
        charged_tx_fee: Number(row.charged_tx_fee),
        consensus_timestamp: utils.nsToSecNs(row.consensus_ns),
        id: row.id,
        max_fee: utils.getNullableNumber(row.max_fee),
        memo_base64: utils.encodeBase64(row.memo),
        name: row.name,
        node: EntityId.fromEncodedId(row.node_account_id, true).toString(),
        result: row.result,
        scheduled: row.scheduled,
        token_transfers: createTokenTransferList(row.token_transfer_list),
        transaction_hash: utils.encodeBase64(row.transaction_hash),
        transaction_id: utils.createTransactionId(
          EntityId.fromEncodedId(row.payer_account_id).toString(),
          validStartTimestamp
        ),
        transfers: [],
        valid_duration_seconds: utils.getNullableNumber(row.valid_duration_seconds),
        valid_start_timestamp: utils.nsToSecNs(validStartTimestamp),
      };
    }

    if (row.ctl_entity_id !== null) {
      transactions[row.consensus_ns].transfers.push({
        account: EntityId.fromEncodedId(row.ctl_entity_id).toString(),
        amount: Number(row.amount),
      });
    }
  }

  const anchorSecNs = rows.length > 0 ? utils.nsToSecNs(rows[rows.length - 1].consensus_ns) : 0;

  return {
    transactions: Object.values(transactions),
    anchorSecNs,
  };
};

/**
 * Transactions queries are organized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function returns the outer query base on the consensus_timestamps list returned by the inner query.
 * Also see: getTransactionsInnerQuery function
 * @param {String} innerQuery SQL query that provides a list of unique transactions that match the query criteria
 * @param {String} order Sorting order
 * @return {{Promise<String>}} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = async (innerQuery, order) => {
  return `
    ${getSelectClauseWithTokenTransferOrder(order)}
    FROM ( ${innerQuery} ) AS tlist
       JOIN transaction t ON tlist.consensus_timestamp = t.consensus_ns
       LEFT OUTER JOIN t_transaction_results ttr ON ttr.proto_id = t.result
       LEFT OUTER JOIN t_transaction_types ttt ON ttt.proto_id = t.type
       LEFT OUTER JOIN crypto_transfer ctl ON tlist.consensus_timestamp = ctl.consensus_timestamp
       LEFT OUTER JOIN token_transfer ttl ON tlist.consensus_timestamp = ttl.consensus_timestamp
     GROUP BY t.consensus_ns, ctl_entity_id, ctl.amount, ttr.result, ttt.name, t.payer_account_id, t.memo, t.valid_start_ns, t.node_account_id, t.charged_tx_fee, t.valid_duration_seconds, t.max_fee, t.transaction_hash
     ORDER BY t.consensus_ns ${order} , ctl_entity_id ASC, amount ASC`;
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
 * Get the transactions inner query for transactions with a crypto or token transfer list matching the query filters.
 *
 * @param namedAccountQuery - Account query with named parameters
 * @param namedTsQuery - Transaction table timestamp query with named parameters
 * @param resultTypeQuery - Transaction result query
 * @param limitQuery - Limit query
 * @param namedCreditDebitQuery - Credit or debit query
 * @param transactionTypeQuery - Transaction type query
 * @param order - Sorting order
 * @return {string} - The inner query string
 */
const getCreditDebitTransferTransactionsInnerQuery = function (
  namedAccountQuery,
  namedTsQuery,
  resultTypeQuery,
  limitQuery,
  namedCreditDebitQuery,
  transactionTypeQuery,
  order
) {
  const namedCtlTsQuery = namedTsQuery.replace(/t\.consensus_ns/g, 'ctl.consensus_timestamp');
  const ctlWhereClause = buildWhereClause(namedAccountQuery, namedCtlTsQuery, namedCreditDebitQuery);
  const namedTtlAccountQuery = namedAccountQuery.replace(/ctl\.entity_id/g, 'ttl.account_id');
  const namedTtlTsQuery = namedTsQuery.replace(/t\.consensus_ns/g, 'ttl.consensus_timestamp');
  const namedTtlCreditDebitQuery = namedCreditDebitQuery.replace(/ctl\.amount/g, 'ttl.amount');

  const ttlWhereClause = buildWhereClause(namedTtlAccountQuery, namedTtlTsQuery, namedTtlCreditDebitQuery);
  const ctlSubQuery = `
    SELECT DISTINCT COALESCE(ctl.consensus_timestamp, ttl.consensus_timestamp) AS consensus_timestamp
    FROM
    (SELECT DISTINCT consensus_timestamp
        FROM crypto_transfer ctl
        ${ctlWhereClause}
        ORDER BY consensus_timestamp ${order}
        ${limitQuery}) as ctl
    FULL OUTER JOIN
        (SELECT DISTINCT consensus_timestamp
            FROM token_transfer ttl
            ${ttlWhereClause}
            ORDER BY consensus_timestamp ${order}
            ${limitQuery}) as ttl
    ON ctl.consensus_timestamp = ttl.consensus_timestamp
    ORDER BY consensus_timestamp DESC`;

  if (resultTypeQuery || transactionTypeQuery) {
    const whereClause = buildWhereClause(namedTsQuery, resultTypeQuery, transactionTypeQuery);
    return `
      SELECT t.consensus_ns AS consensus_timestamp
      FROM transaction t
      JOIN (${ctlSubQuery}) AS ctl
      ON t.consensus_ns = ctl.consensus_timestamp
      ${whereClause}
      ORDER BY t.consensus_ns ${order}
      ${limitQuery}`;
  }

  // get consensus timestamps from crypto_transfer table directly when there are no transaction related filters
  return `${ctlSubQuery}
    ${limitQuery}`;
};

/**
 * Get the general transactions inner query for transactions matching the query filters.
 *
 * @param namedAccountQuery - Account query with named parameters
 * @param namedTsQuery - Transaction table timestamp query with named parameters
 * @param resultTypeQuery - Transaction result query
 * @param namedLimitQuery - Limit query with named parameters
 * @param transactionTypeQuery - Transaction type query
 * @param order - Sorting order
 * @return {string} - The inner query string
 */
const getGeneralTransactionsInnerQuery = function (
  namedAccountQuery,
  namedTsQuery,
  resultTypeQuery,
  namedLimitQuery,
  transactionTypeQuery,
  order
) {
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
    ORDER BY consensus_ns ${order}
    ${namedLimitQuery}`;

  if (namedAccountQuery) {
    // account filter applies to transaction.payer_account_id, crypto_transfer.entity_id, and token_transfer.account_id, a full outer join
    //between the three tables is needed to get rows that may only exist in one.
    const ctlQuery = getTransferDistinctTimestampsQuery(
      'crypto_transfer',
      'ctl',
      namedTsQuery,
      'consensus_timestamp',
      resultTypeQuery,
      transactionTypeQuery,
      namedAccountQuery,
      order,
      namedLimitQuery
    );

    const namedTtlAccountQuery = namedAccountQuery.replace(/ctl\.entity_id/g, 'ttl.account_id');
    const ttlQuery = getTransferDistinctTimestampsQuery(
      'token_transfer',
      'ttl',
      namedTsQuery,
      'consensus_timestamp',
      resultTypeQuery,
      transactionTypeQuery,
      namedTtlAccountQuery,
      order,
      namedLimitQuery
    );

    return `
      SELECT coalesce(t.consensus_timestamp,ctl.consensus_timestamp,ttl.consensus_timestamp) AS consensus_timestamp
      FROM (${transactionOnlyQuery}) AS t
      FULL OUTER JOIN (${ctlQuery}) AS ctl
      ON t.consensus_timestamp = ctl.consensus_timestamp
      FULL OUTER JOIN (${ttlQuery}) AS ttl
      ON t.consensus_timestamp = ttl.consensus_timestamp
      ORDER BY consensus_timestamp ${order}
      ${namedLimitQuery}`;
  }

  // no account filter, only need to query transaction table
  return transactionOnlyQuery;
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
  order,
  namedLimitQuery
) {
  const namedTransferTsQuery = namedTsQuery.replace(/t\.consensus_ns/g, `${tableAlias}.${timestampColumn}`);
  const joinClause =
    (resultTypeQuery || transactionTypeQuery) &&
    `JOIN transaction AS t ON ${tableAlias}.${timestampColumn} = t.consensus_ns`;
  const whereClause = buildWhereClause(namedAccountQuery, namedTransferTsQuery, resultTypeQuery, transactionTypeQuery);

  return `
      SELECT DISTINCT ${tableAlias}.${timestampColumn} AS consensus_timestamp
        FROM ${tableName} AS ${tableAlias}
        ${joinClause}
        ${whereClause}
        ORDER BY ${tableAlias}.consensus_timestamp ${order}
        ${namedLimitQuery}`;
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

  if (creditDebitQuery) {
    // limit the query to transactions with crypto or token transfer list
    return getCreditDebitTransferTransactionsInnerQuery(
      namedAccountQuery,
      namedTsQuery,
      resultTypeQuery,
      namedLimitQuery,
      namedCreditDebitQuery,
      transactionTypeQuery,
      order
    );
  }

  return getGeneralTransactionsInnerQuery(
    namedAccountQuery,
    namedTsQuery,
    resultTypeQuery,
    namedLimitQuery,
    transactionTypeQuery,
    order
  );
};

const reqToSql = async function (req) {
  // Parse the filter parameters for account-numbers, timestamp, credit/debit, and pagination (limit)
  const parsedQueryParams = req.query;
  const [accountQuery, accountParams] = utils.parseAccountIdQueryParam(parsedQueryParams, 'ctl.entity_id');
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_ns');
  const [creditDebitQuery, creditDebitParams] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  const resultTypeQuery = utils.parseResultParams(req, 't.result');
  const transactionTypeQuery = await utils.getTransactionTypeQuery(parsedQueryParams);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  const sqlParams = accountParams.concat(tsParams).concat(creditDebitParams).concat(params);

  const innerQuery = getTransactionsInnerQuery(
    accountQuery,
    tsQuery,
    resultTypeQuery,
    query,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );
  const sqlQuery = await getTransactionsOuterQuery(innerQuery, order);

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

  const sqlQuery = `
    ${getSelectClauseWithTokenTransferOrder()}
    FROM transaction t
    JOIN t_transaction_results ttr ON ttr.proto_id = t.result
    JOIN t_transaction_types ttt ON ttt.proto_id = t.type
    LEFT JOIN crypto_transfer ctl ON ctl.consensus_timestamp = t.consensus_ns
    LEFT JOIN token_transfer ttl ON t.consensus_ns = ttl.consensus_timestamp
    ${whereClause}
    GROUP BY consensus_ns, ctl_entity_id, ctl.amount, ttr.result, ttt.name, t.payer_account_id, t.memo,
      t.valid_start_ns, t.node_account_id, t.charged_tx_fee, t.valid_duration_seconds, t.max_fee, t.transaction_hash
    ORDER BY consensus_ns ASC, ctl_entity_id ASC, ctl.amount ASC`;

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
