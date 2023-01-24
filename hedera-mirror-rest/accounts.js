/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import base32 from './base32';
import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import * as utils from './utils';
import {EntityService} from './service';
import transactions from './transactions';
import {NotFoundError} from './errors';

const {tokenBalance: tokenBalanceResponseLimit} = getResponseLimit();

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} Processed account record
 */
const processRow = (row) => {
  const balance =
    row.balance === undefined
      ? null
      : {
          balance: row.balance,
          timestamp: utils.nsToSecNs(row.consensus_timestamp),
          tokens: utils.parseTokenBalances(row.token_balances),
        };
  const entityId = EntityId.parse(row.id);
  let evmAddress = row.evm_address && utils.toHexString(row.evm_address, true);
  if (evmAddress === null && row.type === constants.entityTypes.CONTRACT) {
    evmAddress = entityId.toEvmAddress();
  }

  const stakedToNode = row.staked_node_id !== null && row.staked_node_id !== -1;
  return {
    account: entityId.toString(),
    alias: base32.encode(row.alias),
    auto_renew_period: row.auto_renew_period,
    balance,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    decline_reward: row.decline_reward,
    deleted: row.deleted,
    ethereum_nonce: row.ethereum_nonce,
    evm_address: evmAddress,
    expiry_timestamp: utils.nsToSecNs(utils.calculateExpiryTimestamp(
      row.auto_renew_period,
      row.created_timestamp,
      row.expiration_timestamp
    )),
    key: utils.encodeKey(row.key),
    max_automatic_token_associations: row.max_automatic_token_associations,
    memo: row.memo,
    pending_reward: row.pending_reward,
    receiver_sig_required: row.receiver_sig_required,
    staked_account_id: EntityId.parse(row.staked_account_id, {isNullable: true}).toString(),
    staked_node_id: stakedToNode ? row.staked_node_id : null,
    stake_period_start:
      stakedToNode && row.stake_period_start !== -1
        ? utils.nsToSecNs(BigInt(row.stake_period_start) * constants.ONE_DAY_IN_NS)
        : null,
  };
};

// 'id' is different for different join types, so will add later when composing the query
const entityFields = [
  'e.alias',
  'e.auto_renew_period',
  'e.created_timestamp',
  'e.decline_reward',
  'e.deleted',
  'e.ethereum_nonce',
  'e.evm_address',
  'e.expiration_timestamp',
  'e.id',
  'e.key',
  'e.max_automatic_token_associations',
  'e.memo',
  'e.receiver_sig_required',
  'e.staked_account_id',
  'e.staked_node_id',
  'e.stake_period_start',
  'e.type',
  `(case when es.pending_reward is null then 0
         when e.decline_reward is true or coalesce(e.staked_node_id, -1) = -1 then 0
         when e.stake_period_start >= es.end_stake_period then 0
         else es.pending_reward
    end) as pending_reward`,
].join(',\n');

/**
 * Gets the query for entity fields with hbar and token balance info
 *
 * @param balanceQuery
 * @param entityAccountQuery
 * @param limitAndOrderQuery
 * @param pubKeyQuery
 * @param tokenBalanceLimit
 * @return {{query: string, params: *[]}}
 */
const getEntityBalanceQuery = (
  balanceQuery,
  entityAccountQuery,
  limitAndOrderQuery,
  pubKeyQuery,
  tokenBalanceLimit
) => {
  const {query: limitQuery, params: limitParams, order} = limitAndOrderQuery;
  const whereCondition = [
    `e.type in ('ACCOUNT', 'CONTRACT')`,
    balanceQuery.query,
    entityAccountQuery.query,
    pubKeyQuery.query,
  ]
    .filter((x) => !!x)
    .join(' and ');
  const params = utils.mergeParams([], balanceQuery.params, entityAccountQuery.params, pubKeyQuery.params, limitParams);
  const query = `
    with latest_token_balance as (
      select account_id, balance, token_id
      from token_account
      where associated is true
    ), latest_record_file as (select max(consensus_end) as consensus_timestamp from record_file)
    select
      ${entityFields},
      latest_record_file.consensus_timestamp,
      balance,
      (
        select json_agg(json_build_object('token_id', token_id, 'balance', balance))
        from (
          select token_id, balance
          from latest_token_balance
          where account_id = e.id
        order by token_id ${order}
        limit ${tokenBalanceLimit}) as account_token_balance
      ) as token_balances
    from entity e left join entity_stake es on es.id = e.id,
      latest_record_file
    where ${whereCondition}
    order by e.id ${order}
    ${limitQuery}`;
  return {query, params};
};

/**
 * Creates account query and params from filters with limit and order
 *
 * @param entityAccountQuery entity id query
 * @param tokenBalanceLimit The max number of token balances for an account
 * @param balanceQuery optional account balance query
 * @param limitAndOrderQuery optional limit and order query
 * @param pubKeyQuery optional entity public key query
 * @param includeBalance include balance info or not
 * @return {{query: string, params: []}}
 */
