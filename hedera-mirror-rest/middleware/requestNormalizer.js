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

/**
 * Normalizes a request by adding any missing default values and sorting the query parameters.
 * @param path {string}
 * @param query
 * @returns {string}
 */
const normalizeRequestQueryParams = (path, query) => {
  const openApiPathMap = getOpenApiPathMap(path);
  if (_.isEmpty(openApiPathMap)) {
    return formatPathQuery(path, query);
  }

  for (const openApiParam of openApiPathMap.parameters) {
    if (query[openApiParam?.parameterName] === undefined && openApiParam?.defaultValue !== undefined) {
      // Add default value to query parameter
      query[openApiParam.parameterName] = openApiParam.defaultValue;
    } else if (Array.isArray(query[openApiParam?.parameterName])) {
      // Sort any listed query parameters
      query[openApiParam.parameterName].sort();
    }
  }

  return formatPathQuery(path, query);
};

/**
 * @param path
 * @returns {parameters [{parameterName, defaultValue}]}
 */
const getOpenApiPathMap = (path) => {
  let openApiPathMap = openApiMap.get(path);

  // If the path doesn't match a key from the map, iterate through the map to find a regex match
  if (openApiPathMap === undefined) {
    for (let [key, value] of openApiMap) {
      if (value.regex.test(path)) {
        openApiPathMap = value;
        break;
      }
    }
  }

  return openApiPathMap;
};

const alphabeticalSort = (a, b) => {
  return a.localeCompare(b);
};

const formatPathQuery = (path, query) => {
  return _.isEmpty(query) ? path : path + '?' + sortQueryProperties(query);
};

const sortQueryProperties = (query) => {
  return qs.stringify(query, stringifyOptions);
};

// alphabeticalSort is used to sort the properties of the Object
const stringifyOptions = {encode: false, indices: false, sort: alphabeticalSort};

export {normalizeRequestQueryParams};
