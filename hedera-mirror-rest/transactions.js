/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
const {DbError} = require('./errors/dbError');
const {NotFoundError} = require('./errors/notFoundError');

const selectClause = `SELECT
       t.payer_account_id,
       t.memo,
       t.consensus_ns,
       t.valid_start_ns,
       coalesce(ttr.result, 'UNKNOWN') AS result,
       coalesce(ttt.name, 'UNKNOWN') AS name,
       t.node_account_id,
       ctl.entity_id as ctl_entity_id,
       ctl.amount,
       t.charged_tx_fee,
       t.valid_duration_seconds,
       t.max_fee,
       t.transaction_hash\n`;

/**
 * Create transferlists from the output of SQL queries. The SQL table has different
 * rows for each of the transfers in a single transaction. This function collates all
 * transfers into a single list.
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @return {{anchorSecNs: (String|number), transactions: {}}}
 */
const createTransferLists = function (rows) {
  // If the transaction has a transferlist (i.e. list of individual trasnfers, it
  // will show up as separate rows. Combine those into a single transferlist for
  // a given consensus_ns (Note that there could be two records for the same
  // transaction-id where one would pass and others could fail as duplicates)
  let transactions = {};

  for (let row of rows) {
    if (!(row.consensus_ns in transactions)) {
      var validStartTimestamp = row.valid_start_ns;
      transactions[row.consensus_ns] = {};
      transactions[row.consensus_ns]['consensus_timestamp'] = utils.nsToSecNs(row['consensus_ns']);
      transactions[row.consensus_ns]['transaction_hash'] = utils.encodeBase64(row['transaction_hash']);
      transactions[row.consensus_ns]['valid_start_timestamp'] = utils.nsToSecNs(validStartTimestamp);
      transactions[row.consensus_ns]['charged_tx_fee'] = Number(row['charged_tx_fee']);
      transactions[row.consensus_ns]['id'] = row['id'];
      transactions[row.consensus_ns]['memo_base64'] = utils.encodeBase64(row['memo']);
      transactions[row.consensus_ns]['result'] = row['result'];
      transactions[row.consensus_ns]['name'] = row['name'];
      transactions[row.consensus_ns]['max_fee'] = utils.getNullableNumber(row['max_fee']);
      transactions[row.consensus_ns]['valid_duration_seconds'] = utils.getNullableNumber(row['valid_duration_seconds']);
      transactions[row.consensus_ns]['node'] = EntityId.fromEncodedId(row['node_account_id']).toString();
      transactions[row.consensus_ns]['transaction_id'] = utils.createTransactionId(
        EntityId.fromEncodedId(row['payer_account_id']).toString(),
        validStartTimestamp
      );
      transactions[row.consensus_ns].transfers = [];
    }

    transactions[row.consensus_ns].transfers.push({
      account: EntityId.fromEncodedId(row['ctl_entity_id']).toString(),
      amount: Number(row.amount),
    });
  }

  const anchorSecNs = rows.length > 0 ? utils.nsToSecNs(rows[rows.length - 1].consensus_ns) : 0;

  return {
    transactions: Object.values(transactions),
    anchorSecNs: anchorSecNs,
  };
};

/**
 * Cryptotransfer transactions queries are orgnaized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function returns the outer query base on the consensus_timestamps list returned by the inner query.
 * Also see: getTransactionsInnerQuery function
 * @param {String} innerQuery SQL query that provides a list of unique transactions that match the query criteria
 * @param {String} order Sorting order
 * @return {String} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = function (innerQuery, order) {
  return (
    selectClause +
    `FROM ( ${innerQuery} ) AS tlist
       JOIN transaction t ON tlist.consensus_timestamp = t.consensus_ns
       LEFT OUTER JOIN t_transaction_results ttr ON ttr.proto_id = t.result
       LEFT OUTER JOIN t_transaction_types ttt ON ttt.proto_id = t.type
       JOIN crypto_transfer ctl ON tlist.consensus_timestamp = ctl.consensus_timestamp
     ORDER BY t.consensus_ns ${order} , ctl_entity_id ASC, amount ASC`
  );
};

/**
 * Cryptotransfer transactions queries are orgnaized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function forms the inner query base based on all the query criteria specified in the REST URL
 * It selects a list of unique transactions (consensus_timestamps).
 * Also see: getTransactionsOuterQuery function
 * @param {String} accountQuery SQL query that filters based on the account ids
 * @param {String} tsQuery SQL query that filters based on the timestamps
 * @param {String} resultTypeQuery SQL query that filters based on the result types
 * @param {String} limitQuery SQL query that limits the number of unique transactions returned
 * @param {String} creditDebitQuery SQL query that filters for credit/debit transactions
 * @param {String} order Sorting order
 * @return {String} innerQuery SQL query that filters transactions based on various types of queries
 */
