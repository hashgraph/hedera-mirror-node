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
const utils = require('./utils.js');
const config = require('./config.js');
const constants = require('./constants.js');
const {DbError} = require('./errors/dbError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {NotFoundError} = require('./errors/notFoundError');

const selectClause = `SELECT etrans.entity_shard, etrans.entity_realm, etrans.entity_num,
       t.memo,
       t.consensus_ns,
       t.valid_start_ns,
       coalesce(ttr.result, 'UNKNOWN') AS result,
       coalesce(ttt.name, 'UNKNOWN') AS name,
       t.fk_node_acc_id,
       enode.entity_realm AS node_realm,
       enode.entity_num AS node_num,
       ctl.realm_num AS account_realm,
       ctl.entity_num AS account_num,
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
 * @param {Array} arr REST API return array
 * @return {Array} arr Updated REST API return array
 */
const createTransferLists = function (rows, arr) {
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
      transactions[row.consensus_ns]['node'] = config.shard + '.' + row.node_realm + '.' + row.node_num;

      // Construct a transaction id using format: shard.realm.num-sssssssssss-nnnnnnnnn
      transactions[row.consensus_ns]['transaction_id'] = utils.createTransactionId(
        config.shard,
        row.entity_realm,
        row.entity_num,
        validStartTimestamp
      );

      transactions[row.consensus_ns].transfers = [];
    }

    transactions[row.consensus_ns].transfers.push({
      account: config.shard + '.' + row.account_realm + '.' + row.account_num,
      amount: Number(row.amount),
    });
  }

  const anchorSecNs = rows.length > 0 ? utils.nsToSecNs(rows[rows.length - 1].consensus_ns) : 0;

  arr.transactions = Object.values(transactions);

  return {
    ret: arr,
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
       JOIN t_transactions t ON tlist.consensus_timestamp = t.consensus_ns
       LEFT OUTER JOIN t_transaction_results ttr ON ttr.proto_id = t.result
       JOIN t_entities enode ON enode.id = t.fk_node_acc_id
       JOIN t_entities etrans ON etrans.id = t.fk_payer_acc_id
       LEFT OUTER JOIN t_transaction_types ttt ON ttt.proto_id = t.type
       JOIN t_cryptotransferlists ctl ON tlist.consensus_timestamp = ctl.consensus_timestamp
     ORDER BY t.consensus_ns ${order} , account_num ASC, amount ASC`
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
 * @param {String} creditDebit Either 'credit', 'debit' to filter based on credit or debit transactions
 * @param {String} order Sorting order
 * @return {String} innerQuery SQL query that filters transactions based on various types of queries
 */
const getTransactionsInnerQuery = function (accountQuery, tsQuery, resultTypeQuery, limitQuery, creditDebit, order) {
  let innerQuery = `
       SELECT DISTINCT ctl.consensus_timestamp
       FROM t_cryptotransferlists ctl
       JOIN t_transactions t ON t.consensus_ns = ctl.consensus_timestamp
       WHERE `;
  if (accountQuery) {
    innerQuery += accountQuery; // Max limit on the inner query.
  } else {
    innerQuery += '1=1\n';
  }
  innerQuery += ' AND ' + [tsQuery, resultTypeQuery].map((q) => (q === '' ? '1=1' : q)).join(' AND ');
  if ('credit' === creditDebit) {
    innerQuery += ' AND ctl.amount > 0 ';
  } else if ('debit' === creditDebit) {
    innerQuery += ' AND ctl.amount < 0 ';
  }
  innerQuery += ' ORDER BY ctl.consensus_timestamp ' + order + '\n' + limitQuery;
  return innerQuery;
};

const reqToSql = function (req) {
  // Parse the filter parameters for credit/debit, account-numbers,
  // timestamp, and pagination (limit)
  const creditDebit = utils.parseCreditDebitParams(req);

  let [accountQuery, accountParams] = utils.parseParams(
    req,
    'account.id',
    [
      {
        realm: 'realm_num',
        num: 'entity_num',
      },
    ],
    'entityId'
  );

  const [tsQuery, tsParams] = utils.parseParams(req, 'timestamp', ['t.consensus_ns'], 'timestamp_ns');

  const resultTypeQuery = utils.parseResultParams(req);

  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);

  let sqlParams = accountParams.concat(tsParams).concat(params);

  let innerQuery = getTransactionsInnerQuery(accountQuery, tsQuery, resultTypeQuery, query, creditDebit, order);
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
      let ret = {
        transactions: [],
        links: {
          next: null,
        },
      };

      const tl = createTransferLists(results.rows, ret);
      ret = tl.ret;
      let anchorSecNs = tl.anchorSecNs;

      ret.links = {
        next: utils.getPaginationLink(
          req,
          ret.transactions.length !== query.limit,
          'timestamp',
          anchorSecNs,
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
  // The transaction id is in the format of 'shard.realm.num-ssssssssss-nnnnnnnnn'
  // convert it in shard, realm, num and nanoseconds parameters
  let txIdMatches = req.params.id.match(/(\d+)\.(\d+)\.(\d+)-(\d{10})-(\d{9})/);
  if (txIdMatches === null || txIdMatches.length != 6) {
    logger.info(`getOneTransaction: Invalid transaction id ${req.params.id}`);
    let message =
      'Invalid Transaction id. Please use "shard.realm.num-ssssssssss-nnnnnnnnn" ' +
      'format where ssss are 10 digits seconds and nnn are 9 digits nanoseconds';

    throw new InvalidArgumentError(message);
  }
  const sqlParams = [txIdMatches[1], txIdMatches[2], txIdMatches[3], txIdMatches[4] + '' + txIdMatches[5]];

  let sqlQuery =
    selectClause +
    `FROM t_transactions t
       JOIN t_transaction_results ttr ON ttr.proto_id = t.result
       JOIN t_entities enode ON enode.id = t.fk_node_acc_id
       JOIN t_entities etrans ON etrans.id = t.fk_payer_acc_id
       JOIN t_transaction_types ttt ON ttt.proto_id = t.type
       JOIN t_cryptotransferlists ctl ON  ctl.consensus_timestamp = t.consensus_ns
     WHERE etrans.entity_shard = ?
       AND  etrans.entity_realm = ?
       AND  etrans.entity_num = ?
       AND  t.valid_start_ns = ?
     ORDER BY consensus_ns ASC, account_num ASC, amount ASC`; // In case of duplicate transactions, only the first succeeds

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
      let ret = {
        transactions: [],
      };

      logger.debug('# rows returned: ' + results.rows.length);
      const tl = createTransferLists(results.rows, ret);
      ret = tl.ret;

      if (ret.transactions.length === 0) {
        throw new NotFoundError('Not found');
      }

      logger.debug('getOneTransaction returning ' + ret.transactions.length + ' entries');
      res.locals[constants.responseDataLabel] = ret;
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
