/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import httpContext from 'express-http-context';
import qs from 'qs';

import {httpStatusCodes, requestIdLabel, requestStartTime} from '../constants';
import {randomString} from '../utils';

const requestLogger = async (req, res, next) => {
  const requestId = await randomString(8);
  httpContext.set(requestIdLabel, requestId);

  // set default http OK code for reference
  res.locals.statusCode = httpStatusCodes.OK.code;
  res.locals[requestStartTime] = Date.now();
};

/**
 * Manage request query params to support case insensitive keys
 * Express default query parser uses qs, other option is querystring, both are case sensitive
 * Parse using default qs logic and use to populate a new map in which all keys are lowercased
 * @param queryString
 * @returns Query string map object
 */
const requestQueryParser = (queryString) => {
  const merge = (current, next) => {
    if (!Array.isArray(current)) {
      current = [current];
    }

    if (Array.isArray(next)) {
      current.push(...next);
    } else {
      current.push(next);
    }

    return current;
  };

  // parse first to benefit from qs query handling
  const parsedQueryString = qs.parse(queryString);

  const caseInsensitiveQueryString = {};
  for (const [key, value] of Object.entries(parsedQueryString)) {
    const lowerKey = key.toLowerCase();
    if (lowerKey in caseInsensitiveQueryString) {
      // handle repeated values, merge into an array
      caseInsensitiveQueryString[lowerKey] = merge(caseInsensitiveQueryString[lowerKey], value);
    } else {
      caseInsensitiveQueryString[lowerKey] = value;
    }
  }

  return caseInsensitiveQueryString;
};

export {requestLogger, requestQueryParser};
