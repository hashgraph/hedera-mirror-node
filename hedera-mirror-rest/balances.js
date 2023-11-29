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
    const tsQueryResult = await getTsQuery(tsQuery, tsParams);
    if (!tsQueryResult.query) {
      return;
    }

    [sqlQuery, tsParams] = await getBalancesQuery(
      accountQuery,
      balanceQuery,
      limitQuery,
      order,
      pubKeyQuery,
      tsQueryResult.params,
      tsQueryResult.query
    );
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

  const result = await pool.queryQuietly(pgSqlQuery, sqlParams);
  res.locals[constants.responseDataLabel] = formatBalancesResult(req, result, limit, order);
  logger.debug(`getBalances returning ${result.rows.length} entries`);
};

/**
 * Gets the balance snapshot timestamp range given tsQuery and tsParams. If a balance snapshot timestamp satisfying the
 * conditions is found, a range with lower bound equal to the beginning of the month of the balance snapshot timestamp,
 * and upper bound equal to the balance snapshot timestamp is returned. Otherwise, an empty object is returned.
 *
 * @param tsQuery - the timestamp query built by parsing the timestamp filter from the request url
 * @param tsParams - the timestamp parameters for the timestamp query
 * @returns {Promise<{lower: BigInt|Number, upper: BigInt|Number}|{}>}
 */
const getAccountBalanceTimestampRange = async (tsQuery, tsParams) => {
  const {lowerBound, upperBound, neParams} = getOptimizedTimestampRange(tsQuery, tsParams);
  if (lowerBound === undefined) {
    return {};
  }

  // Add the treasury account to the query as it will always be in the balance snapshot and account_id is the first
  // column of the primary key
  let condition = 'account_id = 2 and consensus_timestamp >= $1 and consensus_timestamp <= $2';
  const params = [lowerBound, upperBound];
  if (neParams.length) {
    condition += ' and not consensus_timestamp = any ($3)';
    params.push(neParams);
  }

  const query = `
    select consensus_timestamp
    from account_balance
    where ${condition}
    order by consensus_timestamp desc
    limit 1`;

  if (logger.isTraceEnabled()) {
    logger.trace(`getAccountBalanceTimestampRange query: ${query} ${utils.JSONStringify(params)}`);
  }

  const {rows} = await pool.queryQuietly(query, params);
  if (rows.length === 0) {
    return {};
  }

  const upper = rows[0].consensus_timestamp;
  const lower = utils.getFirstDayOfMonth(upper);
  return {lower, upper};
};

const getBalancesQuery = async (accountQuery, balanceQuery, limitQuery, order, pubKeyQuery, tsParams, tsQuery) => {
  // Only need to join entity if we're selecting on publickey
  const joinEntityClause = pubKeyQuery ? entityJoin : '';
  const tokenBalanceSubQuery = getTokenBalanceSubQuery(order, tsQuery);
  const whereClause = `
      where ${[tsQuery, accountQuery, pubKeyQuery, balanceQuery].filter(Boolean).join(' and ')}`;
  const sqlQuery = `
      select distinct on (account_id) ab.*, (${tokenBalanceSubQuery}) as token_balances
      from account_balance ab
      ${joinEntityClause}
      ${whereClause}
      order by ab.account_id ${order}, ab.consensus_timestamp desc
      ${limitQuery}`;
  return [sqlQuery, tsParams];
};

/**
 * Optimize the timestamp range based on the query built from the timestamp parameters in the request URL. With the
 * assumption that the importer has synced up and the balance data close to NOW in wall clock should exist in db,
 * adjust the lower bound and upper bound timestamps so the range will cover at most two monthly time partitions.
 *
 * @param tsQuery
 * @param tsParams
 * @returns {{}|{upperBound: bigint, lowerBound: bigint, neParams: *[]}}
 */
const getOptimizedTimestampRange = (tsQuery, tsParams) => {
  let lowerBound = 0n;
  const neParams = [];
  let upperBound = constants.MAX_LONG;

  // Find the lower bound and the upper bound from tsParams if present
  tsQuery
    .split('?')
    .filter(Boolean)
    .forEach((query, index) => {
      const value = BigInt(tsParams[index]);
      // eq operator has already been converted to the lte operator
      if (query.includes(utils.opsMap.lte)) {
        // lte operator includes the lt operator, so this clause must before the lt clause
        upperBound = upperBound < value ? upperBound : value;
      } else if (query.includes(utils.opsMap.lt)) {
        // Convert lt to lte to simplify query
        const ltValue = value - 1n;
        upperBound = upperBound < ltValue ? upperBound : ltValue;
      } else if (query.includes(utils.opsMap.gte)) {
        // gte operator includes the gt operator, so this clause must come before the gt clause
        lowerBound = lowerBound > value ? lowerBound : value;
      } else if (query.includes(utils.opsMap.gt)) {
        // Convert gt to gte to simplify query
        const gtValue = value + 1n;
        lowerBound = lowerBound > gtValue ? lowerBound : gtValue;
      } else if (query.includes(utils.opsMap.ne)) {
        neParams.push(value);
      }
    });

  if (lowerBound > upperBound) {
    return {};
  }

  const nowInNs = BigInt(Date.now()) * constants.NANOSECONDS_PER_MILLISECOND;
  if (upperBound !== constants.MAX_LONG) {
    if (lowerBound === 0n) {
      // There is an upper bound but no lowerBound
      const firstDayOfUpperBoundMonth = utils.getFirstDayOfMonth(upperBound);
      const firstDayOfCurrentMonth = utils.getFirstDayOfMonth(nowInNs);
      lowerBound =
        firstDayOfUpperBoundMonth < firstDayOfCurrentMonth
          ? // if the upper bound is in a month prior to the current month, the lower bound is the beginning of
            // the month prior to the month the upper bound is in. For example if the upper bound month is November
            // 2022, the lower bound timestamp is the beginning of October 2022.
            utils.getFirstDayOfMonth(firstDayOfUpperBoundMonth - 1n)
          : // If upper bound is in the current or later month the lower bound is the beginning of the month before
            // current month. For example if the current month is November, the lower bound timestamp is
            // the beginning of October.
            utils.getFirstDayOfMonth(firstDayOfCurrentMonth - 1n);
    }
  } else {
    // There is only a lower bound, set the lower bound to the maximum of (gteParam, the first day of the month
    // before the current month)
    const firstDayOfCurrentMonth = utils.getFirstDayOfMonth(nowInNs);
    const firstDayOfPreviousMonth = utils.getFirstDayOfMonth(firstDayOfCurrentMonth - 1n);
    lowerBound = lowerBound > firstDayOfPreviousMonth ? lowerBound : firstDayOfPreviousMonth;
  }

  return {lowerBound, upperBound, neParams};
};

const getTsQuery = async (tsQuery, tsParams) => {
  const {lower, upper} = await getAccountBalanceTimestampRange(tsQuery, tsParams);
  if (lower === undefined) {
    return {};
  }

  const query = 'ab.consensus_timestamp >= ? and ab.consensus_timestamp <= ?';
  // Double the params to account for the query values for account_balance and token_balance together
  const params = [lower, upper, lower, upper];
  return {query, params};
};

const getTokenBalanceSubQuery = (order, consensusTsQuery) => {
  consensusTsQuery = consensusTsQuery.replaceAll('ab.', 'tb.');
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

const balances = {
  getAccountBalanceTimestampRange,
  getBalances,
};

if (utils.isTestEnv()) {
  Object.assign(balances, {
    getOptimizedTimestampRange,
  });
}

export default balances;
