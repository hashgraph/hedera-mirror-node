/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import _ from 'lodash';

import {getOpenApiMap} from './openapiHandler.js';

const openApiMap = getOpenApiMap();

/**
 * Do not sort these query parameters as the results of the sql query changes based on their order
 *
 * Some examples from the spec tests:
 *   /api/v1/contracts/results?block.number=11&block.number=10 sorts the results differently than ?block.number=10&block.number=11
 *   /api/v1/transactions/0.0.10-1234567890-000000001?nonce=2&nonce=1 sorts the results differently than ?nonce=1&nonce=2
 *
 * From historical-custom-fees.json:
 *   /api/v1/tokens/1135?timestamp=lt:1234567899.999999000&timestamp=1234567899.999999001 returns a result whereas
 *   ?timestamp=1234567899.999999001&timestamp=lt:1234567899.999999000 returns a 404
 */
const NON_SORTED_PARAMS = ['balance', 'block.hash', 'block.number', 'nonce', 'scheduled', 'timestamp'];

/**
 * Normalizes a request by adding any missing default values and sorting any array query parameters.
 *
 * Any unknown query parameters will not be included in the normalized query string.
 *
 * Note, this should be called after requestQueryParser, as query parameters should be lower case before this is called.
 * Parameters such as `LIMIT=10` are not equivalent to `limit=10` and are unknown and will not be included in the normalized query.
 *
 * @param openApiRoute {string}
 * @param path {string}
 * @param query request query object
 * @returns {string}
 */
const normalizeRequestQueryParams = (openApiRoute, path, query) => {
  const openApiParameters = openApiMap.get(openApiRoute);
  if (_.isEmpty(openApiParameters)) {
    return path;
  }

  let queryString = '';
  for (const param of openApiParameters) {
    const name = param.parameterName;
    const value = query[param.parameterName];
    if (value !== undefined) {
      queryString = Array.isArray(value)
        ? appendArrayToQuery(queryString, name, value)
        : appendToQuery(queryString, name, value);
    } else if (param?.defaultValue !== undefined) {
      // Add the default value to the query parameter
      queryString = appendToQuery(queryString, name, param.defaultValue);
    }
  }

  return _.isEmpty(queryString) ? path : path + '?' + queryString;
};

const appendArrayToQuery = (queryString, parameterName, valueArray) => {
  if (!NON_SORTED_PARAMS.includes(parameterName)) {
    // Sort the order of the parameters within the array
    valueArray.sort();
  }

  return queryString + getQueryPrefix(queryString) + parameterName + '=' + valueArray.join('&' + parameterName + '=');
};

const appendToQuery = (queryString, parameterName, value) => {
  return queryString + getQueryPrefix(queryString) + parameterName + '=' + value;
};

const getQueryPrefix = (queryString) => {
  return queryString.length > 0 ? '&' : '';
};

export {normalizeRequestQueryParams};
