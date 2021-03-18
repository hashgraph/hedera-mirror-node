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

const log4js = require('log4js');

const logger = log4js.getLogger();

const checkSql = (parsedparams, condition) => {
  for (const p of parsedparams) {
    if (p.field == condition.field && p.operator == condition.operator && p.value == condition.value) {
      return true;
    }
  }
  logger.warn(
    `ERROR: Condition: ${condition.field} ${condition.operator} ${
      condition.value
    } not present in the generated SQL: ${JSON.stringify(parsedparams)}`
  );
  return false;
};

/**
 * Parse the sql query with positional parameters and an array of corresponding
 * values to extracts the filter clauses of the query (e.g. consensus_ns < xyz)
 * @param {String} sqlquery The SQL query string for postgreSQL
 * @param {Array} sqlparams The array of values for positional parameters
 * @param {String} orderprefix The parameter before ASC or DESC keyword in the SQL query
 * @return {Array} parsedparams An array with extracted filter
 *                          with parsed query parameters.
 */
const parseSqlQueryAndParams = (sqlquery, sqlparams, orderprefix = '') => {
  try {
    // The SQL query is of general form: "select p1, p2 ... pn from table_x limit l"
    // Extract the parameters (p1, p2, ..., pn) and the limit values

    // First clean up the newline/tab characters from multiline string
    const sql = sqlquery.replace(/[\r\n\t]/gm, ' ');

    // Extract everything before the first "from" clause (case insensitive)
    const retparams = sql.match(/select(.*?)\bfrom\b(.*)/is);
    if (retparams.length === 0) {
      return [];
    }

    // Split out individual parameters.
    // Replace "x as y" by "y" (e.g. 'entity_shard as shard' by shard); and
    // "x.y" by "y" (e.g. etrans.entity_num to entity_num) and
    // trim the whitespaces
    let params = retparams[1].split(',');
    params = params
      .map((p) => p.replace(/.*\bas\b/, ''))
      .map((p) => p.replace(/.*\./, ''))
      .map((p) => p.trim());

    // Extract all positional parameters - that are in the
    // "field operator $number" format such as "timestamp <= $2"
    let positionalparams = sql.match(/(\w+?)\s*[<|!|=|>]=*\s*\$(\d+?)/g);
    positionalparams = positionalparams === null ? [] : positionalparams;

    // Now find the limit parameter. This doesn't have an operator
    // because the sql syntax is like: "limit 1000"
    const limitparam = sql.match(/limit\s*?(\$.d*)\b/i);
    if (limitparam !== null) {
      positionalparams.push(`limit = ${limitparam[1]}`);
    }

    const parsedparams = [];
    // Now, parse each of them and separate out the field, operator and the value
    positionalparams.forEach((p) => {
      const matches = p.match(/(\w+?)\s*([<|=|>]=*)\s*\$(\d+?)/);
      if (matches.length === 4) {
        // original, field, operator, value
        parsedparams.push({
          field: matches[1],
          operator: matches[2],
          value: sqlparams[parseInt(matches[3]) - 1],
        });
      }
    });

    let eqParams = sql.match(/(\w+?)\s*?IN\s*?\(\s*?((?:\$\d+,?\s*?)+)\)/g);
    eqParams = eqParams === null ? [] : eqParams;

    eqParams.forEach((e) => {
      const matches = e.match(/(\w+?)\s*?(IN)\s*?\(\s*?((?:\$\d+,?\s*?)+)\)/);
      if (matches.length >= 4) {
        const eqParamSplit = matches[3].split(',');
        eqParamSplit.forEach((es) => {
          parsedparams.push({
            field: matches[1],
            operator: 'in',
            value: sqlparams[parseInt(es.trim()[1]) - 1],
          });
        });
      }
    });

    // And lastly, deal with the textual parameters like order and result
    // Find the order parameter by searching for 'desc' or 'asc'

    const orderRegex = new RegExp(`${orderprefix}\\s*\\b(desc|asc)\\b`, 'i');
    const orderparam = sql.match(orderRegex);
    if (orderparam !== null) {
      parsedparams.push({
        field: 'order',
        operator: '=',
        value: orderparam[orderparam.length - 1],
      });
    }
    // Result parameter
    const resultparam = sql.match(/result\s*(!*=)\s*(\d+)/);
    if (resultparam !== null && resultparam.length == 3) {
      parsedparams.push({
        field: 'result',
        operator: resultparam[1],
        value: resultparam[2],
      });
    }

    return parsedparams;
  } catch (err) {
    return [];
  }
};

const testBadParams = (request, server, url, param, badValues) => {
  const opList = ['', 'eq:', 'lt:', 'gt:', 'lte:', 'gte:', 'ne:'];
  let opListIndex = 0;
  for (const opt of badValues) {
    const op = opList[opListIndex];
    opListIndex = (opListIndex + 1) % opList.length;

    const queryparam = `${param}=${op}${opt}`;
    const fullUrl = `${url}?${queryparam}`;
    test(`Test: ${fullUrl}`, async () => {
      const response = await request(server).get(fullUrl);
      expect(response.status).toBeGreaterThanOrEqual(400);
      let check = false;
      const err = JSON.parse(response.text);
      if ('_status' in err && 'messages' in err._status && err._status.messages.length > 0) {
        if (err._status.messages[0].message === `Invalid parameter: ${param}`) {
          check = true;
        }
      }
      expect(check).toBeTruthy();
    });
  }
};

const badParamsList = () => {
  return [
    '',
    '"',
    'abcd',
    '!@#$%^&*(){}_+~`',
    '123456789012345678901234567890',
    '1a23',
    '0.a',
    'a.0.3',
    '0-0-3',
    '1eq:2',
    ':eq:',
    ':',
  ];
};

/**
 * Validate that account ids in the responseObjects returned by the api are in the list of valid account ids
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Array} list of valid account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumInArray = function (responseObjects, potentialValues) {
  for (const object of responseObjects) {
    const accNum = object.account.split('.')[2];
    if (!potentialValues.includes(Number(accNum))) {
      logger.warn(`validateAccNumInArray check failed: ${accNum} is not in [${potentialValues}]`);
      return false;
    }
  }
  return true;
};

module.exports = {
  badParamsList,
  checkSql,
  parseSqlQueryAndParams,
  testBadParams,
  validateAccNumInArray,
};
