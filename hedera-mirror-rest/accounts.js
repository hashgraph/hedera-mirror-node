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
const config = require('./config.js');
const constants = require('./constants.js');
const EntityId = require('./entityId');
const utils = require('./utils.js');
const transactions = require('./transactions.js');
const {NotFoundError} = require('./errors/notFoundError');
const {InvalidArgumentError} = require('./errors/invalidArgumentError');
const {DbError} = require('./errors/dbError');

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} accRecord Processed account record
 */
const processRow = function (row) {
  let accRecord = {};
  accRecord.balance = {};
  accRecord.account = row.entity_shard + '.' + row.entity_realm + '.' + row.entity_num;
  accRecord.balance.timestamp = row.consensus_timestamp === null ? null : utils.nsToSecNs(row.consensus_timestamp);
  accRecord.balance.balance = row.account_balance === null ? null : Number(row.account_balance);
  accRecord.expiry_timestamp = row.exp_time_ns === null ? null : utils.nsToSecNs(row.exp_time_ns);
  accRecord.auto_renew_period = row.auto_renew_period === null ? null : Number(row.auto_renew_period);
  accRecord.key = row.key === null ? null : utils.encodeKey(row.key);
  accRecord.deleted = row.deleted;

  return accRecord;
};

const getAccountQueryPrefix = function () {
  return `select ab.balance as account_balance,
       ab.consensus_timestamp as consensus_timestamp,
       ${config.shard} as entity_shard,
       coalesce(ab.account_realm_num, e.entity_realm) as entity_realm,
       coalesce(ab.account_num, e.entity_num) as entity_num,
       e.exp_time_ns,
       e.auto_renew_period,
       e.key,
       e.deleted
    from account_balances ab
    full outer join t_entities e on (
        ${config.shard} = e.entity_shard
        and ab.account_realm_num = e.entity_realm
        and ab.account_num =  e.entity_num
        and e.fk_entity_type_id < ${utils.ENTITY_TYPE_FILE}
    )
    where ab.consensus_timestamp = (select max(consensus_timestamp) from account_balances)`;
};

