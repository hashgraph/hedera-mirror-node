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

  /**
   * Gets filters for the lower part of the multi-union query.
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @return {{key: string, operator: string, value: *}[]}
   */
  getLowerFilters(primaryBound, secondaryBound) {
    let filters = [];
    if (!secondaryBound.hasBound()) {
      // no secondary bound filters or no secondary filters at all, everything goes into the lower part and there
      // shouldn't be inner or upper part.
      filters = [primaryBound.equal, primaryBound.lower, primaryBound.upper, secondaryBound.equal];
    } else if (primaryBound.hasLower() && secondaryBound.hasLower()) {
      // both have lower. If primary has lower and secondary doesn't have lower, the lower bound of primary
      // will go into the inner part.
      filters = [{...primaryBound.lower, operator: utils.opsMap.eq}, secondaryBound.lower];
    }
    return filters.filter((f) => !_.isNil(f));
  }

  /**
   * Gets filters for the inner part of the multi-union query
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @return {{key: string, operator: string, value: *}[]}
   */
  getInnerFilters(primaryBound, secondaryBound) {
    if (!primaryBound.hasBound() || !secondaryBound.hasBound()) {
      return [];
    }

    return [
      // if secondary has lower bound, the primary filter should be > ?
      {filter: primaryBound.lower, newOperator: secondaryBound.hasLower() ? utils.opsMap.gt : null},
      // if secondary has upper bound, the primary filter should be < ?
      {filter: primaryBound.upper, newOperator: secondaryBound.hasUpper() ? utils.opsMap.lt : null},
    ]
      .filter((f) => !_.isNil(f.filter))
      .map((f) => ({...f.filter, operator: f.newOperator || f.filter.operator}));
  }

  /**
   * Gets filters for the upper part of the multi-union query
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @return {{key: string, operator: string, value: *}[]}
   */
  getUpperFilters(primaryBound, secondaryBound) {
    if (!primaryBound.hasUpper() || !secondaryBound.hasUpper()) {
      return [];
    }
    // the upper part should always have primary filter = ?
    return [{...primaryBound.upper, operator: utils.opsMap.eq}, secondaryBound.upper];
  }

  /**
   * Gets the pagination link based on 2 main query params
   *
   * @param {Request} req
   * @param {*[]} rows
   * @param {{string:Bound}} bounds
   * @param {{primary:string,secondary:string}} boundKeys
   * @param {number} limit
   * @param {string} order
   * @return {string|null}
   */
  getPaginationLink(req, rows, bounds, boundKeys, limit, order) {
    const primaryBound = bounds[boundKeys.primary];
    const secondaryBound = bounds[boundKeys.secondary];

    if (rows.length < limit || (primaryBound.hasEqual() && secondaryBound.hasEqual())) {
      // fetched all matching rows or the query is for a specific combination
      return null;
    }

    const lastRow = _.last(rows);
    const lastValues = {};
    if (primaryBound.hasBound() || primaryBound.isEmpty()) {
      // the primary param has bound or no primary param at all
      // primary param should be exclusive when the secondary operator is eq
      lastValues[boundKeys.primary] = {value: lastRow[boundKeys.primary], inclusive: !secondaryBound.hasEqual()};
    }

    if (secondaryBound.hasBound() || secondaryBound.isEmpty()) {
      // the secondary param has bound or no secondary param at all
      lastValues[boundKeys.secondary] = {value: lastRow[boundKeys.secondary]};
    }

    return utils.getPaginationLink(req, false, lastValues, order);
  }
}

module.exports = BaseController;
