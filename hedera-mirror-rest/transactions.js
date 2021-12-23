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
const constants = require('./constants');
const EntityId = require('./entityId');
const TransactionId = require('./transactionId');
const {NotFoundError} = require('./errors/notFoundError');
const {
  AssessedCustomFee,
  CryptoTransfer,
  NftTransfer,
  TokenTransfer,
  Transaction,
  TransactionResult,
  TransactionType,
} = require('./model');
const {AssessedCustomFeeViewModel, NftTransferViewModel} = require('./viewmodel');

const transactionFields = [
  Transaction.CHARGED_TX_FEE,
  Transaction.CONSENSUS_TIMESTAMP,
  Transaction.ENTITY_ID,
  Transaction.MAX_FEE,
  Transaction.MEMO,
  Transaction.NODE_ACCOUNT_ID,
  Transaction.NONCE,
  Transaction.PARENT_CONSENSUS_TIMESTAMP,
  Transaction.PAYER_ACCOUNT_ID,
  Transaction.RESULT,
  Transaction.SCHEDULED,
  Transaction.TRANSACTION_BYTES,
  Transaction.TRANSACTION_HASH,
  Transaction.TYPE,
  Transaction.VALID_DURATION_SECONDS,
  Transaction.VALID_START_NS,
];
const transactionFullFields = transactionFields.map((f) => Transaction.getFullName(f));
// consensus_timestamp in transfer_list is a coalesce of multiple consensus timestamp columns
const transferListFullFields = transactionFields
  .filter((f) => f !== Transaction.CONSENSUS_TIMESTAMP)
  .map((f) => `t.${f}`);

