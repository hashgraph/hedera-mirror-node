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
const utils = require('./utils.js');
const {DbError} = require('./errors/dbError');

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = async (req, res) => {
  utils.validateReq(req);

  // Parse the filter parameters for credit/debit, account-numbers,
  // timestamp and pagination
  let [accountQuery, accountParams] = utils.parseParams(
    req,
    'account.id',
    [{shard: '0', realm: 'ab.account_realm_num', num: 'ab.account_num'}],
    'entityId'
  );

  // if the request has a timestamp=xxxx or timestamp=eq:xxxxx, then
  // modify that to be timestamp <= xxxx, so we return the latest balances
  // as of the user-supplied timestamp.
  if ('timestamp' in req.query) {
    const pattern = /^(eq:)?(\d*\.?\d*)$/;
    const replacement = 'lte:$2';
    if (Array.isArray(req.query.timestamp)) {
      for (let index = 0; index < req.query.timestamp.length; index++) {
        req.query.timestamp[index] = req.query.timestamp[index].replace(pattern, replacement);
      }
    } else {
      req.query.timestamp = req.query.timestamp.replace(pattern, replacement);
    }
  }

  let [tsQuery, tsParams] = utils.parseParams(req, 'timestamp', ['ab.consensus_timestamp'], 'timestamp_ns');

  let [balanceQuery, balanceParams] = utils.parseParams(req, 'account.balance', ['ab.balance'], 'balance');

  let [pubKeyQuery, pubKeyParams] = utils.parseParams(
    req,
    'account.publickey',
    ['e.ed25519_public_key_hex'],
    'publickey',
    (s) => {
      return s.toLowerCase();
    }
  );
  let joinEntities = '' !== pubKeyQuery; // Only need to join t_entites if we're selecting on publickey.

  const {query, params, order, limit} = utils.parseLimitAndOrderParams(req, 'desc');

  // Use the inner query to find the latest snapshot timestamp from the balance history table
  let innerQuery =
    'select consensus_timestamp from account_balances ab\n' +
    ' where\n' +
    (tsQuery === '' ? '1=1' : tsQuery) +
    '\n' +
    'order by consensus_timestamp desc limit 1';

  let sqlQuery =
    'select ab.consensus_timestamp,\n' +
    'ab.account_realm_num as realm_num, ab.account_num as entity_num, ab.balance\n' +
    ' from account_balances ab\n';
  if (joinEntities) {
    sqlQuery +=
      ' join t_entities e\n' +
      ' on e.entity_realm = ab.account_realm_num\n' +
      ' and e.entity_num = ab.account_num\n' +
      ' and e.entity_shard = ' +
      config.shard +
      '\n' +
      ' and e.fk_entity_type_id < ' +
      utils.ENTITY_TYPE_FILE +
      '\n';
  }
  sqlQuery +=
    ' where ' +
    ' consensus_timestamp = (' +
    innerQuery +
    ')\n' +
    ' and\n' +
    [accountQuery, pubKeyQuery, balanceQuery].map((q) => (q === '' ? '1=1' : q)).join(' and ') +
    ' order by consensus_timestamp desc, ' +
    ['account_realm_num', 'account_num'].map((q) => q + ' ' + order).join(',') +
    ' ' +
    query;

  let sqlParams = tsParams.concat(accountParams).concat(pubKeyParams).concat(balanceParams).concat(params);

  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);

  logger.trace('getBalance query: ' + pgSqlQuery + JSON.stringify(sqlParams));

  // Execute query
  return pool
    .query(pgSqlQuery, sqlParams)
    .catch((err) => {
      throw new DbError(err.message);
    })
    .then((results) => {
      let ret = {
        timestamp: null,
        balances: [],
        links: {
          next: null,
        },
      };

      function accountIdFromRow(row) {
        return `${config.shard}.${row.realm_num}.${row.entity_num}`;
      }

      // Go through all results, and collect them by seconds.
      // These need to be returned as an array (and not an object) because
      // per ECMA ES2015, the order of keys parsable as integers are implicitly
      // sorted (i.e. insert order is not maintained)
      // let retObj = {}
      for (let row of results.rows) {
        let ns = utils.nsToSecNs(row.consensus_timestamp);
        row.account = accountIdFromRow(row);

        if (ret.timestamp === null) {
          ret.timestamp = ns;
        }
        ret.balances.push({
          account: row.account,
          balance: Number(row.balance),
        });
      }

      const anchorAccountId = results.rows.length > 0 ? results.rows[results.rows.length - 1].account : 0;

      // Pagination links
      ret.links = {
        next: utils.getPaginationLink(req, ret.balances.length !== limit, 'account.id', anchorAccountId, order),
      };

      if (process.env.NODE_ENV === 'test') {
        ret.sqlQuery = results.sqlQuery;
      }

      logger.debug('getBalances returning ' + ret.balances.length + ' entries');

      res.locals[constants.responseDataLabel] = ret;
    });
};

module.exports = {
  getBalances: getBalances,
};
