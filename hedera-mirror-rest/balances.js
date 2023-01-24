/*
 * Copyright (C) 2019-2022 Hedera Hashgraph, LLC
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

import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import * as utils from './utils';

const {tokenBalance: tokenBalanceLimit} = getResponseLimit();

const formatBalancesResult = (req, result, limit, order) => {
  const {rows, sqlQuery} = result;
  const ret = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };

  if (rows.length > 0) {
    ret.timestamp = utils.nsToSecNs(rows[0].consensus_timestamp);
  }

  ret.balances = rows.map((row) => {
    return {
      account: EntityId.parse(row.account_id).toString(),
      balance: row.balance,
      tokens: utils.parseTokenBalances(row.token_balances),
    };
  });

  const anchorAccountId = ret.balances.length > 0 ? ret.balances[ret.balances.length - 1].account : 0;

  // Pagination links
  ret.links = {
    next: utils.getPaginationLink(
      req,
      ret.balances.length !== limit,
      {
        [constants.filterKeys.ACCOUNT_ID]: anchorAccountId,
      },
      order
    ),
  };

  if (utils.isTestEnv()) {
    ret.sqlQuery = sqlQuery;
  }

  return ret;
};

const entityJoin = `join (select id, public_key from entity where type in ('ACCOUNT', 'CONTRACT')) ac on ac.id = ab.account_id`;

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = async (req, res) => {
  utils.validateReq(req, acceptedBalancesParameters);

  // Parse the filter parameters for credit/debit, account-numbers, timestamp and pagination
  const [accountQuery, accountParams] = utils.parseAccountIdQueryParam(req.query, 'ab.account_id');
  // transform the timestamp=xxxx or timestamp=eq:xxxx query in url to 'timestamp <= xxxx' SQL query condition
  const [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'consensus_timestamp', {
    [utils.opsMap.eq]: utils.opsMap.lte,
  });
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'public_key');
  const {query: limitQuery, params, order, limit} = utils.parseLimitAndOrderParams(
    req,
    constants.orderFilterValues.DESC
  );

  let sqlQuery;
  // Use account balance table's timestamp for token balance if there is timestamp filter and obtain the balance from
  // the token balance table; otherwise if there is no timestamp get the latest token balance from the token account
  // table.
  const tokenBalanceSubQuery = tsQuery
    ? getTokenBalanceSubQuery(order, 'ab.consensus_timestamp')
    : getTokenAccountBalanceSubQuery(order);

  // subquery to find the latest snapshot timestamp from the balance history table
  const tsSubQuery = `
    select consensus_timestamp
    from account_balance_file
    ${tsQuery && 'where ' + tsQuery}
    order by consensus_timestamp desc
    limit 1`;

  if (tsQuery) {
    // Only need to join entity if we're selecting on publickey
    const joinEntityClause = pubKeyQuery ? entityJoin : '';
    const whereClause = `
      where ${[`ab.consensus_timestamp = (${tsSubQuery})`, accountQuery, pubKeyQuery, balanceQuery]
        .filter(Boolean)
        .join(' and ')}`;
    sqlQuery = `
      select ab.*, (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab ${joinEntityClause}
      ${whereClause}
      order by ab.account_id ${order}
      ${limitQuery}`;
  } else {
    // use current balance from entity table when there's no timestamp query filter
    const conditions = [accountQuery, pubKeyQuery, balanceQuery].filter(Boolean).join(' and ');
    const whereClause = conditions && `where ${conditions}`;
    sqlQuery = `
      with latest_account_balance as (${tsSubQuery}),
      latest_record_file as (select max(consensus_end) as consensus_timestamp from record_file),
      account_balance as (
        select id as account_id, balance, consensus_timestamp, public_key
        from entity, latest_record_file
        where type in ('ACCOUNT', 'CONTRACT')
      )
      select ab.*, (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab, latest_account_balance
      ${whereClause}
      order by ab.account_id ${order}
      ${limitQuery}`;
  }

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery);
  const sqlParams = utils.mergeParams(tsParams, accountParams, pubKeyParams, balanceParams, params);

  if (logger.isTraceEnabled()) {
    logger.trace(`getBalance query: ${pgSqlQuery} ${utils.JSONStringify(sqlParams)}`);
  }

  // Execute query
  const result = await pool.queryQuietly(pgSqlQuery, sqlParams);
  res.locals[constants.responseDataLabel] = formatBalancesResult(req, result, limit, order);
  logger.debug(`getBalances returning ${result.rows.length} entries`);
};

const getTokenBalanceSubQuery = (order, timestampColumn) => {
  return `
    select json_agg(json_build_object('token_id', token_id, 'balance', balance))
    from (
      select token_id, balance
      from token_balance tb
      where tb.account_id = ab.account_id
        and tb.consensus_timestamp = ${timestampColumn}
      order by token_id ${order}
      limit ${tokenBalanceLimit.multipleAccounts}
    ) as account_token_balance`;
};

const getTokenAccountBalanceSubQuery = (order) => {
  return `
    select json_agg(json_build_object('token_id', token_id, 'balance', balance))
    from (
      select token_id, balance
      from token_account ta
      where ta.account_id = ab.account_id and ta.associated is true
      order by token_id ${order}
      limit ${tokenBalanceLimit.multipleAccounts}
    ) as account_token_balance`;
};

const acceptedBalancesParameters = new Set([
  constants.filterKeys.ACCOUNT_BALANCE,
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.ACCOUNT_PUBLICKEY,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.TIMESTAMP
]);

export default {
  getBalances,
};
