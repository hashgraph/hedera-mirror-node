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

const math = require('mathjs');
const EntityId = require('../entityId');
const testutils = require('./testutils');
const {maxLimit} = require('../config');

/**
 * This is a mock database for unit testing.
 * It captures SQL query that would have been sent to the database, and parses the
 * relevant parameters such as timestamp, account.id from it.
 */
class Pool {
  /**
   * Constructor for the mock DB pool
   * @param {Object} dbParams Unused - db parameters
   * @return {} None
   */
  constructor(dbParams) {
    // Some defaults for generating test/dummy data
    this.TEST_DATA_MAX_ACCOUNTS = 1000000;
    this.TEST_DATA_MAX_HISTORY = 60 * 60; // seconds
    this.TEST_DATA_MAX_BALANCE = 1000000;
    this.NUM_NODES = 39;

    this.timeNow = Math.floor(new Date().getTime() / 1000);
  }

  /**
   * Utility routine to convert seconds to nanoseconds string
   * @param {Number} sec seconds
   * @return {String} String representing nanosecond string
   */
  toNs(sec) {
    return `${sec}000000000`;
  }

  /**
   * This Pool.query method gets called when the code tries to query the database.
   * This method mocks the real database. It parses the SQL query and extracts the
   * filter clauses of the query, and returns those as part of the query response.
   * This code can be enhanced to return dummy data for transactions, balances, or
   * queries. Right now, we return a blank arrays
   * @param {String} sqlquery The SQL query string for postgreSQL
   * @param {Array} sqlparams The array of values for positional parameters
   * @return {Promise} promise Javascript promise that gets resolved with the response
   *                          with parsed query parameters.
   */
  query(sqlquery, sqlparams) {
    // Since this is a generic mock DB, first find out if this is a query
    // for transactions, balances, or accounts
    let callerFile;
    try {
      callerFile = new Error().stack.split('at ')[2].match(/\/(\w+?).js:/)[1];
    } catch (err) {
      callerFile = 'unknown';
    }

    // To parse the sql parameters, we need the 'order by' param used
    let orderprefix = '';
    switch (callerFile) {
      case 'transactions':
        orderprefix = 'consensus_ns';
        break;
      case 'balances':
        orderprefix = 'account_id';
        break;
      case 'accounts':
        orderprefix = 'coalesce\\(ab.account_id, e.id\\)';
        break;
      default:
        break;
    }

    // Parse the SQL query
    const parsedparams = testutils.parseSqlQueryAndParams(sqlquery, sqlparams, orderprefix);
    const rows = this.createMockData(callerFile, parsedparams);
    const promise = new Promise(function (resolve, reject) {
      resolve({
        rows,
        sqlQuery: {
          query: sqlquery,
          params: sqlparams,
          parsedparams,
        },
      });
    });

    return promise;
  }

  /**
   * Create mock data
   * @param {String} callerFile Which file invoked this Pool query
   * @param {Object} parsedparams Parsed parameters that were present in the SQL query
   * @return {Array} rows array filled with mock data
   */
  createMockData(callerFile, parsedparams) {
    let rows = [];
    if (callerFile === 'transactions') {
      rows = this.createMockTransactions(parsedparams);
    } else if (callerFile === 'balances') {
      rows = this.createMockBalances(parsedparams);
    } else if (callerFile === 'accounts') {
      rows = this.createMockAccounts(parsedparams);
    }
    return rows;
  }

  /**
   * Create mock data for /transactions query
   * @param {Object} parsedparams Parsed parameters that were present in the SQL query
   * @return {Array} rows array filled with mock data
   */
  createMockTransactions(parsedparams) {
    let accountNum = {
      low: 1,
      high: this.TEST_DATA_MAX_ACCOUNTS,
    };
    let timestamp = {
      low: this.toNs(this.timeNow - this.TEST_DATA_MAX_HISTORY),
      high: this.toNs(this.timeNow),
    };
    let limit = {
      low: maxLimit,
      high: maxLimit,
    };
    let order = 'desc';

    // Adjust the low/high values based on the SQL query parameters
    for (const param of parsedparams) {
      switch (param.field) {
        case 'entity_id':
          accountNum = this.adjustRangeBasedOnConstraints(param, accountNum);
          break;
        case 'consensus_ns':
          timestamp = this.adjustRangeBasedOnConstraints(param, timestamp);
          break;
        case 'limit':
          limit = this.adjustRangeBasedOnConstraints(param, limit);
          break;
        case 'order':
          order = param.value;
          break;
      }
    }

    // Sanity check on the numbers
    [accountNum, timestamp, limit].forEach((pVar) => {
      pVar = this.sanityCheck(pVar);
    });

    // Create a mock response based on the sql query parameters
    let rows = [];
    for (let i = 0; i < limit.high; i++) {
      const row = {};
      row.payer_account_id = EntityId.of(0, 0, i).getEncodedId();
      row.memo = Buffer.from(`Test memo ${i}`);
      row.consensus_ns = this.toNs(this.timeNow - i);
      row.valid_start_ns = this.toNs(this.timeNow - i - 1);
      row.result = 'SUCCESS';
      row.type = 14;
      row.name = 'CRYPTOTRANSFER';
      row.node_account_id = EntityId.of(0, 0, i % this.NUM_NODES).getEncodedId();
      row.ctl_entity_id = EntityId.of(
        0,
        0,
        Number(accountNum.low) + (accountNum.high == accountNum.low ? 0 : i % (accountNum.high - accountNum.low))
      ).getEncodedId();
      row.amount = i * 1000;
      row.charged_tx_fee = 100 + i;
      row.transaction_hash = '';
      row.scheduled = false;
      rows.push(row);
    }
    if (['asc', 'ASC'].includes(order)) {
      rows = rows.reverse();
    }

    return rows;
  }

