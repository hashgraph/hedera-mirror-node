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

const utils = require('./utils');
const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const TransactionId = require('./transactionId');
const {NotFoundError} = require('./errors/notFoundError');
const {AssessedCustomFee, CryptoTransfer, NftTransfer, TokenTransfer, Transaction} = require('./model');
const {AssessedCustomFeeViewModel, NftTransferViewModel} = require('./viewmodel');

/**
 * Gets the select clause with crypto transfers, token transfers, and nft transfers
 *
 * @param {boolean} includeExtraInfo - include extra info: the nft transfer list, the assessed custom fees, and etc
 * @return {string}
 */
const getSelectClauseWithTransfers = (includeExtraInfo, innerQuery, order = 'desc') => {
  const transactionTimeStampCte = (modifyingQuery) => {
    let timestampFilter = '';
    let timestampFilterJoin = '';
    let limitQuery = 'limit $1';

    // populate pre-clause queries where a timestamp filter is applied
    if (!_.isUndefined(modifyingQuery)) {
      timestampFilter = `timestampFilter as (${modifyingQuery}),`;
      timestampFilterJoin = `join timestampFilter tf on ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} = tf.consensus_timestamp`;
      limitQuery = '';
    }

    const tquery = `select
                      ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME},
                      ${Transaction.PAYER_ACCOUNT_ID_FULL_NAME},
                      ${Transaction.VALID_START_NS_FULL_NAME},
                      ${Transaction.MEMO_FULL_NAME},
                      ${Transaction.NODE_ACCOUNT_ID_FULL_NAME},
                      ${Transaction.CHARGED_TX_FEE_FULL_NAME},
                      ${Transaction.VALID_DURATION_SECONDS_FULL_NAME},
                      ${Transaction.MAX_FEE_FULL_NAME},
                      ${Transaction.TRANSACTION_HASH_FULL_NAME},
                      ${Transaction.SCHEDULED_FULL_NAME},
                      ${Transaction.ENTITY_ID_FULL_NAME},
                      ${Transaction.TRANSACTION_BYTES_FULL_NAME},
                      ${Transaction.RESULT_FULL_NAME},
                      ${Transaction.TYPE_FULL_NAME}
                    from ${Transaction.tableName} as ${Transaction.tableAlias}
                    ${timestampFilterJoin}
                    order by ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} ${order}
                    ${limitQuery}`;

    return `${timestampFilter}
      tlist as (${tquery})`;
  };

  // aggregate crypto transfers, token transfers, and nft transfers
  const cryptoTransferListCte = `c_list as (
      select jsonb_agg(jsonb_build_object(
              '${CryptoTransfer.AMOUNT}', ${CryptoTransfer.AMOUNT_FULL_NAME},
              '${CryptoTransfer.ENTITY_ID}', ${CryptoTransfer.ENTITY_ID_FULL_NAME}
          ) order by ${CryptoTransfer.ENTITY_ID_FULL_NAME}, ${CryptoTransfer.AMOUNT_FULL_NAME}
        ) as ctr_list,
        ${CryptoTransfer.CONSENSUS_TIMESTAMP_FULL_NAME}
      from ${CryptoTransfer.tableName} ${CryptoTransfer.tableAlias}
      join tlist on ${CryptoTransfer.CONSENSUS_TIMESTAMP_FULL_NAME} = tlist.consensus_timestamp
      group by ${CryptoTransfer.CONSENSUS_TIMESTAMP_FULL_NAME}
  )`;

  const tokenTransferListCte = `t_list as (
    select jsonb_agg(jsonb_build_object(
          '${TokenTransfer.ACCOUNT_ID}', ${TokenTransfer.ACCOUNT_ID_FULL_NAME},
          '${TokenTransfer.AMOUNT}', ${TokenTransfer.AMOUNT_FULL_NAME},
          '${TokenTransfer.TOKEN_ID}', ${TokenTransfer.TOKEN_ID_FULL_NAME}
        ) order by ${TokenTransfer.TOKEN_ID_FULL_NAME}, ${TokenTransfer.ACCOUNT_ID_FULL_NAME}
      ) as ttr_list,
      ${TokenTransfer.CONSENSUS_TIMESTAMP_FULL_NAME}
    from ${TokenTransfer.tableName} ${TokenTransfer.tableAlias}
    join tlist on ${TokenTransfer.CONSENSUS_TIMESTAMP_FULL_NAME} = tlist.consensus_timestamp
    group by ${TokenTransfer.CONSENSUS_TIMESTAMP_FULL_NAME}
  )`;

  const nftTransferListCte = `nft_list as (
    select jsonb_agg(jsonb_build_object(
          '${NftTransfer.RECEIVER_ACCOUNT_ID}', ${NftTransfer.RECEIVER_ACCOUNT_ID_FULL_NAME},
          '${NftTransfer.SENDER_ACCOUNT_ID}', ${NftTransfer.SENDER_ACCOUNT_ID_FULL_NAME},
          '${NftTransfer.SERIAL_NUMBER}', ${NftTransfer.SERIAL_NUMBER_FULL_NAME},
          '${NftTransfer.TOKEN_ID}', ${NftTransfer.TOKEN_ID_FULL_NAME}
        ) order by ${NftTransfer.TOKEN_ID_FULL_NAME}, ${NftTransfer.SERIAL_NUMBER_FULL_NAME}
      ) as ntr_list,
      ${NftTransfer.CONSENSUS_TIMESTAMP_FULL_NAME}
    from ${NftTransfer.tableName} ${NftTransfer.tableAlias}
    join tlist on ${NftTransfer.CONSENSUS_TIMESTAMP_FULL_NAME} = tlist.consensus_timestamp
    group by ${NftTransfer.CONSENSUS_TIMESTAMP_FULL_NAME}
  )`;

  const assessedFeeListCte = `fee_list as (
    select jsonb_agg(jsonb_build_object(
          '${AssessedCustomFee.AMOUNT}', ${AssessedCustomFee.AMOUNT},
          '${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}', ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID},
          '${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS}', ${AssessedCustomFee.EFFECTIVE_PAYER_ACCOUNT_IDS},
          'payer_account_id', ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID},
          '${AssessedCustomFee.TOKEN_ID}', ${AssessedCustomFee.TOKEN_ID}
        ) order by ${AssessedCustomFee.COLLECTOR_ACCOUNT_ID}, ${AssessedCustomFee.AMOUNT}
      ) as ftr_list,
      ${AssessedCustomFee.CONSENSUS_TIMESTAMP_FULL_NAME}
    from ${AssessedCustomFee.tableName} ${AssessedCustomFee.tableAlias}
    join tlist on ${AssessedCustomFee.CONSENSUS_TIMESTAMP_FULL_NAME} = tlist.consensus_timestamp
    group by ${AssessedCustomFee.CONSENSUS_TIMESTAMP_FULL_NAME}
  )`;

  const transfersListCte = (extraInfo) => {
    let nftAndFeeSelect = '';
    let nftList = '';
    let feeList = '';
    let nftJoin = '';
    let feeJoin = '';
    if (extraInfo) {
      nftAndFeeSelect = ', ntrl.consensus_timestamp, ftrl.consensus_timestamp';
      nftList = 'ntrl.ntr_list,';
      feeList = 'ftrl.ftr_list,';
      nftJoin = 'full outer join nft_list ntrl on t.consensus_timestamp = ntrl.consensus_timestamp';
      feeJoin = 'full outer join fee_list ftrl on t.consensus_timestamp = ftrl.consensus_timestamp';
    }

    return `transfer_list as (
      select coalesce(t.consensus_timestamp, ctrl.consensus_timestamp, ttrl.consensus_timestamp${nftAndFeeSelect}) AS consensus_timestamp,
        ctrl.ctr_list,
        ttrl.ttr_list,
        ${nftList}
        ${feeList}
        t.payer_account_id,
        t.valid_start_ns,
        t.memo,
        t.node_account_id,
        t.charged_tx_fee,
        t.valid_duration_seconds,
        t.max_fee,
        t.transaction_hash,
        t.scheduled,
        t.entity_id,
        t.transaction_bytes,
        t.result,
        t.type
      from tlist t
      full outer join c_list ctrl on t.consensus_timestamp = ctrl.consensus_timestamp
      full outer join t_list ttrl on t.consensus_timestamp = ttrl.consensus_timestamp
      ${nftJoin}
      ${feeJoin}
    )`;
  };
  const ctes = [transactionTimeStampCte(innerQuery), cryptoTransferListCte, tokenTransferListCte];

  const fields = [
    Transaction.PAYER_ACCOUNT_ID_FULL_NAME,
    Transaction.MEMO_FULL_NAME,
    Transaction.CONSENSUS_TIMESTAMP_FULL_NAME,
    Transaction.VALID_START_NS_FULL_NAME,
    `coalesce(ttr.result, 'UNKNOWN') AS result`,
    `coalesce(ttt.name, 'UNKNOWN') AS name`,
    Transaction.NODE_ACCOUNT_ID_FULL_NAME,
    Transaction.CHARGED_TX_FEE_FULL_NAME,
    Transaction.VALID_DURATION_SECONDS_FULL_NAME,
    Transaction.MAX_FEE_FULL_NAME,
    Transaction.TRANSACTION_HASH_FULL_NAME,
    Transaction.SCHEDULED_FULL_NAME,
    Transaction.ENTITY_ID_FULL_NAME,
    Transaction.TRANSACTION_BYTES_FULL_NAME,
    `t.ctr_list AS crypto_transfer_list`,
    `t.ttr_list AS token_transfer_list`,
  ];

  if (includeExtraInfo) {
    ctes.push(nftTransferListCte, assessedFeeListCte);
    fields.push(`t.ntr_list AS nft_transfer_list`);
    fields.push(`t.ftr_list AS assessed_custom_fees`);
  }

  // push transfers list last to ensure CTE's are in order
  ctes.push(transfersListCte(includeExtraInfo));
  return `with ${ctes.join(',\n')}
    SELECT
    ${fields.join(',\n')}
  `;
};

