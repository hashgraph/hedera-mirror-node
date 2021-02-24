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

const constants = require('./constants');
const EntityId = require('./entityId');
const utils = require('./utils');
const transactions = require('./transactions');
const {NotFoundError} = require('./errors/notFoundError');
const {DbError} = require('./errors/dbError');

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} accRecord Processed account record
 */
const processRow = (row) => {
  const accRecord = {};
  accRecord.balance = {};
  accRecord.account = EntityId.fromEncodedId(row.entity_id).toString();
  accRecord.balance.timestamp = row.consensus_timestamp === null ? null : utils.nsToSecNs(row.consensus_timestamp);
  accRecord.balance.balance = row.account_balance === null ? null : Number(row.account_balance);
  accRecord.balance.tokens = utils.parseTokenBalances(row.token_balances);
  accRecord.expiry_timestamp = row.exp_time_ns === null ? null : utils.nsToSecNs(row.exp_time_ns);
  accRecord.auto_renew_period = row.auto_renew_period === null ? null : Number(row.auto_renew_period);
  accRecord.key = row.key === null ? null : utils.encodeKey(row.key);
  accRecord.deleted = row.deleted;

  return accRecord;
};

/**
 * Creates account query with optional extra where condition, order clause, and query
 *
 * @param {string} extraWhereCondition optional extra where condition
 * @param {string} orderClause optional order clause
 * @param {string} order optional sorting order
 * @param {string} query optional additional query, e.g., limit clause
 * @return {string} the complete account query
 */
const getAccountQuery = (extraWhereCondition, orderClause, order, query) => {
  // token balances pairs are aggregated as an array of json objects {token_id, balance}
  const whereClause = extraWhereCondition ? `where ${extraWhereCondition}` : '';

  return `
    select ab.balance as account_balance,
       ab.consensus_timestamp as consensus_timestamp,
       coalesce(ab.account_id, e.id) as entity_id,
       e.exp_time_ns,
       e.auto_renew_period,
       e.key,
       e.deleted,
       (
         select
           json_agg(
             json_build_object(
               'token_id', tb.token_id::text,
               'balance', tb.balance
             ) order by tb.token_id ${order || ''}
           )
         from token_balance tb
         where
           ab.consensus_timestamp = tb.consensus_timestamp
           and ab.account_id = tb.account_id
       ) as token_balances
    from account_balance ab
    inner join (select max(consensus_timestamp) as time_stamp_max from account_balance) as abm on ab.consensus_timestamp = abm.time_stamp_max
    full outer join (select id, exp_time_ns, auto_renew_period, key, deleted, fk_entity_type_id, ed25519_public_key_hex from t_entities where fk_entity_type_id < 3) e on (
        ab.account_id = e.id
    )
    ${whereClause}
    ${orderClause || ''}
    ${query || ''}`;
};

/**
 * Handler function for /accounts API.
 *
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise} Promise for PostgreSQL query
 */
const getAccounts = async (req, res) => {
  // Validate query parameters first
  await utils.validateReq(req);

  // Parse the filter parameters for account-numbers, balances, publicKey and pagination
  // Because of the outer join on the 'account_balance ab' and 't_entities e' below, we
  // need to look  for the given account.id in both account_balance and t_entities table and combine with an 'or'
  const [balancesAccountQuery, balancesAccountParams] = utils.parseAccountIdQueryParam(req.query, 'ab.account_id');
  const [entityAccountQuery, entityAccountParams] = utils.parseAccountIdQueryParam(req.query, 'e.id');
  const accountQuery = balancesAccountQuery === '' ? '' : `(${balancesAccountQuery} or ${entityAccountQuery})`;
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'e.ed25519_public_key_hex');
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req, 'asc');

  const entitySql = getAccountQuery(
    `${[accountQuery, balanceQuery, pubKeyQuery].filter((x) => !!x).join(' and ')}`,
    `order by coalesce(ab.account_id, e.id) ${order}`,
    order,
    query
  );

  const entityParams = balancesAccountParams
    .concat(entityAccountParams)
    .concat(balanceParams)
    .concat(pubKeyParams)
    .concat(params);

  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entitySql);

  if (logger.isTraceEnabled()) {
    logger.trace(`getAccounts query: ${pgEntityQuery} ${JSON.stringify(entityParams)}`);
  }

  // Execute query
  const result = await utils.queryQuietly(pgEntityQuery, ...entityParams);
  const ret = {
    accounts: result.rows.map((row) => processRow(row)),
    links: {
      next: null,
    },
  };

  let anchorAcc = '0.0.0';
  if (ret.accounts.length > 0) {
    anchorAcc = ret.accounts[ret.accounts.length - 1].account;
  }

  ret.links = {
    next: utils.getPaginationLink(req, ret.accounts.length !== limit, 'account.id', anchorAcc, order),
  };

  if (utils.isTestEnv()) {
    ret.sqlQuery = result.sqlQuery;
  }

  logger.debug(`getAccounts returning ${ret.accounts.length} entries`);
  res.locals[constants.responseDataLabel] = ret;
};

