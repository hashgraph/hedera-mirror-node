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
import qs from 'qs';

import {getOpenApiMap} from './openapiHandler.js';

const openApiMap = getOpenApiMap();

// Do not sort these query parameters as the results of the sql query changes based on their order
const NON_SORTED_PARAMS = ['balance', 'block.hash', 'block.number', 'nonce', 'scheduled', 'timestamp'];

/**
 * Normalizes a request by adding any missing default values and sorting the query parameters.
 *
 * Note, query parameters should be lower case before this is called, as parameters such as `LIMIT=25` are not equivalent to 'limit=25' and an additional `limit=25` will be added to the resulting parameters.
 *
 * @param openApiRoute {string}
 * @param path {string}
 * @param query
 * @returns {string}
 */
const normalizeRequestQueryParams = (openApiRoute, path, query) => {
  const openApiParameters = openApiMap.get(openApiRoute);
  if (_.isEmpty(openApiParameters)) {
    return formatPathQuery(path, query);
  }

  const normalizedQuery = _.cloneDeep(query);
  for (const param of openApiParameters) {
    if (normalizedQuery[param?.parameterName] === undefined && param?.defaultValue !== undefined) {
      // Add default value to query parameter
      normalizedQuery[param.parameterName] = param.defaultValue;
    } else if (
      !NON_SORTED_PARAMS.includes(param?.parameterName) &&
      Array.isArray(normalizedQuery[param?.parameterName])
    ) {
      // Sort the order of the parameters within the array
      normalizedQuery[param.parameterName].sort();
    }
  }

  return formatPathQuery(path, normalizedQuery);
};

const alphabeticalSort = (a, b) => {
  return a.localeCompare(b);
};

const formatPathQuery = (path, query) => {
  return _.isEmpty(query) ? path : path + '?' + sortQueryProperties(query);
};

const sortQueryProperties = (query) => {
  // Sort the order of the parameters in the query
  return qs.stringify(query, stringifyOptions);
};

// alphabeticalSort is used to sort the properties of the Object
const stringifyOptions = {encode: false, indices: false, sort: alphabeticalSort};

export {normalizeRequestQueryParams};