/**
 * Gets the select clause with crypto transfers, token transfers, and nft transfers
 *
 * @param {boolean} includeExtraInfo - include extra info: the nft transfer list, the assessed custom fees, and etc
 * @param innerQuery
 * @param order
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
      timestampFilterJoin = `join timestampFilter tf on ${Transaction.getFullName(
        Transaction.CONSENSUS_TIMESTAMP
      )} = tf.consensus_timestamp`;
      limitQuery = '';
    }

    const tquery = `select ${transactionFullFields}
                    from ${Transaction.tableName} ${Transaction.tableAlias}
                    ${timestampFilterJoin}
                    order by ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}
                    ${limitQuery}`;

    return `${timestampFilter}
      tlist as (${tquery})`;
  };

  // aggregate crypto transfers, token transfers, and nft transfers
  const cryptoTransferListCte = `c_list as (
      select jsonb_agg(jsonb_build_object(
              '${CryptoTransfer.AMOUNT}', ${CryptoTransfer.AMOUNT},
              '${CryptoTransfer.ENTITY_ID}', ${CryptoTransfer.getFullName(CryptoTransfer.ENTITY_ID)}
          ) order by ${CryptoTransfer.getFullName(CryptoTransfer.ENTITY_ID)}, ${CryptoTransfer.AMOUNT}
        ) as ctr_list,
        ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)}
      from ${CryptoTransfer.tableName} ${CryptoTransfer.tableAlias}
      join tlist on ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)} = tlist.consensus_timestamp
      group by ${CryptoTransfer.getFullName(CryptoTransfer.CONSENSUS_TIMESTAMP)}
  )`;

  const tokenTransferListCte = `t_list as (
    select jsonb_agg(jsonb_build_object(
          '${TokenTransfer.ACCOUNT_ID}', ${TokenTransfer.ACCOUNT_ID},
          '${TokenTransfer.AMOUNT}', ${TokenTransfer.AMOUNT},
          '${TokenTransfer.TOKEN_ID}', ${TokenTransfer.TOKEN_ID}
        ) order by ${TokenTransfer.TOKEN_ID}, ${TokenTransfer.ACCOUNT_ID}
      ) as ttr_list,
      ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)}
    from ${TokenTransfer.tableName} ${TokenTransfer.tableAlias}
    join tlist on ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)} = tlist.consensus_timestamp
    group by ${TokenTransfer.getFullName(TokenTransfer.CONSENSUS_TIMESTAMP)}
  )`;

  const nftTransferListCte = `nft_list as (
    select jsonb_agg(jsonb_build_object(
          '${NftTransfer.RECEIVER_ACCOUNT_ID}', ${NftTransfer.RECEIVER_ACCOUNT_ID},
          '${NftTransfer.SENDER_ACCOUNT_ID}', ${NftTransfer.SENDER_ACCOUNT_ID},
          '${NftTransfer.SERIAL_NUMBER}', ${NftTransfer.SERIAL_NUMBER},
          '${NftTransfer.TOKEN_ID}', ${NftTransfer.TOKEN_ID}
        ) order by ${NftTransfer.TOKEN_ID}, ${NftTransfer.SERIAL_NUMBER}
      ) as ntr_list,
      ${NftTransfer.getFullName(NftTransfer.CONSENSUS_TIMESTAMP)}
    from ${NftTransfer.tableName} ${NftTransfer.tableAlias}
    join tlist on ${NftTransfer.getFullName(NftTransfer.CONSENSUS_TIMESTAMP)} = tlist.consensus_timestamp
    group by ${NftTransfer.getFullName(NftTransfer.CONSENSUS_TIMESTAMP)}
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
      ${AssessedCustomFee.getFullName(AssessedCustomFee.CONSENSUS_TIMESTAMP)}
    from ${AssessedCustomFee.tableName} ${AssessedCustomFee.tableAlias}
    join tlist on ${AssessedCustomFee.getFullName(AssessedCustomFee.CONSENSUS_TIMESTAMP)} = tlist.consensus_timestamp
    group by ${AssessedCustomFee.getFullName(AssessedCustomFee.CONSENSUS_TIMESTAMP)}
  )`;

  const transfersListCte = (extraInfo) => {
    const consensusTimestampFields = ['t.consensus_timestamp', 'ctrl.consensus_timestamp', 'ttrl.consensus_timestamp'];
    let nftList = '';
    let feeList = '';
    let nftJoin = '';
    let feeJoin = '';
    if (extraInfo) {
      consensusTimestampFields.push('ntrl.consensus_timestamp', 'ftrl.consensus_timestamp');
      nftList = 'ntrl.ntr_list,';
      feeList = 'ftrl.ftr_list,';
      nftJoin = 'full outer join nft_list ntrl on t.consensus_timestamp = ntrl.consensus_timestamp';
      feeJoin = 'full outer join fee_list ftrl on t.consensus_timestamp = ftrl.consensus_timestamp';
    }

    return `transfer_list as (
      select coalesce(${consensusTimestampFields}) AS consensus_timestamp,
        ctrl.ctr_list,
        ttrl.ttr_list,
        ${nftList}
        ${feeList}
        ${transferListFullFields}
      from tlist t
      full outer join c_list ctrl on t.consensus_timestamp = ctrl.consensus_timestamp
      full outer join t_list ttrl on t.consensus_timestamp = ttrl.consensus_timestamp
      ${nftJoin}
      ${feeJoin}
    )`;
  };
  const ctes = [transactionTimeStampCte(innerQuery), cryptoTransferListCte, tokenTransferListCte];

  const fields = [...transactionFullFields, `t.ctr_list AS crypto_transfer_list`, `t.ttr_list AS token_transfer_list`];

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
      account: EntityId.parse(accountId).toString(),
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
      token_id: EntityId.parse(tokenId).toString(),
      account: EntityId.parse(accountId).toString(),
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
    const payerAccountId = EntityId.parse(row.payer_account_id).toString();
    return {
      assessed_custom_fees: createAssessedCustomFeeList(row.assessed_custom_fees),
      bytes: utils.encodeBase64(row.transaction_bytes),
      charged_tx_fee: Number(row.charged_tx_fee),
      consensus_timestamp: utils.nsToSecNs(row.consensus_timestamp),
      entity_id: EntityId.parse(row.entity_id, true).toString(),
      max_fee: utils.getNullableNumber(row.max_fee),
      memo_base64: utils.encodeBase64(row.memo),
      name: TransactionType.getName(row.type),
      nft_transfers: createNftTransferList(row.nft_transfer_list),
      node: EntityId.parse(row.node_account_id, true).toString(),
      nonce: Number(row.nonce),
      parent_consensus_timestamp: utils.nsToSecNs(row.parent_consensus_timestamp),
      result: TransactionResult.getName(row.result),
      scheduled: row.scheduled,
      token_transfers: createTokenTransferList(row.token_transfer_list),
      transaction_hash: utils.encodeBase64(row.transaction_hash),
      transaction_id: utils.createTransactionId(payerAccountId, validStartTimestamp),
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
 * @param {boolean} includeExtraInfo include extra info or not
 * @return {String} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = (innerQuery, order, includeExtraInfo = false) => {
  return `
    ${getSelectClauseWithTransfers(includeExtraInfo, innerQuery, order)}
    FROM transfer_list t
    ORDER BY ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}`;
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
    `JOIN ${Transaction.tableName} AS ${
      Transaction.tableAlias
    } ON ${tableAlias}.${timestampColumn} = ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)}`;
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
    order by ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} ${order}
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
      ON ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} = ctl.consensus_timestamp
      FULL OUTER JOIN (${ttlQuery}) AS ttl
      ON coalesce(${Transaction.getFullName(
        Transaction.CONSENSUS_TIMESTAMP
      )}, ctl.consensus_timestamp) = ttl.consensus_timestamp
      ${namedLimitQuery}`;
  }

  return transactionOnlyQuery;
};

const reqToSql = function (req) {
  // Parse the filter parameters for account-numbers, timestamp, credit/debit, and pagination (limit)
  const parsedQueryParams = req.query;
  const sqlParams = [];
  let [accountQuery, accountParams] = utils.parseAccountIdQueryParam(parsedQueryParams, 'ctl.entity_id');
  accountQuery = utils.convertMySqlStyleQueryToPostgres(accountQuery, sqlParams.length + 1);
  sqlParams.push(...accountParams);
  let [tsQuery, tsParams] = utils.parseTimestampQueryParam(
    parsedQueryParams,
    Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)
  );
  tsQuery = utils.convertMySqlStyleQueryToPostgres(tsQuery, sqlParams.length + 1);
  sqlParams.push(...tsParams);
  let [creditDebitQuery, creditDebitParams] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  creditDebitQuery = utils.convertMySqlStyleQueryToPostgres(creditDebitQuery, sqlParams.length + 1);
  sqlParams.push(...creditDebitParams);
  const resultTypeQuery = utils.parseResultParams(req, Transaction.getFullName(Transaction.RESULT));
  const transactionTypeQuery = utils.parseTransactionTypeParam(parsedQueryParams);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  sqlParams.push(...params);

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
  const {rows, sqlQuery} = await pool.queryQuietly(query.query, query.params);
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
 * Extracts the sql query and params for transactions request by transaction id
 *
 * @param {String} transactionIdStr
 * @param {Array} filters
 * @return {{query: string, params: string[]}}
 */