const getTransactionsInnerQuery = function (
  accountQuery,
  tsQuery,
  resultTypeQuery,
  limitQuery,
  creditDebitQuery,
  order
) {
  let whereClause = [accountQuery, tsQuery, resultTypeQuery, creditDebitQuery].filter((q) => q !== '').join(' AND ');
  whereClause = whereClause === '' ? '' : `WHERE ${whereClause}`;
  return `
    SELECT DISTINCT ctl.consensus_timestamp
    FROM crypto_transfer ctl
      JOIN transaction t ON t.consensus_ns = ctl.consensus_timestamp
    ${whereClause}
    ORDER BY ctl.consensus_timestamp ${order}
    ${limitQuery}`;
};

const reqToSql = function (req) {
  // Parse the filter parameters for credit/debit, account-numbers, timestamp, and pagination (limit)
  const [creditDebitQuery] = utils.parseCreditDebitParams(req.query, 'ctl.amount');
  let [accountQuery, accountParams] = utils.parseAccountIdQueryParamAsEncoded(req.query, 'ctl.entity_id');
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 't.consensus_ns');
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  let sqlParams = accountParams.concat(tsParams).concat(params);

  let innerQuery = getTransactionsInnerQuery(accountQuery, tsQuery, resultTypeQuery, query, creditDebitQuery, order);
  let sqlQuery = getTransactionsOuterQuery(innerQuery, order);

  return {
    limit: limit,
    query: utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams),
    order: order,
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

  let query = reqToSql(req);
  if (logger.isTraceEnabled()) {
    logger.trace('getTransactions query: ' + query.query + JSON.stringify(query.params));
  }

  // Execute query
  return pool
    .query(query.query, query.params)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      const transferList = createTransferLists(results.rows);
      let ret = {
        transactions: transferList.transactions,
      };

      ret.links = {
        next: utils.getPaginationLink(
          req,
          ret.transactions.length !== query.limit,
          'timestamp',
          transferList.anchorSecNs,
          query.order
        ),
      };

      if (process.env.NODE_ENV === 'test') {
        ret.sqlQuery = results.sqlQuery;
      }

      logger.debug('getTransactions returning ' + ret.transactions.length + ' entries');
      res.locals[constants.responseDataLabel] = ret;
    });
};

/**
 * Handler function for /transactions/:transaction_id API.
 * @param {Request} req HTTP request object
 * @return {} None.
 */
const getOneTransaction = async (req, res) => {
  const transactionId = TransactionId.fromString(req.params.id);
  const sqlParams = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()];

  let sqlQuery =
    selectClause +
    `FROM transaction t
       JOIN t_transaction_results ttr ON ttr.proto_id = t.result
       JOIN t_transaction_types ttt ON ttt.proto_id = t.type
       JOIN crypto_transfer ctl ON  ctl.consensus_timestamp = t.consensus_ns
     WHERE t.payer_account_id = ?
       AND  t.valid_start_ns = ?
     ORDER BY consensus_ns ASC, ctl_entity_id ASC, amount ASC`; // In case of duplicate transactions, only the first succeeds

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace('getOneTransaction query: ' + pgSqlQuery + JSON.stringify(sqlParams));
  }

  // Execute query
  return pool
    .query(pgSqlQuery, sqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      const transferList = createTransferLists(results.rows);
      if (transferList.transactions.length === 0) {
        throw new NotFoundError('Not found');
      }
      logger.debug('getOneTransaction returning ' + transferList.transactions.length + ' entries');
      res.locals[constants.responseDataLabel] = {
        transactions: transferList.transactions,
      };
    });
};

module.exports = {
  getTransactions: getTransactions,
  getOneTransaction: getOneTransaction,
  createTransferLists: createTransferLists,
  reqToSql: reqToSql,
  getTransactionsInnerQuery: getTransactionsInnerQuery,
  getTransactionsOuterQuery: getTransactionsOuterQuery,
};
