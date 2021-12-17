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

const {getAccountContractUnionQueryWithOrder} = require('./accountContract');
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
      ? null
      : {
          balance: row.account_balance === null ? null : Number(row.account_balance),
          timestamp: utils.nsToSecNs(row.consensus_timestamp),
          tokens: utils.parseTokenBalances(row.token_balances),
        };
  return {
    account: EntityId.parse(row.id).toString(),
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

// 'id' is different for different join types, so will add later when composing the query
const entityFields = [
  'auto_renew_period',
  'deleted',
  'expiration_timestamp',
  'key',
  'max_automatic_token_associations',
  'memo',
  'receiver_sig_required',
  'type',
].join(',');
const entityAndBalanceFields = [
  entityFields,
  // fields from account_balance
  'consensus_timestamp',
  'balance account_balance',
].join(',');
const latestBalanceFilter = 'ab.consensus_timestamp = (select max(consensus_timestamp) from account_balance)';

/**
 * Gets the query for entity fields with hbar balance info for the full outer join case.
 *
 * @param accountContractQuery
 * @param balanceAccountQuery
 * @param entityAccountQuery
 * @param entityWhereClause
 * @param limitParams
 * @param limitQuery
 * @param order
 * @return {{query: string, params: *[]}}
 */
const getEntityBalanceFullOuterJoinQuery = (
  accountContractQuery,
  balanceAccountQuery,
  entityAccountQuery,
  entityWhereClause,
  limitParams,
  limitQuery,
  order
) => {
  const balanceWhereCondition = [latestBalanceFilter, balanceAccountQuery.query].filter((x) => !!x).join(' and ');
  const entityIdField = 'coalesce(ab.account_id, e.id)';
  const params = utils.mergeParams(
    entityAccountQuery.params,
    limitParams,
    balanceAccountQuery.params,
    limitParams,
    limitParams
  );
  const entityBalanceQuery = `
      select ${entityIdField} id,${entityAndBalanceFields}
      from (
        select id,${entityFields}
        from (${accountContractQuery}) e
        ${entityWhereClause}
        order by id ${order}
        ${limitQuery}
      ) e
      full outer join (
        select *
        from account_balance ab
        where ${balanceWhereCondition}
        order by account_id ${order}
        ${limitQuery}
      ) ab
        on ab.account_id = e.id
      order by ${entityIdField} ${order}
      ${limitQuery}
    `;
  return {query: entityBalanceQuery, params};
};

/**
 * Gets the query for entity fields with hbar balance info for inner, left outer, and right outer join cases.
 *
 * @param accountContractQuery
 * @param balanceAccountQuery
 * @param balanceQuery
 * @param entityAccountQuery
 * @param limitParams
 * @param limitQuery
 * @param order
 * @param pubKeyQuery
 * @return {{query: string, params: *[]}}
 */
const getEntityBalanceQuery = (
  accountContractQuery,
  balanceAccountQuery,
  balanceQuery,
  entityAccountQuery,
  limitParams,
  limitQuery,
  order,
  pubKeyQuery
) => {
  const balanceJoinConditions = ['ab.account_id = e.id'];
  const balanceWhereConditions = [balanceQuery.query];
  let entityIdField = 'id'; // use 'id' from account / contract for inner and left outer joins
  let joinType = '';
  let params;

  // use different joins for different combinations of balance query and public key query. The where conditions
  // and the corresponding params differ too. The entity id conditions only have to apply to one table: for inner
  // and left outer joins, apply them to the account / contract table; for right outer join, apply them to the
  // account_balance table
  if (balanceQuery.query && pubKeyQuery.query) {
    joinType = 'inner';
    balanceJoinConditions.push(latestBalanceFilter);
    params = [entityAccountQuery.params, pubKeyQuery.params, balanceQuery.params];
  } else if (pubKeyQuery.query) {
    joinType = 'left outer';
    balanceJoinConditions.push(latestBalanceFilter);
    params = [entityAccountQuery.params, pubKeyQuery.params];
  } else if (balanceQuery.query) {
    entityIdField = 'account_id';
    balanceWhereConditions.push(balanceAccountQuery.query, latestBalanceFilter);
    joinType = 'right outer';
    // no entity id filter needed for account / contract for right outer join
    params = [balanceQuery.params, balanceAccountQuery.params];
  }

  const whereCondition = [
    // no entity id filter needed for account / contract for right outer join
    joinType !== 'right outer' ? entityAccountQuery.query : '',
    pubKeyQuery.query,
    ...balanceWhereConditions,
  ]
    .filter((x) => !!x)
    .join(' and ');
  const whereClause = `where ${whereCondition}`;

  params = utils.mergeParams(...params, limitParams);
  const entityBalanceQuery = `
      select ${entityIdField} id,${entityAndBalanceFields}
      from (${accountContractQuery}) e
      ${joinType} join account_balance ab
        on ${balanceJoinConditions.join(' and ')}
      ${whereClause}
      order by ${entityIdField} ${order}
      ${limitQuery}
    `;
  return {query: entityBalanceQuery, params};
};

/**
 * Creates account query and params from filters with limit and order
 *
 * @param entityAccountQuery optional entity id query
 * @param balanceAccountQuery optional account balance account id query
 * @param balanceQuery optional account balance query
 * @param limitAndOrderQuery optional limit and order query
 * @param pubKeyQuery optional entity public key query
 * @param includeBalance include balance info or not
 * @return {{query: string, params: []}}
 */
const getAccountQuery = (
  entityAccountQuery,
  balanceAccountQuery,
  balanceQuery = {query: '', params: []},
  limitAndOrderQuery = {query: '', params: [], order: constants.orderFilterValues.ASC},
  pubKeyQuery = {query: '', params: []},
  includeBalance = true
) => {
  const entityWhereCondition = [entityAccountQuery.query, pubKeyQuery.query].filter((x) => !!x).join(' and ');
  const entityWhereClause = entityWhereCondition && `where ${entityWhereCondition}`;
  const limitParams = limitAndOrderQuery.params;
  const limitQuery = limitAndOrderQuery.query || '';
  const order = limitAndOrderQuery.order || constants.orderFilterValues.ASC;
  const accountContractQuery = getAccountContractUnionQueryWithOrder({field: 'id', order});

  if (!includeBalance) {
    const entityOnlyQuery = `
      select id,${entityFields}
      from (${accountContractQuery}) account_contract
      ${entityWhereClause}
      order by id ${order}
      ${limitQuery}`;
    return {
      query: entityOnlyQuery,
      params: utils.mergeParams(entityAccountQuery.params, pubKeyQuery.params, limitParams),
    };
  }

  const {query: entityBalanceQuery, params} =
    balanceQuery.query === '' && pubKeyQuery.query === ''
      ? // use full outer join when no balance query and public key query
        getEntityBalanceFullOuterJoinQuery(
          accountContractQuery,
          balanceAccountQuery,
          entityAccountQuery,
          entityWhereClause,
          limitParams,
          limitQuery,
          order
        )
      : getEntityBalanceQuery(
          accountContractQuery,
          balanceAccountQuery,
          balanceQuery,
          entityAccountQuery,
          limitParams,
          limitQuery,
          order,
          pubKeyQuery
        );

  const query = `
    with entity_balance as (${entityBalanceQuery}),
    token_balance as (
      select
        tb.account_id,
        jsonb_agg(
          jsonb_build_object(
            'token_id', tb.token_id::text,
            'balance', tb.balance
          ) order by tb.token_id ${order}
        ) as token_balances
      from token_balance tb
      join entity_balance eb
        on tb.account_id = eb.id and tb.consensus_timestamp = eb.consensus_timestamp
      group by tb.account_id
    )
    select eb.*,tb.token_balances
    from entity_balance eb
    left join token_balance tb
      on tb.account_id = eb.id
    order by eb.id ${order}
  `;

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
  const entityAccountQuery = toQueryObject(utils.parseAccountIdQueryParam(req.query, 'id'));
  const balanceAccountQuery = toQueryObject(utils.parseAccountIdQueryParam(req.query, 'ab.account_id'));
  const balanceQuery = toQueryObject(utils.parseBalanceQueryParam(req.query, 'ab.balance'));
  const includeBalance = getBalanceParamValue(req.query);
  const limitAndOrderQuery = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.ASC);
  const pubKeyQuery = toQueryObject(utils.parsePublicKeyQueryParam(req.query, 'public_key'));

  const {query, params} = getAccountQuery(
    entityAccountQuery,
    balanceAccountQuery,
    balanceQuery,
    limitAndOrderQuery,
    pubKeyQuery,
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
  const accountId = EntityId.parse(req.params.accountId, constants.filterKeys.ACCOUNT_ID).getEncodedId();
  const parsedQueryParams = req.query;
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_timestamp');
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);

  const ret = {
    transactions: [],
  };

  const accountIdParams = [accountId];
  const {query: entityQuery, params: entityParams} = getAccountQuery(
    {query: 'id = ?', params: accountIdParams},
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
  const transactionTypeQuery = utils.parseTransactionTypeParam(parsedQueryParams);

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

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    processRow,
  });
}
