/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

/**
 * Create transferlists from the output of SQL queries. The SQL table has different
 * rows for each of the transfers in a single transaction. This function collates all
 * transfers into a single list.
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @param {Array} arr REST API return array
 * @return {Array} arr Updated REST API return array
 */
const createTransferLists = function(rows, arr) {
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
      amount: Number(row.amount)
    });
  }

  const anchorSecNs = rows.length > 0 ? utils.nsToSecNs(rows[rows.length - 1].consensus_ns) : 0;

  arr.transactions = Object.values(transactions);

  return {
    ret: arr,
    anchorSecNs: anchorSecNs
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
const getTransactionsOuterQuery = function(innerQuery, order) {
  let outerQuery =
    'select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n' +
    '   , t.memo\n' +
    '	, t.consensus_ns\n' +
    '   , valid_start_ns\n' +
    "   , coalesce(ttr.result, 'UNKNOWN') as result\n" +
    "   , coalesce(ttt.name, 'UNKNOWN') as name\n" +
    '   , t.fk_node_acc_id\n' +
    '   , enode.entity_realm as node_realm\n' +
    '   , enode.entity_num as node_num\n' +
    '   , ctl.realm_num as account_realm\n' +
    '   , ctl.entity_num as account_num\n' +
    '   , amount\n' +
    '   , t.charged_tx_fee\n' +
    '   , t.valid_duration_seconds\n' +
    '   , t.max_fee\n' +
    ' from (' +
    innerQuery +
    ') as tlist\n' +
    '   join t_transactions t on tlist.consensus_timestamp = t.consensus_ns\n' +
    '   left outer join t_transaction_results ttr on ttr.proto_id = t.result\n' +
    '   join t_entities enode on enode.id = t.fk_node_acc_id\n' +
    '   join t_entities etrans on etrans.id = t.fk_payer_acc_id\n' +
    '   left outer join t_transaction_types ttt on ttt.proto_id = t.type\n' +
    '   left outer join t_cryptotransferlists ctl on  tlist.consensus_timestamp = ctl.consensus_timestamp\n' +
    '   order by t.consensus_ns ' +
    order +
    ', account_num asc, amount asc ' +
    '\n';
  return outerQuery;
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
const getTransactionsInnerQuery = function(accountQuery, tsQuery, resultTypeQuery, limitQuery, creditDebit, order) {
  let innerQuery =
    '      select distinct ctl.consensus_timestamp\n' +
    '       from t_cryptotransferlists ctl\n' +
    '       join t_transactions t on t.consensus_ns = ctl.consensus_timestamp\n' +
    '       where ';
  if (accountQuery) {
    innerQuery += accountQuery; // Max limit on the inner query.
  } else {
    innerQuery += '1=1\n';
  }
  innerQuery += 'and ' + [tsQuery, resultTypeQuery].map(q => (q === '' ? '1=1' : q)).join(' and ');
  if ('credit' === creditDebit) {
    innerQuery += ' and ctl.amount > 0 ';
  } else if ('debit' === creditDebit) {
    innerQuery += ' and ctl.amount < 0 ';
  }
  innerQuery += '   order by ctl.consensus_timestamp ' + order + '\n' + limitQuery;
  return innerQuery;
};

const reqToSql = function(req) {
  // Parse the filter parameters for credit/debit, account-numbers,
  // timestamp, and pagination (limit)
  const creditDebit = utils.parseCreditDebitParams(req);

  let [accountQuery, accountParams] = utils.parseParams(
    req,
    'account.id',
    [
      {
        realm: 'realm_num',
        num: 'entity_num'
      }
    ],
    'entityId'
  );

  const [tsQuery, tsParams] = utils.parseParams(req, 'timestamp', ['t.consensus_ns'], 'timestamp_ns');

  const resultTypeQuery = utils.parseResultParams(req);

  const {limitQuery, limitParams, order, limit} = utils.parseLimitAndOrderParams(req);

  let sqlParams = accountParams.concat(tsParams).concat(limitParams);

  let innerQuery = getTransactionsInnerQuery(accountQuery, tsQuery, resultTypeQuery, limitQuery, creditDebit, order);
  let sqlQuery = getTransactionsOuterQuery(innerQuery, order);

  return {
    limit: limit,
    query: utils.convertMySqlStyleQueryToPostgress(sqlQuery, sqlParams),
    order: order,
    params: sqlParams
  };
};

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTransactions = function(req) {
  // Validate query parameters first
  const valid = utils.validateReq(req);
  if (!valid.isValid) {
    return new Promise((resolve, reject) => {
      resolve(valid);
    });
  }

  let query = reqToSql(req);

  logger.debug('getTransactions query: ' + query.query + JSON.stringify(query.params));

  // Execute query
  return pool.query(query.query, query.params).then(results => {
    let ret = {
      transactions: [],
      links: {
        next: null
      }
    };

    const tl = createTransferLists(results.rows, ret);
    ret = tl.ret;
    let anchorSecNs = tl.anchorSecNs;

    ret.links = {
      next: utils.getPaginationLink(req, ret.transactions.length !== query.limit, 'timestamp', anchorSecNs, query.order)
    };

    if (process.env.NODE_ENV === 'test') {
      ret.sqlQuery = results.sqlQuery;
    }

    logger.debug('getTransactions returning ' + ret.transactions.length + ' entries');

    return {
      code: utils.httpStatusCodes.OK,
      contents: ret
    };
  });
};

/**
 * Handler function for /transactions/:transaction_id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneTransaction = function(req, res) {
  logger.debug('--------------------  getOneTransaction --------------------');
  logger.debug('Client: [' + req.ip + '] URL: ' + req.originalUrl);

  // The transaction id is in the format of 'shard.realm.num-ssssssssss-nnnnnnnnn'
  // convert it in shard, realm, num and nanoseconds parameters
  let txIdMatches = req.params.id.match(/(\d+)\.(\d+)\.(\d+)-(\d{10})-(\d{9})/);
  if (txIdMatches === null || txIdMatches.length != 6) {
    logger.info(`getOneTransaction: Invalid transaction id ${req.params.id}`);
    res
      .status(404)
      .send(
        'Invalid Transaction id. Please use "shard.realm.num-ssssssssss.nnnnnnnnn" ' +
          'format where ssss are 10 digits seconds and nnn are 9 digits nanoseconds'
      );
    return;
  }
  const sqlParams = [txIdMatches[1], txIdMatches[2], txIdMatches[3], txIdMatches[4] + '' + txIdMatches[5]];

  let sqlQuery =
    'select etrans.entity_shard,  etrans.entity_realm, etrans.entity_num\n' +
    '   , t.memo\n' +
    '	, t.consensus_ns\n' +
    '   , valid_start_ns\n' +
    "   , coalesce(ttr.result, 'UNKNOWN') as result\n" +
    "   , coalesce(ttt.name, 'UNKNOWN') as type\n" +
    '   , t.fk_node_acc_id\n' +
    '   , enode.entity_shard as node_shard\n' +
    '   , enode.entity_realm as node_realm\n' +
    '   , enode.entity_num as node_num\n' +
    '   , account_id\n' +
    '   , ctl.realm_num as account_realm\n' +
    '   , ctl.entity_num as account_num\n' +
    '   , amount\n' +
    '   , charged_tx_fee\n' +
    '   , valid_duration_seconds\n' +
    '   , max_fee\n' +
    ' from t_transactions t\n' +
    '   join t_transaction_results ttr on ttr.proto_id = t.result\n' +
    '   join t_entities enode on enode.id = t.fk_node_acc_id\n' +
    '   join t_entities etrans on etrans.id = t.fk_payer_acc_id\n' +
    '   join t_transaction_types ttt on ttt.proto_id = t.type\n' +
    '   join t_cryptotransferlists ctl on  ctl.consensus_timestamp = t.consensus_ns\n' +
    ' where etrans.entity_shard = ?\n' +
    '   and  etrans.entity_realm = ?\n' +
    '   and  etrans.entity_num = ?\n' +
    '   and  t.valid_start_ns = ?\n' +
    ' order by consensus_ns asc'; // In case of duplicate transactions, only the first succeeds

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgress(sqlQuery, sqlParams);

  logger.debug('getOneTransaction query: ' + pgSqlQuery + JSON.stringify(sqlParams));

  // Execute query
  pool.query(pgSqlQuery, sqlParams, (error, results) => {
    let ret = {
      transactions: []
    };

    if (error) {
      logger.error('getOneTransaction error: ' + JSON.stringify(error, Object.getOwnPropertyNames(error)));
      res.status(404).send('Not found');
      return;
    }

    logger.debug('# rows returned: ' + results.rows.length);
    const tl = createTransferLists(results.rows, ret);
    ret = tl.ret;

    if (ret.transactions.length === 0) {
      res.status(404).send('Not found');
      return;
    }

    if (process.env.NODE_ENV === 'test') {
      ret.sqlQuery = results.sqlQuery;
    }

    logger.debug('getOneTransaction returning ' + ret.transactions.length + ' entries');
    res.json(ret);
  });
};

module.exports = {
  getTransactions: getTransactions,
  getOneTransaction: getOneTransaction,
  createTransferLists: createTransferLists,
  reqToSql: reqToSql,
  getTransactionsInnerQuery: getTransactionsInnerQuery,
  getTransactionsOuterQuery: getTransactionsOuterQuery
};
