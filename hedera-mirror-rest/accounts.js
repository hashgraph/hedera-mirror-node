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
  accRecord.account = EntityId.fromEncodedId(row.entity_id).toString();
  accRecord.auto_renew_period = row.auto_renew_period === null ? null : Number(row.auto_renew_period);
  accRecord.balance = {};
  accRecord.balance.balance = row.account_balance === null ? null : Number(row.account_balance);
  accRecord.balance.timestamp = row.consensus_timestamp === null ? null : utils.nsToSecNs(row.consensus_timestamp);
  accRecord.balance.tokens = utils.parseTokenBalances(row.token_balances);
  accRecord.deleted = row.deleted;
  accRecord.expiry_timestamp = row.expiration_timestamp === null ? null : utils.nsToSecNs(row.expiration_timestamp);
  accRecord.key = row.key === null ? null : utils.encodeKey(row.key);
  accRecord.memo = row.memo;
  accRecord.receiver_sig_required = row.receiver_sig_required;

  logger.info(`*** getAccounts processRow row.memo: ${JSON.stringify(row.memo)}`);
  logger.info(`*** getAccounts processRow accRecord.memo: ${JSON.stringify(accRecord.memo)}`);
  logger.info(`*** getAccounts processRow row.receiver_sig_required: ${JSON.stringify(row.receiver_sig_required)}`);
  logger.info(
    `*** getAccounts processRow accRecord.receiver_sig_required: ${JSON.stringify(accRecord.receiver_sig_required)}`
  );
  return accRecord;
};

/**
 * Creates account query and params from filters with limit and order
 *
 * @param entityAccountQuery - optional entity id query
 * @param balancesAccountQuery - optional account balance account id query
 * @param balanceQuery - optional account balance query
 * @param pubKeyQuery - optional entity public key query
 * @param limitAndOrderQuery - optional limit and order query
 * @return {{query: string, params}}
 */
const getAccountQuery = (
  entityAccountQuery,
  balancesAccountQuery,
  balanceQuery = {query: '', params: []},
  pubKeyQuery = {query: '', params: []},
  limitAndOrderQuery = {query: '', params: [], order: ''}
) => {
  const entityWhereFilter = ['type < 3', entityAccountQuery.query, pubKeyQuery.query].filter((x) => !!x).join(' and ');
  const balanceWhereFilter = [
    'ab.consensus_timestamp = (select max(consensus_timestamp) as time_stamp_max from account_balance)',
    balancesAccountQuery.query,
    balanceQuery.query,
  ]
    .filter((x) => !!x)
    .join(' and ');
  const {query: limitQuery, params: limitParams, order} = limitAndOrderQuery;

  // balanceQuery and pubKeyQuery are applied in the two sub queries; depending on the presence, use different joins
  let joinType = 'full outer';
  if (balanceQuery.query && pubKeyQuery.query) {
    joinType = 'inner';
  } else if (balanceQuery.query) {
    joinType = 'left outer';
  } else if (pubKeyQuery.query) {
    joinType = 'right outer';
  }

  const query = `
    select ab.balance as account_balance,
       ab.consensus_timestamp as consensus_timestamp,
       coalesce(ab.account_id, e.id) as entity_id,
       e.expiration_timestamp,
       e.auto_renew_period,
       e.key,
       e.deleted,
       e.memo,
       e.receiver_sig_required,
       (
         select json_agg(
           json_build_object(
             'token_id', token_id::text,
             'balance', balance
           ) order by token_id ${order || ''}
         )
         from token_balance
         where account_id = ab.account_id and consensus_timestamp = ab.consensus_timestamp
       ) token_balances
    from (
      select *
      from account_balance ab
      where ${balanceWhereFilter}
      order by ab.account_id ${order || ''}
      ${limitQuery || ''}
    ) ab
    ${joinType} join (
      select id, expiration_timestamp, auto_renew_period, key, deleted, type, public_key, memo, receiver_sig_required
      from entity e
      where ${entityWhereFilter}
      order by e.id ${order || ''}
      ${limitQuery || ''}
    ) e on e.id = ab.account_id
    order by coalesce(ab.account_id, e.id) ${order || ''}
    ${limitQuery || ''}`;

  const params = balancesAccountQuery.params
    .concat(balanceQuery.params)
    .concat(limitParams)
    .concat(entityAccountQuery.params)
    .concat(pubKeyQuery.params)
    .concat(limitParams)
    .concat(limitParams);

  return {query, params};
};

