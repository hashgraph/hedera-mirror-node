/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import base32 from './base32';
import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import * as utils from './utils';
import {EntityService} from './service';
import transactions from './transactions';
import {InvalidArgumentError, NotFoundError} from './errors';
import {Entity} from './model';
import balances from './balances';
import {opsMap, parseInteger} from './utils';
import {filterKeys} from './constants';

const {tokenBalance: tokenBalanceResponseLimit} = getResponseLimit();

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} Processed account record
 */
const processRow = (row) => {
  const alias = base32.encode(row.alias);
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
  if (evmAddress === null) {
    if (alias && row.alias.length === constants.EVM_ADDRESS_LENGTH) {
      evmAddress = utils.toHexString(row.alias, true);
    } else {
      evmAddress = entityId.toEvmAddress();
    }
  }

  const stakedToNode = row.staked_node_id !== null && row.staked_node_id !== -1;
  return {
    account: entityId.toString(),
    alias,
    auto_renew_period: row.auto_renew_period,
    balance,
    created_timestamp: utils.nsToSecNs(row.created_timestamp),
    decline_reward: row.decline_reward,
    deleted: row.deleted,
    ethereum_nonce: row.ethereum_nonce,
    evm_address: evmAddress,
    expiry_timestamp: utils.nsToSecNs(
      utils.calculateExpiryTimestamp(row.auto_renew_period, row.created_timestamp, row.expiration_timestamp)
    ),
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
  'e.timestamp_range',
  `(case when es.pending_reward is null then 0
         when e.decline_reward is true or coalesce(e.staked_node_id, -1) = -1 then 0
         when e.stake_period_start >= es.end_stake_period then 0
         else es.pending_reward
    end) as pending_reward`,
].join(',\n');

/**
 * Gets the query for entity fields with hbar and token balance info
 *
 * @param entityBalanceQuery
 * @param entityAccountQuery
 * @param limitAndOrderQuery
 * @param pubKeyQuery
 * @param tokenBalanceQuery
 * @param accountBalanceQuery
 * @param entityStakeQuery
 * @return {{query: string, params: *[]}}
 */
const getEntityBalanceQuery = (
  entityBalanceQuery,
  entityAccountQuery,
  limitAndOrderQuery,
  pubKeyQuery,
  tokenBalanceQuery,
  accountBalanceQuery,
  entityStakeQuery
) => {
  const {query: limitQuery, params: limitParams, order} = limitAndOrderQuery;

  const whereCondition = [entityStakeQuery.query].filter((x) => !!x).join(' and ');
  const entityWhereCondition = [
    `e.type in ('ACCOUNT', 'CONTRACT')`,
    entityBalanceQuery.query,
    entityAccountQuery.query,
    pubKeyQuery.query,
  ]
    .filter((x) => !!x)
    .join(' and ');
  const params = utils.mergeParams(
    [],
    tokenBalanceQuery.params,
    entityBalanceQuery.params,
    entityAccountQuery.params,
    pubKeyQuery.params,
    accountBalanceQuery.params,
    entityStakeQuery.params
  );

  const queries = [
    `with latest_token_balance as (select account_id, balance, token_id, created_timestamp
                                       from token_account
                                       where associated is true)`,
  ];

  const selectFields = [
    entityFields,
    `(select json_agg(jsonb_build_object('token_id', token_id, 'balance', balance)) ::jsonb
                        from (select token_id, balance
                              from latest_token_balance
                              where ${tokenBalanceQuery.query}
                              order by token_id ${order}
        limit ${tokenBalanceQuery.limit}) as account_token_balance) as token_balances`,
  ];

  if (accountBalanceQuery.query) {
    const consensusTimestampSelect = `(case 
            when upper(e.timestamp_range) is null
              then COALESCE(ab.consensus_timestamp, (select max(consensus_end) from record_file)) 
            else COALESCE(ab.consensus_timestamp, upper(e.timestamp_range))
          end) as consensus_timestamp`;
    const balanceSelect = 'COALESCE(ab.balance, e.balance) as balance';

    selectFields.push(consensusTimestampSelect);
    selectFields.push(balanceSelect);

    queries.push(
      `
                select ${selectFields.join(',\n')}
                from (SELECT *
                      from ${Entity.tableName} e
                      where ${entityWhereCondition}
                      UNION ALL
                      SELECT *
                      from ${Entity.historyTableName} e
                      where ${entityWhereCondition}
                      order by ${Entity.TIMESTAMP_RANGE} desc limit 1) e
                         left join entity_stake es on es.id = e.id
                         left join account_balance ab on ${accountBalanceQuery.query} ${
        whereCondition ? 'where' : ''
      } ${whereCondition}
            `
    );
  } else {
    const conditions = [entityWhereCondition, whereCondition].filter((x) => !!x).join(' and ');
    selectFields.push('(select max(consensus_end) from record_file) as consensus_timestamp');
    selectFields.push('e.balance as balance');

    queries.push(` select ${selectFields.join(',\n')}
                       from entity e
                                left join entity_stake es on es.id = e.id
                       where ${conditions}
                       order by e.id ${order} ${limitQuery}`);
    utils.mergeParams(params, limitParams);
  }

  const query = queries.join('\n');

  return {query, params};
};

/**
 * Creates account query and params from filters with limit and order
 *
 * @param entityAccountQuery entity id query
 * @param tokenBalanceQuery token balance query
 * @param accountBalanceQuery optional query for relevant balance file
 * @param entityStakeQuery optional entity stake query
 * @param entityBalanceQuery optional account balance query
 * @param limitAndOrderQuery optional limit and order query
 * @param pubKeyQuery optional entity public key query
 * @param includeBalance include balance info or not
 * @return {{query: string, params: []}}
 */
const getAccountQuery = (
  entityAccountQuery,
  tokenBalanceQuery = {query: 'account_id = e.id', params: [], limit: tokenBalanceResponseLimit.multipleAccounts},
  accountBalanceQuery = {query: '', params: []},
  entityStakeQuery = {query: '', params: []},
  entityBalanceQuery = {query: '', params: []},
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
            from entity e
                     left join entity_stake es on es.id = e.id
            where ${entityCondition}
            order by id ${limitAndOrderQuery.order} ${limitAndOrderQuery.query}`;
    return {
      query: entityOnlyQuery,
      params: utils.mergeParams(entityAccountQuery.params, pubKeyQuery.params, limitAndOrderQuery.params),
    };
  }

  return getEntityBalanceQuery(
    entityBalanceQuery,
    entityAccountQuery,
    limitAndOrderQuery,
    pubKeyQuery,
    tokenBalanceQuery,
    accountBalanceQuery,
    entityStakeQuery
  );
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
    undefined,
    undefined,
    undefined,
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
  // Parse the filter parameters for account-numbers, balance, and pagination
  const filters = utils.buildAndValidateFilters(req.query, acceptedSingleAccountParameters);
  const encodedId = await EntityService.getEncodedId(req.params[constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);

  const transactionsFilter = filters.find((filter) => filter.key === filterKeys.TRANSACTIONS);
  const parsedQueryParams = req.query;
  const timestampFilters = filters.filter((filter) => filter.key === filterKeys.TIMESTAMP);
  const [tsRange, eqValues, neValues] = utils.checkTimestampRange(timestampFilters, false, true, true, false);
  const [transactionTsQuery, transactionTsParams] = utils.buildTimestampQuery(
    tsRange,
    't.consensus_timestamp',
    neValues,
    eqValues
  );

  let paramCount = 0;
  const resultTypeQuery = utils.parseResultParams(req);
  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req);
  const accountIdParams = [encodedId];
  const tokenBalanceQuery = {
    query: `account_id = $${++paramCount}`,
    params: accountIdParams,
    limit: tokenBalanceResponseLimit.singleAccount,
  };
  const entityAccountQuery = {query: `e.id = $${paramCount}`, params: []};
  const entityStakeQuery = {query: `(es.id = $${paramCount} OR es.id IS NULL)`, params: []};

  const accountBalanceQuery = {query: '', params: []};
  if (transactionTsQuery) {
    let tokenBalanceTsQuery = transactionTsQuery
      .replaceAll('t.consensus_timestamp', 'created_timestamp')
      .replaceAll('?', (_) => `$${++paramCount}`);

    tokenBalanceQuery.query += ` and ${tokenBalanceTsQuery} `;
    tokenBalanceQuery.params = tokenBalanceQuery.params.concat(transactionTsParams);

    const [entityTsQuery, entityTsParams] = utils.buildTimestampRangeQuery(
      tsRange,
      Entity.getFullName(Entity.TIMESTAMP_RANGE),
      neValues,
      eqValues
    );
    entityAccountQuery.query += ` and ${entityTsQuery.replaceAll('?', (_) => `$${++paramCount}`)}`;
    entityAccountQuery.params = entityAccountQuery.params.concat(entityTsParams);

    const [balanceFileTsQuery, balanceFileTsParams] = utils.buildTimestampQuery(
      tsRange,
      'consensus_timestamp',
      [],
      eqValues,
      false
    );
    const accountBalanceTs = await balances.getAccountBalanceTimestamp(
      balanceFileTsQuery.replaceAll(opsMap.eq, opsMap.lte),
      balanceFileTsParams,
      order
    );
    //Allow type coercion as the neValues will always be bigint and accountBalanceTs may be a number
    const timestampExcluded = neValues.some((value) => value == accountBalanceTs);

    if (timestampExcluded) {
      throw new NotFoundError('Not found');
    }

    accountBalanceQuery.query = `ab.account_id = e.id and ab.consensus_timestamp = $${++paramCount}`;
    accountBalanceQuery.params = [accountBalanceTs ?? null];
  }

  const {query: entityQuery, params: entityParams} = getAccountQuery(
    entityAccountQuery,
    tokenBalanceQuery,
    accountBalanceQuery,
    entityStakeQuery
  );

  const pgEntityQuery = utils.convertMySqlStyleQueryToPostgres(entityQuery);

  if (logger.isTraceEnabled()) {
    logger.trace(`getOneAccount entity query: ${pgEntityQuery} ${utils.JSONStringify(entityParams)}`);
  }

  // Execute query & get a promise
  const entityPromise = pool.queryQuietly(pgEntityQuery, entityParams);

  const creditDebitQuery = ''; // type=credit|debit is not supported
  const accountQuery = 'ctl.entity_id = ?';
  const accountParams = [encodedId];

  let transactionsPromise;

  if (!transactionsFilter || transactionsFilter.value !== 'false') {
    const transactionTypeQuery = utils.parseTransactionTypeParam(parsedQueryParams);

    const innerQuery = transactions.getTransactionsInnerQuery(
        accountQuery,
        transactionTsQuery,
        resultTypeQuery,
        query,
        creditDebitQuery,
        transactionTypeQuery,
        order
    );

    const innerParams = utils.mergeParams(accountParams, transactionTsParams, params);
    const transactionsQuery = transactions.getTransactionsOuterQuery(innerQuery, order);
    const pgTransactionsQuery = utils.convertMySqlStyleQueryToPostgres(transactionsQuery);

    if (logger.isTraceEnabled()) {
      logger.trace(`getOneAccount transactions query: ${pgTransactionsQuery} ${utils.JSONStringify(innerParams)}`);
    }

    // Execute query & get a promise
    transactionsPromise = pool.queryQuietly(pgTransactionsQuery, innerParams);
  }
  else {
    // Promise that returns empty result
    transactionsPromise = Promise.resolve({rows: []});
  }

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
  constants.filterKeys.ORDER,
]);

const acceptedSingleAccountParameters = new Set([
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.TIMESTAMP,
  constants.filterKeys.TRANSACTION_TYPE,
  constants.filterKeys.TRANSACTIONS
]);

if (utils.isTestEnv()) {
  Object.assign(accounts, {
    getBalanceParamValue,
    processRow,
  });
}

export default accounts;