/**
 * Handler function for /account/:id API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneAccount = async (req, res) => {
  // Parse the filter parameters for account-numbers, balance, and pagination
  const accountId = EntityId.fromString(req.params.id, constants.filterKeys.ACCOUNT_ID).getEncodedId();
  const parsedQueryParams = req.query;
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_ns');
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);

  const ret = {
    transactions: [],
  };

  // Because of the outer join on the 'account_balance ab' and 't_entities e' below, we
  // need to look  for the given account.id in both account_balance and t_entities table and combine with an 'or'
  const entitySql = getAccountQuery(` (ab.account_id = ? or e.id = ?)`);
  const entityParams = [accountId, accountId];
  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entitySql);

  if (logger.isTraceEnabled()) {
    logger.trace(`getOneAccount entity query: ${pgEntityQuery} ${JSON.stringify(entityParams)}`);
  }

  // Execute query & get a promise
  const entityPromise = pool.query(pgEntityQuery, entityParams);

  const [creditDebitQuery] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  const accountQuery = 'ctl.entity_id = ?';
  const accountParams = [accountId];
  const transactionTypeQuery = await utils.getTransactionTypeQuery(parsedQueryParams);

  const innerQuery = transactions.getTransactionsInnerQuery(
    accountQuery,
    tsQuery,
    resultTypeQuery,
    query,
    creditDebitQuery,
    transactionTypeQuery,
    order
  );

  const innerParams = accountParams.concat(tsParams).concat(params);
  const transactionsQuery = await transactions.getTransactionsOuterQuery(innerQuery, order);
  const pgTransactionsQuery = utils.convertMySqlStyleQueryToPostgres(transactionsQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getOneAccount transactions query: ${pgTransactionsQuery} ${JSON.stringify(innerParams)}`);
  }

  // Execute query & get a promise
  const transactionsPromise = pool.query(pgTransactionsQuery, innerParams);

  // After all promises (for all of the above queries) have been resolved...
  return Promise.all([entityPromise, transactionsPromise])
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((values) => {
      const entityResults = values[0];
      const transactionsResults = values[1];

      // Process the results of entities query
      if (entityResults.rows.length === 0) {
        throw new NotFoundError();
      }

      if (entityResults.rows.length !== 1) {
        throw new NotFoundError('Error: Could not get entity information');
      }

      Object.assign(ret, processRow(entityResults.rows[0]));

      if (utils.isTestEnv()) {
        ret.entitySqlQuery = entityResults.sqlQuery;
      }

      // Process the results of transaction query
      const transferList = transactions.createTransferLists(transactionsResults.rows);
      ret.transactions = transferList.transactions;
      const {anchorSecNs} = transferList;

      if (utils.isTestEnv()) {
        ret.transactionsSqlQuery = transactionsResults.sqlQuery;
      }

      // Pagination links
      ret.links = {
        next: utils.getPaginationLink(req, ret.transactions.length !== limit, 'timestamp', anchorSecNs, order),
      };

      logger.debug(`getOneAccount returning ${ret.transactions.length} transactions entries`);
      res.locals[constants.responseDataLabel] = ret;
    });
};

module.exports = {
  getAccounts,
  getOneAccount,
};