const toQueryObject = (queryAndParams) => {
  return {
    query: queryAndParams[0],
    params: queryAndParams[1],
  };
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
  utils.validateReq(req);

  // Parse the filter parameters for account-numbers, balances, publicKey and pagination
  const entityAccountQuery = toQueryObject(utils.parseAccountIdQueryParam(req.query, 'e.id'));
  const balancesAccountQuery = toQueryObject(utils.parseAccountIdQueryParam(req.query, 'ab.account_id'));
  const balanceQuery = toQueryObject(utils.parseBalanceQueryParam(req.query, 'ab.balance'));
  const pubKeyQuery = toQueryObject(utils.parsePublicKeyQueryParam(req.query, 'e.public_key'));
  const limitAndOrderQuery = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.ASC);

  const {query, params} = getAccountQuery(
    entityAccountQuery,
    balancesAccountQuery,
    balanceQuery,
    pubKeyQuery,
    limitAndOrderQuery
  );

  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(query);

  if (logger.isTraceEnabled()) {
    logger.trace(`getAccounts query: ${pgEntityQuery} ${JSON.stringify(params)}`);
  }

  // Execute query
  const result = await pool.queryQuietly(pgEntityQuery, ...params);
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
    next: utils.getPaginationLink(
      req,
      ret.accounts.length !== limitAndOrderQuery.limit,
      constants.filterKeys.ACCOUNT_ID,
      anchorAcc,
      limitAndOrderQuery.order
    ),
  };

  if (utils.isTestEnv()) {
    ret.sqlQuery = result.sqlQuery;
  }

  logger.debug(`getAccounts returning ${ret.accounts.length} entries`);
  res.locals[constants.responseDataLabel] = ret;
};

/**
 * Handler function for /account/:accountId API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {} None.
 */
const getOneAccount = async (req, res) => {
  // Validate query parameters first
  utils.validateReq(req);

  // Parse the filter parameters for account-numbers, balance, and pagination
  const accountId = EntityId.fromString(req.params.accountId, constants.filterKeys.ACCOUNT_ID).getEncodedId();
  const parsedQueryParams = req.query;
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_ns');
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);

  const ret = {
    transactions: [],
  };

  const accountIdParams = [accountId];
  const {query: entityQuery, params: entityParams} = getAccountQuery(
    {query: 'e.id = ?', params: accountIdParams},
    {query: 'ab.account_id = ?', params: accountIdParams}
  );
  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entityQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getOneAccount entity query: ${pgEntityQuery} ${JSON.stringify(entityParams)}`);
  }

  // Execute query & get a promise
  const entityPromise = pool.query(pgEntityQuery, entityParams);

  const [creditDebitQuery] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  const accountQuery = 'ctl.entity_id = ?';
  const accountParams = [accountId];
  const transactionTypeQuery = utils.getTransactionTypeQuery(parsedQueryParams);

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
        next: utils.getPaginationLink(
          req,
          ret.transactions.length !== limit,
          constants.filterKeys.TIMESTAMP,
          anchorSecNs,
          order
        ),
      };

      logger.debug(`getOneAccount returning ${ret.transactions.length} transactions entries`);
      res.locals[constants.responseDataLabel] = ret;
    });
};

module.exports = {
  getAccounts,
  getOneAccount,
};