/**
 * Handler function for /accounts API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getAccounts = async (req, res) => {
  // Validate query parameters first
  utils.validateReq(req);

  // Parse the filter parameters for account-numbers, balances, publicKey and pagination

  // Because of the outer join on the 'account_balances ab' and 't_entities e' below, we
  // need to look  for the given account.id in both account_balances and t_entities table and combine with an 'or'
  const [balancesAccountQuery, balancesAccountParams] = utils.parseAccountIdQueryParam(
    req.query,
    'ab.account_realm_num',
    'ab.account_num'
  );
  const [entityAccountQuery, entityAccountParams] = utils.parseAccountIdQueryParam(
    req.query,
    'e.entity_realm',
    ' e.entity_num'
  );
  const accountQuery =
    balancesAccountQuery === ''
      ? ''
      : `(${balancesAccountQuery} or (${entityAccountQuery} and e.fk_entity_type_id < ${utils.ENTITY_TYPE_FILE}))`;
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  let [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'e.ed25519_public_key_hex');
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req, 'asc');

  const entitySql =
    getAccountQueryPrefix() +
    '    and \n' +
    [accountQuery, balanceQuery, pubKeyQuery].map((q) => (q === '' ? '1=1' : q)).join(' and ') +
    ' order by coalesce(ab.account_num, e.entity_num) ' +
    order +
    '\n' +
    query;

  const entityParams = balancesAccountParams
    .concat(entityAccountParams)
    .concat(balanceParams)
    .concat(pubKeyParams)
    .concat(params);

  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entitySql, entityParams);

  if (logger.isTraceEnabled()) {
    logger.trace('getAccounts query: ' + pgEntityQuery + JSON.stringify(entityParams));
  }

  // Execute query
  return pool
    .query(pgEntityQuery, entityParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      let ret = {
        accounts: [],
        links: {
          next: null,
        },
      };

      for (let row of results.rows) {
        ret.accounts.push(processRow(row));
      }

      let anchorAcc = '0.0.0';
      if (ret.accounts.length > 0) {
        anchorAcc = ret.accounts[ret.accounts.length - 1].account;
      }

      ret.links = {
        next: utils.getPaginationLink(req, ret.accounts.length !== limit, 'account.id', anchorAcc, order),
      };

      if (process.env.NODE_ENV === 'test') {
        ret.sqlQuery = results.sqlQuery;
      }

      logger.debug('getAccounts returning ' + ret.accounts.length + ' entries');
      res.locals[constants.responseDataLabel] = ret;
    });
};

/**
 * Handler function for /account/:id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneAccount = async (req, res) => {
  // Parse the filter parameters for account-numbers, balance, and pagination
  const acc = utils.parseEntityId(req.params.id);
  if (acc.num === 0) {
    throw InvalidArgumentError.forParams('account.id');
  }
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 't.consensus_ns');
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);

  let ret = {
    transactions: [],
  };

  // Because of the outer join on the 'account_balances ab' and 't_entities e' below, we
  // need to look  for the given account.id in both account_balances and t_entities table and combine with an 'or'
  const entitySql =
    getAccountQueryPrefix() +
    'and (\n' +
    '    (ab.account_realm_num  =  ? and ab.account_num  =  ?)\n' +
    '    or (e.entity_shard = ? and e.entity_realm = ? and e.entity_num = ?\n' +
    '        and e.fk_entity_type_id < ' +
    utils.ENTITY_TYPE_FILE +
    ')\n' +
    ')\n';

  const entityParams = [acc.realm, acc.num, config.shard, acc.realm, acc.num];
  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entitySql, entityParams);

  if (logger.isTraceEnabled()) {
    logger.trace('getOneAccount entity query: ' + pgEntityQuery + JSON.stringify(entityParams));
  }

  // Execute query & get a promise
  const entityPromise = pool.query(pgEntityQuery, entityParams);

  const [creditDebitQuery] = utils.parseCreditDebitParams(req.query, 'ctl.amount');
  const accountQuery = 'ctl.entity_id = ?';
  const accountParams = [EntityId.of(config.shard, acc.realm, acc.num).getEncodedId()];

  let innerQuery = transactions.getTransactionsInnerQuery(
    accountQuery,
    tsQuery,
    resultTypeQuery,
    query,
    creditDebitQuery,
    order
  );

  const innerParams = accountParams.concat(tsParams).concat(params);

  let transactionsQuery = transactions.getTransactionsOuterQuery(innerQuery, order);

  const pgTransactionsQuery = utils.convertMySqlStyleQueryToPostgres(transactionsQuery, innerParams);

  if (logger.isTraceEnabled()) {
    logger.trace('getOneAccount transactions query: ' + pgTransactionsQuery + JSON.stringify(innerParams));
  }

  // Execute query & get a promise
  const transactionsPromise = pool.query(pgTransactionsQuery, innerParams);

  // After all promises (for all of the above queries) have been resolved...
  return Promise.all([entityPromise, transactionsPromise])
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then(function (values) {
      const entityResults = values[0];
      const transactionsResults = values[1];

      // Process the results of entities query
      if (entityResults.rows.length === 0) {
        throw new NotFoundError();
      }

      if (entityResults.rows.length !== 1) {
        throw new NotFoundError('Error: Could not get entity information');
      }

      for (let row of entityResults.rows) {
        const r = processRow(row);
        for (let key in r) {
          ret[key] = r[key];
        }
      }

      if (process.env.NODE_ENV === 'test') {
        ret.entitySqlQuery = entityResults.sqlQuery;
      }

      // Process the results of t_transactions query
      const transferList = transactions.createTransferLists(transactionsResults.rows);
      ret.transactions = transferList.transactions;
      let anchorSecNs = transferList.anchorSecNs;

      if (process.env.NODE_ENV === 'test') {
        ret.transactionsSqlQuery = transactionsResults.sqlQuery;
      }

      // Pagination links
      ret.links = {
        next: utils.getPaginationLink(req, ret.transactions.length !== limit, 'timestamp', anchorSecNs, order),
      };

      logger.debug('getOneAccount returning ' + ret.transactions.length + ' transactions entries');
      res.locals[constants.responseDataLabel] = ret;
    });
};

module.exports = {
  getAccounts: getAccounts,
  getOneAccount: getOneAccount,
};
