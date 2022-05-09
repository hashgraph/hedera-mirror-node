/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const _ = require('lodash');

const utils = require('../utils');

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

class BaseController {
  updateConditionsAndParamsWithValues = (
    filter,
    existingParams,
    existingConditions,
    fullName,
    position = existingParams.length
  ) => {
    existingParams.push(filter.value);
    existingConditions.push(`${fullName}${filter.operator}$${position}`);
  };

  updateConditionsAndParamsWithInValues = (
    filter,
    invalues,
    existingParams,
    existingConditions,
    fullName,
    position = existingParams.length
  ) => {
    if (filter.operator === utils.opsMap.eq) {
      // aggregate '=' conditions and use the sql 'in' operator
      invalues.push(filter.value);
    } else {
      existingParams.push(filter.value);
      existingConditions.push(`${fullName}${filter.operator}$${position}`);
    }
  };

  updateQueryFiltersWithInValues = (
    existingParams,
    existingConditions,
    invalues,
    fullName,
    start = existingParams.length + 1
  ) => {
    if (!_.isNil(invalues) && !_.isEmpty(invalues)) {
      // add the condition 'c.id in ()'
      existingParams.push(...invalues);
      const positions = _.range(invalues.length)
        .map((position) => position + start)
        .map((position) => `$${position}`);
      existingConditions.push(`${fullName} in (${positions})`);
    }
  };

  /**
   * Retrieve a unique identifying string for a filter using it's key and comparison operator
   * e.g. 'token.id-=', 'serialnumber->='
   * Note gt & gte are equivalent, as are lt & lte when mergeOrEqualComparisons  is true
   * @param {Object} filter
   * @param {boolean} mergeOrEqualComparisons flag to treat gt & gte as equivalent, as well as lt & lte
   * @returns {string}
   */
  getFilterKeyOpString = (filter, mergeOrEqualComparisons = true) => {
    const rangeRegex = /(>|<)(=)?/;
    const comparisonString = mergeOrEqualComparisons ? filter.operator.replace(rangeRegex, '$1') : filter.operator;
    return `${filter.key}-${comparisonString.trim()}`;
  };

  /**
   * Verify there's only a single occurence of a given non-eq filter in the map using its unique string identifier
   * @param {Object} filterMap Map of observer filters
   * @param {String} filter Current filter
   */
  validateSingleFilterKeyOccurence = (filterMap, filter) => {
    if (filterMap[this.getFilterKeyOpString(filter)]) {
      throw new InvalidArgumentError(`Multiple range params not allowed for ${filter.key}`);
    }
  };
}

module.exports = BaseController;
