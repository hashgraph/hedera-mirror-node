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
 * @return {Object} Processed account record
 */
const processRow = (row) => {
  const balance =
    row.account_balance === undefined
      ? undefined
      : {
          balance: row.account_balance === null ? null : Number(row.account_balance),
          timestamp: utils.nsToSecNs(row.consensus_timestamp),
          tokens: utils.parseTokenBalances(row.token_balances),
        };
  return {
    account: EntityId.fromEncodedId(row.id).toString(),
    auto_renew_period: row.auto_renew_period === null ? null : Number(row.auto_renew_period),
    balance,
    deleted: row.deleted,
    expiry_timestamp: utils.nsToSecNs(row.expiration_timestamp),
    key: utils.encodeKey(row.key),
    max_automatic_token_associations: row.max_automatic_token_associations,
    memo: row.memo,
    receiver_sig_required: row.receiver_sig_required,
  };
};

/**
 * Creates account query and params from filters with limit and order
 *
 * @param entityAccountQuery optional entity id query
 * @param balancesAccountQuery optional account balance account id query
 * @param balanceQuery optional account balance query
 * @param pubKeyQuery optional entity public key query
 * @param limitAndOrderQuery optional limit and order query
 * @param includeBalance include balance info or not
 * @return {{query: string, params: []}}
 */
const getAccountQuery = (
  entityAccountQuery,
  balancesAccountQuery,
  balanceQuery = {query: '', params: []},
  pubKeyQuery = {query: '', params: []},
  limitAndOrderQuery = {query: '', params: [], order: ''},
  includeBalance = true
) => {
  const entityWhereFilter = [
    `e.type in ('${constants.entityTypes.ACCOUNT}', '${constants.entityTypes.CONTRACT}')`,
    entityAccountQuery.query,
    pubKeyQuery.query,
  ]
    .filter((x) => !!x)
    .join(' and ');
  const {query: limitQuery, params: limitParams, order} = limitAndOrderQuery;
  const entityQuery = `select
      id,
      expiration_timestamp,
      auto_renew_period,
      key,
      deleted,
      type,
      public_key,
      max_automatic_token_associations,
      memo,
      receiver_sig_required
    from entity e
    where ${entityWhereFilter}
    order by e.id ${order || ''}
    ${limitQuery || ''}`;

  if (!includeBalance) {
    return {
      query: entityQuery,
      params: utils.mergeParams(entityAccountQuery.params, pubKeyQuery.params, limitParams),
    };
  }

  const balanceWhereFilter = [
    'ab.consensus_timestamp = (select max(consensus_timestamp) as time_stamp_max from account_balance)',
    balancesAccountQuery.query,
    balanceQuery.query,
  ]
    .filter((x) => !!x)
    .join(' and ');

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
    with balances as (
      select json_agg(
          json_build_object(
            'token_id', tb.token_id::text,
            'balance', tb.balance
          ) order by tb.token_id ${order || ''}
        ) as token_balances,
        ab.balance             as balance,
        ab.consensus_timestamp as consensus_timestamp,
        ab.account_id          as account_id
      from account_balance ab
      left outer join token_balance tb
        on ab.account_id = tb.account_id and ab.consensus_timestamp = tb.consensus_timestamp
      where ${balanceWhereFilter}
      group by ab.consensus_timestamp, ab.account_id, ab.balance
      order by ab.account_id ${order || ''}
      ${limitQuery || ''}
    )
    select balances.balance as account_balance,
      balances.consensus_timestamp as consensus_timestamp,
       coalesce(balances.account_id, e.id) as id,
       e.expiration_timestamp,
       e.auto_renew_period,
       e.key,
       e.deleted,
       e.max_automatic_token_associations,
       e.memo,
       e.receiver_sig_required,
       balances.token_balances
    from balances
    ${joinType} join (${entityQuery}) e
      on e.id = balances.account_id
    order by coalesce(balances.account_id, e.id) ${order || ''}
    ${limitQuery || ''}`;

  const params = utils.mergeParams(
    balancesAccountQuery.params,
    balanceQuery.params,
    limitParams,
    entityAccountQuery.params,
    pubKeyQuery.params,
    limitParams,
    limitParams
  );

  return {query, params};
};

const toQueryObject = (queryAndParams) => {
  return {
    query: queryAndParams[0],
    params: queryAndParams[1],
  };
};

const getBalanceParamValue = (query) => {
  const values = query[constants.filterKeys.BALANCE] || 'true';
  const lastValue = typeof values === 'string' ? values : values[values.length - 1];
  return utils.parseBooleanValue(lastValue);
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
  const includeBalance = getBalanceParamValue(req.query);
  const pubKeyQuery = toQueryObject(utils.parsePublicKeyQueryParam(req.query, 'e.public_key'));
  const limitAndOrderQuery = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.ASC);

  const {query, params} = getAccountQuery(
    entityAccountQuery,
    balancesAccountQuery,
    balanceQuery,
    pubKeyQuery,
    limitAndOrderQuery,
    includeBalance
  );

  const pgQuery = utils.convertMySqlStyleQueryToPostgres(query);

  if (logger.isTraceEnabled()) {
    logger.trace(`getAccounts query: ${pgQuery} ${JSON.stringify(params)}`);
  }

  // Execute query
  // set random_page_cost to 0 to make the cost estimation of using the index on (public_key, index)
  // lower than that of other indexes so pg planner will choose the better index when querying by public key
  const preQueryHint = pubKeyQuery.query !== '' && constants.zeroRandomPageCostQueryHint;
  const result = await pool.queryQuietly(pgQuery, params, preQueryHint);
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
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_timestamp');
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

  const innerParams = utils.mergeParams(accountParams, tsParams, params);
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
