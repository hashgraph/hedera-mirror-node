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

const httpContext = require('express-http-context');
const qs = require('qs');
const constants = require('../constants');
const {randomString} = require('../utils');

const requestLogger = async (req, res, next) => {
  httpContext.set(constants.requestIdLabel, randomString(8));
  logger.info(`${req.ip} ${req.method} ${req.originalUrl}`);

  // set default http OK code for reference
  res.locals.statusCode = 200;
};

/**
 * Manage request query params to support case insensitive keys
 * Express default query parser uses qs, other option is querystring, both are case sensitive
 * Parse using default qs logic and use to populate a new map in which all keys are lowercased
 * @param queryString
 * @returns Query string map object
 */
const requestQueryParser = (queryString) => {
  // parse first to benefit from qs query handling
  const parsedQueryString = qs.parse(queryString);

  const caseInsensitiveQueryString = {};
  for (const key of Object.keys(parsedQueryString)) {
    const lowerKey = key.toLowerCase();
    let currentValue = caseInsensitiveQueryString[lowerKey];
    if (currentValue) {
      // handle repeated values. Add to array if applicable or convert to array
      if (!Array.isArray(currentValue)) {
        // create a new array of current value
        currentValue = [currentValue];
      }

      // assign value as concat of current value array and new value which may also be an array
      caseInsensitiveQueryString[lowerKey] = currentValue.concat(parsedQueryString[key]);
    } else {
      caseInsensitiveQueryString[lowerKey] = parsedQueryString[key];
    }
  }

  return caseInsensitiveQueryString;
};

module.exports = {
  requestLogger,
  requestQueryParser,
};