const extractSqlFromTransactionsByIdRequest = (transactionIdStr, filters) => {
  const transactionId = TransactionId.fromString(transactionIdStr);
  const params = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()];
  const conditions = [
    `${Transaction.getFullName(Transaction.PAYER_ACCOUNT_ID)} = $1`,
    `${Transaction.getFullName(Transaction.VALID_START_NS)} = $2`,
  ];

  let nonce;
  let scheduled;
  for (const filter of filters) {
    // honor the last for both nonce and scheduled
    switch (filter.key) {
      case constants.filterKeys.NONCE:
        nonce = filter.value;
        break;
      case constants.filterKeys.SCHEDULED:
        scheduled = filter.value;
        break;
      default:
        break;
    }
  }

  if (nonce !== undefined) {
    params.push(nonce);
    conditions.push(`${Transaction.getFullName(Transaction.NONCE)} = $${params.length}`);
  }

  if (scheduled !== undefined) {
    params.push(scheduled);
    conditions.push(`${Transaction.getFullName(Transaction.SCHEDULED)} = $${params.length}`);
  }

  const whereClause = buildWhereClause(...conditions);
  const innerQuery = `select ${Transaction.CONSENSUS_TIMESTAMP}
                      from ${Transaction.tableName} ${Transaction.tableAlias}
                      ${whereClause}
                      order by ${Transaction.CONSENSUS_TIMESTAMP} desc`;
  const query = `
    ${getSelectClauseWithTransfers(true, innerQuery)}
    from transfer_list t
    order by ${Transaction.getFullName(Transaction.CONSENSUS_TIMESTAMP)} asc`;

  return {
    query,
    params,
  };
};

/**
 * Handler function for /transactions/:transactionId API.
 * @param {Request} req HTTP request object
 * @return {} None.
 */
const getTransactionsById = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query);
  const {query, params} = extractSqlFromTransactionsByIdRequest(req.params.transactionId, filters);
  if (logger.isTraceEnabled()) {
    logger.trace(`getTransactionsById query: ${query} ${JSON.stringify(params)}`);
  }

  // Execute query
  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    throw new NotFoundError('Not found');
  }

  const transferList = createTransferLists(rows);
  logger.debug(`getTransactionsById returning ${transferList.transactions.length} entries`);
  res.locals[constants.responseDataLabel] = {
    transactions: transferList.transactions,
  };
};

module.exports = {
  getTransactions,
  getTransactionsById,
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
    extractSqlFromTransactionsByIdRequest,
  });
}
