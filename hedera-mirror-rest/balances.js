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

import AccountAlias from './accountAlias.js';
import {getResponseLimit} from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {EntityService} from './service/index.js';
import {EvmAddressType} from './constants';
import {InvalidArgumentError} from './errors/index.js';
import * as math from 'mathjs';
import * as utils from './utils';
import config from './config.js';

const {tokenBalance: tokenBalanceLimit} = getResponseLimit();

// Nanoseconds in a month consisting of 31 days
const NS_IN_MONTH = 2_678_400_000_000_000;

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
    const maxTimestamp = rows
      .map((r) => r.consensus_timestamp)
      .reduce((result, current) => (result > current ? result : current), 0);
    ret.timestamp = utils.nsToSecNs(maxTimestamp);
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
  utils.validateReq(req, acceptedBalancesParameters, balanceFilterValidator);

  // Parse the filter parameters for credit/debit, account-numbers, timestamp and pagination
  const [accountQuery, accountParamsPromise] = parseAccountIdQueryParam(req.query, 'ab.account_id');
  const accountParams = await Promise.all(accountParamsPromise);
  // transform the timestamp=xxxx or timestamp=eq:xxxx query in url to 'timestamp <= xxxx' SQL query condition
  let [tsQuery, tsParams] = utils.parseTimestampQueryParam(req.query, 'consensus_timestamp', {
    [utils.opsMap.eq]: utils.opsMap.lte,
  });
  const [balanceQuery, balanceParams] = utils.parseBalanceQueryParam(req.query, 'ab.balance');
  const [pubKeyQuery, pubKeyParams] = utils.parsePublicKeyQueryParam(req.query, 'public_key');
  const {
    query: limitQuery,
    params,
    order,
    limit,
  } = utils.parseLimitAndOrderParams(req, constants.orderFilterValues.DESC);

  res.locals[constants.responseDataLabel] = {
    timestamp: null,
    balances: [],
    links: {
      next: null,
    },
  };

  let sqlQuery;
  if (tsQuery) {
    if (!config.query.deduplicateBalances) {
      const balanceTimestamp = await getAccountBalanceTimestamp(tsQuery, tsParams);
      if (balanceTimestamp === undefined) {
        return;
      }
      sqlQuery = await getBalancesQuery(accountQuery, balanceQuery, limitQuery, order, pubKeyQuery);
      tsParams = [balanceTimestamp, balanceTimestamp];
    } else {
      [sqlQuery, tsParams] = await getDeduplicateBalancesQuery(
        accountQuery,
        balanceQuery,
        limitQuery,
        order,
        pubKeyQuery,
        tsParams,
        tsQuery
      );
    }
  } else {
    // use current balance from entity table when there's no timestamp query filter
    const conditions = [accountQuery, pubKeyQuery, balanceQuery].filter(Boolean).join(' and ');
    const whereClause = conditions && `where ${conditions}`;
    const tokenBalanceSubQuery = getTokenAccountBalanceSubQuery(order);
    sqlQuery = `
      with account_balance as (
        select id as account_id, balance, balance_timestamp as consensus_timestamp, public_key
        from entity
        where type in ('ACCOUNT', 'CONTRACT')
      )
      select ab.*, (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab
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

const getAccountBalanceTimestamp = async (tsQuery, tsParams, order = 'desc') => {
  // Add the treasury account to the query as it will always be in the balance snapshot and account_id is the primary key of the table thus it will speed up queries on v2
  tsQuery = tsQuery ? tsQuery.concat(' and account_id = ?') : ' account_id = ?';
  tsParams.push('2');

  const query = `
    select consensus_timestamp
    from account_balance
    where ${tsQuery}
    order by consensus_timestamp ${order}
    limit 1`;

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(query);
  const {rows} = await pool.queryQuietly(pgSqlQuery, tsParams);
  return rows[0]?.consensus_timestamp;
};

const getBalancesQuery = async (accountQuery, balanceQuery, limitQuery, order, pubKeyQuery) => {
  // Only need to join entity if we're selecting on publickey
  const joinEntityClause = pubKeyQuery ? entityJoin : '';
  const tokenBalanceSubQuery = getTokenBalanceSubQuery(order);
  const whereClause = `
      where ${[`ab.consensus_timestamp = ?`, accountQuery, pubKeyQuery, balanceQuery].filter(Boolean).join(' and ')}`;
  return `
      select ab.*, (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab
      ${joinEntityClause}
      ${whereClause}
      order by ab.account_id ${order}
      ${limitQuery}`;
};

const getDeduplicateBalancesQuery = async (
  accountQuery,
  balanceQuery,
  limitQuery,
  order,
  pubKeyQuery,
  tsParams,
  tsQuery
) => {
  let consensusTsQuery = `ab.consensus_timestamp = ?`;
  if (!accountQuery) {
    // Use a single balanceTimestamp to improve query performance
    const balanceTimestamp = await getAccountBalanceTimestamp(tsQuery, tsParams, order);
    tsParams = [balanceTimestamp, balanceTimestamp];
  } else {
    // For queries with account(s) specified, a timestamp range including the lower bound of the partition increases performance
    consensusTsQuery = `ab.consensus_timestamp >= ? and ab.consensus_timestamp <= ?`;
    // Get partition lower bound by subtracting 31 days from the timestamp
    // Queries with this lower bound will hit two partitions at most
    const upperBound = tsParams[0];
    let oneMonthTimestamp = math.subtract(upperBound, NS_IN_MONTH);
    const lowerPartitionBound = oneMonthTimestamp >= 0 ? oneMonthTimestamp : 0;
    tsParams = [lowerPartitionBound, upperBound, lowerPartitionBound, upperBound];
  }

  // Only need to join entity if we're selecting on publickey
  const joinEntityClause = pubKeyQuery ? entityJoin : '';
  const tokenBalanceSubQuery = getTokenBalanceSubQuery(order, accountQuery);
  const whereClause = `
      where ${[consensusTsQuery, accountQuery, pubKeyQuery, balanceQuery].filter(Boolean).join(' and ')}`;
  const sqlQuery = `
      select distinct on (account_id) ab.*, (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab
      ${joinEntityClause}
      ${whereClause}
      order by ab.account_id ${order}, ab.consensus_timestamp desc
      ${limitQuery}`;
  return [sqlQuery, tsParams];
};

const getTokenBalanceSubQuery = (order, accountQuery) => {
  const consensusTsQuery = accountQuery
    ? 'tb.consensus_timestamp >= ? and tb.consensus_timestamp <= ?'
    : 'tb.consensus_timestamp = ?';
  return `
    select json_agg(json_build_object('token_id', token_id, 'balance', balance))
    from (
      select distinct on (token_id) token_id, balance
      from token_balance tb
      where tb.account_id = ab.account_id
        and ${consensusTsQuery}
      order by token_id ${order}, consensus_timestamp desc
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

const parseAccountIdQueryParam = (query, columnName) => {
  let evmAliasAddressCount = 0;
  return utils.parseParams(
    query[constants.filterKeys.ACCOUNT_ID],
    (value) => {
      if (EntityId.isValidEntityId(value, false)) {
        return EntityId.parse(value).getEncodedId();
      }
      if (EntityId.isValidEvmAddress(value, EvmAddressType.NO_SHARD_REALM) && ++evmAliasAddressCount === 1) {
        return EntityService.getEncodedId(value);
      }
      if (AccountAlias.isValid(value, true) && ++evmAliasAddressCount === 1) {
        return EntityService.getAccountIdFromAlias(AccountAlias.fromString(value));
      }

      if (evmAliasAddressCount > 1) {
        throw new InvalidArgumentError({
          message: `Invalid parameter: ${constants.filterKeys.ACCOUNT_ID}`,
          detail: `Only one EVM address or alias is allowed.`,
        });
      }
      throw new InvalidArgumentError(`Invalid parameter: ${constants.filterKeys.ACCOUNT_ID}`);
    },
    (op, value) => {
      if (evmAliasAddressCount > 0 && op !== utils.opsMap.eq) {
        throw new InvalidArgumentError({
          message: `Invalid parameter: ${constants.filterKeys.ACCOUNT_ID}`,
          detail: `EVM address or alias only supports equals operator`,
        });
      }

      return Array.isArray(value)
        ? [`${columnName} IN (?`.concat(', ?'.repeat(value.length - 1)).concat(')'), value]
        : [`${columnName}${op}?`, [value]];
    },
    true
  );
};

const balanceFilterValidator = (param, op, val) => {
  return param === constants.filterKeys.ACCOUNT_ID
    ? utils.validateOpAndValue(op, val)
    : utils.filterValidityChecks(param, op, val);
};

const acceptedBalancesParameters = new Set([
  constants.filterKeys.ACCOUNT_BALANCE,
  constants.filterKeys.ACCOUNT_ID,
  constants.filterKeys.ACCOUNT_PUBLICKEY,
  constants.filterKeys.LIMIT,
  constants.filterKeys.ORDER,
  constants.filterKeys.TIMESTAMP,
]);

export default {
  getBalances,
  getAccountBalanceTimestamp,
};