const getAccountQuery = (
  entityAccountQuery,
  tokenBalanceLimit,
  balanceQuery = {query: '', params: []},
  limitAndOrderQuery = {query: '', params: [], order: constants.orderFilterValues.ASC},
  pubKeyQuery = {query: '', params: []},
  includeBalance = true
) => {
  if (!includeBalance) {
    const entityCondition = [`e.type in ('ACCOUNT', 'CONTRACT')`, entityAccountQuery.query, pubKeyQuery.query]
      .filter((x) => !!x)
      .join(' and ');

    const entityOnlyQuery = `
      select ${entityFields}
      from entity e left join entity_stake es on es.id = e.id
      where ${entityCondition}
      order by id ${limitAndOrderQuery.order}
      ${limitAndOrderQuery.query}`;
    return {
      query: entityOnlyQuery,
      params: utils.mergeParams(entityAccountQuery.params, pubKeyQuery.params, limitAndOrderQuery.params),
    };
  }

  return getEntityBalanceQuery(balanceQuery, entityAccountQuery, limitAndOrderQuery, pubKeyQuery, tokenBalanceLimit);
};

const toQueryObject = (queryAndParams) => {
  return {
    query: queryAndParams[0],
    params: queryAndParams[1],
  };
};

/**
 * Gets the balance param value from the last balance query param. Defaults to true if not found.
 * @param query the http request query object
 * @return {boolean}
 */
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
 * @return {Promise}
 */
const getAccounts = async (req, res) => {
  // Validate query parameters first
  utils.validateReq(req, acceptedAccountsParameters);

  // Parse the filter parameters for account-numbers, balances, publicKey and pagination
  const entityAccountQuery = toQueryObject(utils.parseAccountIdQueryParam(req.query, 'e.id'));
  const balanceQuery = toQueryObject(utils.parseBalanceQueryParam(req.query, 'e.balance'));
  const includeBalance = getBalanceParamValue(req.query);
  const limitAndOrderQuery = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.ASC);
  const pubKeyQuery = toQueryObject(utils.parsePublicKeyQueryParam(req.query, 'public_key'));

  const {query, params} = getAccountQuery(
    entityAccountQuery,
    tokenBalanceResponseLimit.multipleAccounts,
    balanceQuery,
    limitAndOrderQuery,
    pubKeyQuery,
    includeBalance
  );

  const pgQuery = utils.convertMySqlStyleQueryToPostgres(query);

  if (logger.isTraceEnabled()) {
    logger.trace(`getAccounts query: ${pgQuery} ${utils.JSONStringify(params)}`);
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
      {[constants.filterKeys.ACCOUNT_ID]: anchorAcc},
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
 * Handler function for /account/:idOrAliasOrEvmAddress API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @return {Promise}
 */
const getOneAccount = async (req, res) => {
  // Validate query parameters first
  utils.validateReq(req, acceptedSingleAccountParameters);

  const encodedId = await EntityService.getEncodedId(req.params[constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);

  // Parse the filter parameters for account-numbers, balance, and pagination
  const parsedQueryParams = req.query;
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(parsedQueryParams, 't.consensus_timestamp');
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);

  const accountIdParams = [encodedId];
  const {query: entityQuery, params: entityParams} = getAccountQuery(
    {query: 'e.id = ?', params: accountIdParams},
    tokenBalanceResponseLimit.singleAccount
  );
  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entityQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getOneAccount entity query: ${pgEntityQuery} ${utils.JSONStringify(entityParams)}`);
  }

  // Execute query & get a promise
  const entityPromise = pool.queryQuietly(pgEntityQuery, entityParams);

  const [creditDebitQuery] = utils.parseCreditDebitParams(parsedQueryParams, 'ctl.amount');
  const accountQuery = 'ctl.entity_id = ?';
  const accountParams = [encodedId];
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
  const transactionsQuery = transactions.getTransactionsOuterQuery(innerQuery, order);
  const pgTransactionsQuery = utils.convertMySqlStyleQueryToPostgres(transactionsQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getOneAccount transactions query: ${pgTransactionsQuery} ${utils.JSONStringify(innerParams)}`);
  }

  // Execute query & get a promise
  const transactionsPromise = pool.queryQuietly(pgTransactionsQuery, innerParams);

  // After all promises (for all of the above queries) have been resolved...
  const [entityResults, transactionsResults] = await Promise.all([entityPromise, transactionsPromise]);
  // Process the results of entities query
  if (entityResults.rows.length === 0) {
    throw new NotFoundError();
  }

  if (entityResults.rows.length !== 1) {
    throw new NotFoundError('Error: Could not get entity information');
  }

  const ret = processRow(entityResults.rows[0]);

  if (utils.isTestEnv()) {
    ret.entitySqlQuery = entityResults.sqlQuery;
  }

  // Process the results of transaction query
  const transferList = await transactions.createTransferLists(transactionsResults.rows);
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
      {
        [constants.filterKeys.TIMESTAMP]: anchorSecNs,
      },
      order
    ),
  };

  logger.debug(`getOneAccount returning ${ret.transactions.length} transactions entries`);
  res.locals[constants.responseDataLabel] = ret;
};

const accounts = {
  getAccounts,
  getOneAccount,
};

const acceptedAccountsParameters = new Set([
  constants.filterKeys.ACCOUNT_BALANCE,
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.ACCOUNT_PUBLICKEY,
  constants.filterKeys.BALANCE,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER
]);

const acceptedSingleAccountParameters = new Set([
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.TIMESTAMP,
  constants.filterKeys.TRANSACTION_TYPE
]);

if (utils.isTestEnv()) {
  Object.assign(accounts, {
    getBalanceParamValue,
    processRow,
  });
}

export default accounts;