/**
 * Creates an assessed custom fee list from aggregated array of JSON objects in the query result
 *
 * @param assessedCustomFees assessed custom fees
 * @return {undefined|{amount: Number, collector_account_id: string, payer_account_id: string, token_id: string}[]}
 */
const createAssessedCustomFeeList = (assessedCustomFees) => {
  if (!assessedCustomFees) {
    return undefined;
  }

  return assessedCustomFees.map((assessedCustomFee) => {
    const model = new AssessedCustomFee(assessedCustomFee);
    return new AssessedCustomFeeViewModel(model);
  });
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
    const nftTransfer = new NftTransfer(transfer);
    return new NftTransferViewModel(nftTransfer);
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
    const payerAccountId = EntityId.fromEncodedId(row.payer_account_id).toString();
    return {
      charged_tx_fee: Number(row.charged_tx_fee),
      consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
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
      transaction_id: utils.createTransactionId(payerAccountId, validStartTimestamp),
      transfers: createCryptoTransferList(row.crypto_transfer_list),
      valid_duration_seconds: utils.getNullableNumber(row.valid_duration_seconds),
      valid_start_timestamp: utils.nsToSecNs(validStartTimestamp),
      assessed_custom_fees: createAssessedCustomFeeList(row.assessed_custom_fees),
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
 * @param {boolean} includeExtraInfo include extra info or not
 * @return {String} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = (innerQuery, order, includeExtraInfo = false) => {
  return `
    ${getSelectClauseWithTransfers(includeExtraInfo, innerQuery, order)}
    FROM transfer_list t
       LEFT OUTER JOIN t_transaction_results ttr ON ttr.proto_id = ${Transaction.RESULT_FULL_NAME}
       LEFT OUTER JOIN t_transaction_types ttt ON ttt.proto_id = ${Transaction.TYPE_FULL_NAME}
     ORDER BY ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} ${order}`;
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
  const namedTransferTsQuery = namedTsQuery.replace(/t\.consensus_timestamp/g, `${tableAlias}.${timestampColumn}`);
  const joinClause =
    (resultTypeQuery || transactionTypeQuery) &&
    `JOIN ${Transaction.tableName} AS ${Transaction.tableAlias} ON ${tableAlias}.${timestampColumn} = ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME}`;
  const whereClause = buildWhereClause(
    namedAccountQuery,
    namedTransferTsQuery,
    resultTypeQuery,
    transactionTypeQuery,
    namedCreditDebitQuery
  );

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

  const transactionAccountQuery = namedAccountQuery.replace(/ctl\.entity_id/g, 't.payer_account_id');
  const transactionWhereClause = buildWhereClause(
    transactionAccountQuery,
    namedTsQuery,
    resultTypeQuery,
    transactionTypeQuery
  );
  const transactionOnlyLimitQuery = _.isNil(namedLimitQuery) ? '' : namedLimitQuery;
  const transactionOnlyQuery = _.isEmpty(transactionWhereClause)
    ? undefined
    : `select ${Transaction.CONSENSUS_TIMESTAMP}
    from ${Transaction.tableName} as ${Transaction.tableAlias}
    ${transactionWhereClause}
    order by ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} ${order}
    ${transactionOnlyLimitQuery}`;

  if (creditDebitQuery || namedAccountQuery) {
    const ctlQuery = getTransferDistinctTimestampsQuery(
      CryptoTransfer.tableName,
      'ctl',
      namedTsQuery,
      CryptoTransfer.CONSENSUS_TIMESTAMP,
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
      TokenTransfer.tableName,
      'ttl',
      namedTsQuery,
      TokenTransfer.CONSENSUS_TIMESTAMP,
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
      FROM (${transactionOnlyQuery}) AS ${Transaction.tableAlias}
      FULL OUTER JOIN (${ctlQuery}) AS ctl
      ON ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} = ctl.consensus_timestamp
      FULL OUTER JOIN (${ttlQuery}) AS ttl
      ON coalesce(${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME}, ctl.consensus_timestamp) = ttl.consensus_timestamp
      ${namedLimitQuery}`;
  }

  return transactionOnlyQuery;
};

const reqToSql = function (req) {
  // Parse the filter parameters for account-numbers, timestamp, credit/debit, and pagination (limit)
  const parsedQueryParams = req.query;
  let sqlParams = [];
  let [accountQuery, accountParams] = utils.parseAccountIdQueryParam(parsedQueryParams, 'ctl.entity_id');
  accountQuery = utils.convertMySqlStyleQueryToPostgres(accountQuery, sqlParams.length + 1);
  sqlParams = sqlParams.concat(accountParams);
  let [tsQuery, tsParams] = utils.parseTimestampQueryParam(
    parsedQueryParams,
    Transaction.CONSENSUS_TIMESTAMP_FULL_NAME
  );
  tsQuery = utils.convertMySqlStyleQueryToPostgres(tsQuery, sqlParams.length + 1);
  sqlParams = sqlParams.concat(tsParams);
  let [creditDebitQuery, creditDebitParams] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  creditDebitQuery = utils.convertMySqlStyleQueryToPostgres(creditDebitQuery, sqlParams.length + 1);
  sqlParams = sqlParams.concat(creditDebitParams);
  const resultTypeQuery = utils.parseResultParams(req, Transaction.RESULT_FULL_NAME);
  const transactionTypeQuery = utils.getTransactionTypeQuery(parsedQueryParams);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  sqlParams = sqlParams.concat(params);

  const innerQuery = getTransactionsInnerQuery(
    accountQuery,
    tsQuery,
    resultTypeQuery,
    query,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );
  const sqlQuery = getTransactionsOuterQuery(innerQuery, order);

  return {
    limit,
    query: utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams.length),
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
  utils.validateReq(req);

  const query = reqToSql(req);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTransactions query: ${query.query} ${JSON.stringify(query.params)}`);
  }

  // Execute query
  const {rows, sqlQuery} = await pool.queryQuietly(query.query, ...query.params);
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

  return `${Transaction.SCHEDULED_FULL_NAME} = ${scheduled}`;
};

/**
 * Handler function for /transactions/:transactionId API.
 * @param {Request} req HTTP request object
 * @return {} None.
 */
const getOneTransaction = async (req, res) => {
  utils.validateReq(req);

  const transactionId = TransactionId.fromString(req.params.transactionId);
  const scheduledQuery = getScheduledQuery(req.query);
  const sqlParams = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs(), config.maxLimit];
  const whereClause = buildWhereClause(
    `${Transaction.PAYER_ACCOUNT_ID_FULL_NAME} = ?`,
    `${Transaction.VALID_START_NS_FULL_NAME} = ?`,
    scheduledQuery
  );
  const includeExtraInfo = true;

  const innerQuery = `select ${Transaction.CONSENSUS_TIMESTAMP}
                      from ${Transaction.tableName} AS ${Transaction.tableAlias}
                        ${whereClause}
                      order by ${Transaction.CONSENSUS_TIMESTAMP} desc
                      limit $3`;

  const sqlQuery = `
    ${getSelectClauseWithTransfers(includeExtraInfo, innerQuery)}
    FROM transfer_list t
    JOIN t_transaction_results ttr ON ttr.proto_id = ${Transaction.RESULT_FULL_NAME}
    JOIN t_transaction_types ttt ON ttt.proto_id = ${Transaction.TYPE_FULL_NAME}
    ORDER BY ${Transaction.CONSENSUS_TIMESTAMP_FULL_NAME} ASC`;

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery);
  if (logger.isTraceEnabled()) {
    logger.trace(`getOneTransaction query: ${pgSqlQuery} ${JSON.stringify(sqlParams)}`);
  }

  // Execute query
  const {rows} = await pool.queryQuietly(pgSqlQuery, ...sqlParams);
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
    createAssessedCustomFeeList,
    createCryptoTransferList,
    createNftTransferList,
    createTokenTransferList,
    createTransferLists,
  });
}
