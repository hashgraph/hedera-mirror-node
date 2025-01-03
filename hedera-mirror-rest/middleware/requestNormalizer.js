/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import {getOpenApiMap} from './openapiHandler.js';
import {filterKeys} from '../constants.js';
import _ from 'lodash';
import * as querystring from 'node:querystring';

const openApiMap = getOpenApiMap();

// Multiple values of these params can be collapsed to the last value
const COLLAPSABLE_PARAMS = [filterKeys.BALANCE, filterKeys.BLOCK_HASH, filterKeys.NONCE, filterKeys.SCHEDULED];

/**
 * Do not sort these query parameters as the results of the sql query changes based on their order
 *
 * Some examples from the spec tests:
 *   /api/v1/contracts/results?block.number=11&block.number=10 sorts the results differently than ?block.number=10&block.number=11
 *
 * From historical-custom-fees.json:
 *   /api/v1/tokens/1135?timestamp=lt:1234567899.999999000&timestamp=1234567899.999999001 returns a result whereas
 *   ?timestamp=1234567899.999999001&timestamp=lt:1234567899.999999000 returns a 404
 */
const NON_SORTED_PARAMS = COLLAPSABLE_PARAMS.concat([filterKeys.BLOCK_NUMBER, filterKeys.TIMESTAMP]);

/**
 * Normalizes a request by adding any missing default values and sorting any array query parameters.
 *
 * It is expected that this is called after any error handling for the request
 *
 * @param openApiRoute {string}
 * @param path {string}
 * @param query request query object
 * @returns {string}
 */
const normalizeRequestQueryParams = (openApiRoute, path, query) => {
  const openApiParameters = openApiMap.get(openApiRoute);
  if (_.isEmpty(openApiParameters)) {
    return _.isEmpty(query) ? path : path + '?' + querystring.stringify(query);
  }

  let normalizedQuery = '';
  for (const param of openApiParameters) {
    const name = param.parameterName;
    const value = query[name];
    let normalizedValue = '';
    if (value !== undefined) {
      normalizedValue = Array.isArray(value) ? getNormalizedArrayValue(name, value) : value;
    } else if (param?.defaultValue !== undefined) {
      // Add the default value to the query parameter
      normalizedValue = param.defaultValue;
    }

    if (!_.isEmpty(normalizedValue)) {
      normalizedQuery = appendToQuery(normalizedQuery, name + '=' + normalizedValue);
    }
  }

  return _.isEmpty(normalizedQuery) ? path : path + '?' + normalizedQuery;
};

const appendToQuery = (queryString, queryValue) => {
  return queryString + getQueryPrefix(queryString) + queryValue;
};

const getNormalizedArrayValue = (name, valueArray) => {
  if (_.isEmpty(valueArray)) {
    return;
  }

  if (!NON_SORTED_PARAMS.includes(name)) {
    // Sort the order of the parameters within the array
    valueArray.sort();
  } else if (COLLAPSABLE_PARAMS.includes(name)) {
    // Only add the last item in the array to the query parameter
    valueArray = valueArray.slice(valueArray.length - 1);
  }

  return valueArray.join('&' + name + '=');
};

const getQueryPrefix = (queryString) => {
  return queryString.length > 0 ? '&' : '';
};

export {normalizeRequestQueryParams};