  /**
   * Create mock data for /balances query
   *
   * @param {Object} parsedparams Parsed parameters that were present in the SQL query
   * @return {Array} rows array filled with mock data
   */
  createMockBalances(parsedparams) {
    let accountNum = {
      low: 1,
      high: this.TEST_DATA_MAX_ACCOUNTS,
    };
    let timestamp = {
      low: this.timeNow - this.TEST_DATA_MAX_HISTORY,
      high: this.timeNow,
    };
    let balance = {
      low: 0,
      high: this.TEST_DATA_MAX_BALANCE,
    };
    let limit = {
      low: maxLimit,
      high: maxLimit,
    };
    let order = 'desc';

    // Adjust the low/high values based on the SQL query parameters
    for (const param of parsedparams) {
      switch (param.field) {
        case 'account_id':
          accountNum = this.adjustRangeBasedOnConstraints(param, accountNum);
          break;
        case 'consensus_timestamp':
          // Convert the nanoseconds into seconds
          let paramSeconds = JSON.parse(JSON.stringify(param)); // deep copy
          paramSeconds.value = math.number(math.divide(math.bignumber(paramSeconds.value), math.bignumber(1e9)));

          timestamp = this.adjustRangeBasedOnConstraints(paramSeconds, timestamp);
          break;
        case 'balance':
          balance = this.adjustRangeBasedOnConstraints(param, balance);
          break;
        case 'limit':
          limit = this.adjustRangeBasedOnConstraints(param, limit);
          break;
        case 'order':
          order = param.value;
          break;
      }
    }

    // Sanity check on the numbers
    [accountNum, timestamp, balance, limit].forEach((pVar) => {
      pVar = this.sanityCheck(pVar);
    });

    // Create a mock response based on the sql query parameters
    let rows = [];
    for (let i = 0; i < limit.high; i++) {
      const row = {};
      row.consensus_timestamp = this.toNs(Math.floor((timestamp.low + timestamp.high) / 2));
      row.account_id = `${
        Number(accountNum.high) - (accountNum.high === accountNum.low ? 0 : i % (accountNum.high - accountNum.low))
      }`;
      row.balance = balance.low + Math.floor((balance.high - balance.low) / limit.high);

      rows.push(row);
    }

    if (['asc', 'ASC'].includes(order)) {
      rows = rows.reverse();
    }

    return rows;
  }

  /**
   * Create mock data for /accounts query
   * @param {Object} parsedparams Parsed parameters that were present in the SQL query
   * @return {Array} rows array filled with mock data
   */
  createMockAccounts(parsedparams) {
    let accountNum = {
      low: 1,
      high: this.TEST_DATA_MAX_ACCOUNTS,
    };
    let balance = {
      low: 0,
      high: this.TEST_DATA_MAX_BALANCE,
    };
    let limit = {
      low: maxLimit,
      high: maxLimit,
    };
    let order = 'desc';

    // Adjust the low/high values based on the SQL query parameters
    for (const param of parsedparams) {
      switch (param.field) {
        case 'account_id':
          accountNum = this.adjustRangeBasedOnConstraints(param, accountNum);
          break;
        case 'balance':
          balance = this.adjustRangeBasedOnConstraints(param, balance);
          break;
        case 'limit':
          limit = this.adjustRangeBasedOnConstraints(param, limit);
          break;
        case 'order':
          order = param.value;
          break;
      }
    }

    // Sanity check on the numbers
    [accountNum, balance, limit].forEach((pVar) => {
      pVar = this.sanityCheck(pVar);
    });

    // Create a mock response based on the sql query parameters
    let rows = [];
    for (let i = 0; i < limit.high; i++) {
      let row = {};

      row.account_balance = balance.low + Math.floor((balance.high - balance.low) / limit.high);
      row.consensus_timestamp = this.toNs(this.timeNow);
      row.entity_id = `${
        Number(accountNum.high) - (accountNum.high == accountNum.low ? 0 : i % (accountNum.high - accountNum.low))
      }`;
      row.exp_time_ns = this.toNs(this.timeNow + 1000);
      row.auto_renew_period = i * 1000;
      row.key = Buffer.from(`Key for row ${i}`);
      row.deleted = false;
      row.entity_type = 'Account';

      rows.push(row);
    }

    if (['asc', 'ASC'].includes(order)) {
      rows = rows.reverse();
    }

    return rows;
  }

  /**
   * Utility function to adjust the low and high constraints of a given object based on
   * the values present in the SQL query
   * @param {Object} parm A query parameters that was present in the SQL query
   * @param {Object} pVar The object whose low/high values need to be adjusted
   * @return {Object} pVar the adjusted object
   */
  adjustRangeBasedOnConstraints(param, pVar) {
    switch (param.operator) {
      case '<':
      case '<=':
        pVar.high = param.value - 1;
        break;
      case '>':
      case '>=':
        pVar.low = param.value + 1;
        break;
      case '=':
        pVar.low = param.value;
        pVar.high = param.value;
        break;
    }
    if (param.field === 'limit') {
      if (param.high > maxLimit) {
        param.low = maxLimit;
        param.high = maxLimit;
      }
    }
    return pVar;
  }

  /**
   * Utility function to ensure low value is lower than high value to prevent
   * in advertent infinite loops if the query has low/high values inverted
   * @param {Object} pVar The object whose low/high values need to be tested
   * @return {Object} pVar the adjusted object
   */
  sanityCheck(pVar) {
    if (pVar.low > pVar.high) {
      pVar.low = pVar.high - 1;
    }
    return pVar;
  }
}

module.exports = Pool;
