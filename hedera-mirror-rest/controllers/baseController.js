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
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

class BaseController {
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
   * Validate that the bounds are valid. Validates the following:
   *  Bound Range
   *  Secondary Bound is not present without Primary Bound
   *  Lower Bounds
   *  Upper Bounds.
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @throws {InvalidArgumentError}
   */
  validateBounds(primaryBound, secondaryBound) {
    this.validateBoundsRange(primaryBound, secondaryBound);
    this.validateSecondaryBound(primaryBound, secondaryBound);
    this.validateLowerBounds(primaryBound, secondaryBound);
    this.validateUpperBounds(primaryBound, secondaryBound);
  }

  /**
   * Validate that if the primary bound is empty the secondary bound is empty as well.
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @throws {InvalidArgumentError}
   */
  validateSecondaryBound(primaryBound, secondaryBound) {
    if (primaryBound.isEmpty() && !secondaryBound.isEmpty()) {
      throw new InvalidArgumentError(
        `${secondaryBound.filterKey} without a ${primaryBound.filterKey} parameter filter`
      );
    }
  }

  /**
   * Validate that the Lower Bounds are valid.
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @throws {InvalidArgumentError}
   */
  validateLowerBounds(primaryBound, secondaryBound) {
    if (
      !primaryBound.hasEqual() &&
      secondaryBound.hasLower() &&
      (!primaryBound.hasLower() || primaryBound.lower.operator === utils.opsMap.gt)
    ) {
      throw new InvalidArgumentError(`${primaryBound.filterKey} must have gte or eq operator`);
    }
  }

  /**
   * Validate that the Upper Bounds are valid.
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @throws {InvalidArgumentError}
   */
  validateUpperBounds(primaryBound, secondaryBound) {
    if (
      !primaryBound.hasEqual() &&
      secondaryBound.hasUpper() &&
      (!primaryBound.hasUpper() || primaryBound.upper.operator === utils.opsMap.lt)
    ) {
      throw new InvalidArgumentError(`${primaryBound.filterKey} must have lte or eq operator`);
    }
  }

  /**
   * Validate the bound range and equal combination
   *
   * @param {Bound} primaryBound
   * @param {Bound} secondaryBound
   * @throws {InvalidArgumentError}
   */
  validateBoundsRange(primaryBound, secondaryBound) {
    for (const bound of [primaryBound, secondaryBound]) {
      if (bound.hasBound() && bound.hasEqual()) {
        throw new InvalidArgumentError(`Can't support both range and equal`);
      }
    }
  }

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
    } else if (primaryBound.hasEqual()) {
      filters = [
        primaryBound.equal,
        primaryBound.lower,
        primaryBound.upper,
        secondaryBound.lower,
        secondaryBound.equal,
        secondaryBound.upper,
      ];
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
   * @param {{primary:string,secondary:string, primaryDbColumn:string, secondaryDbColumn:string}} boundKeys
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
      const dbColumn = !_.isNil(boundKeys.primaryDbColumn) ? boundKeys.primaryDbColumn : boundKeys.primary;
      lastValues[boundKeys.primary] = {value: lastRow[dbColumn], inclusive: !secondaryBound.hasEqual()};
    }

    if (secondaryBound.hasBound() || secondaryBound.isEmpty()) {
      // the secondary param has bound or no secondary param at all
      const dbColumn = !_.isNil(boundKeys.secondaryDbColumn) ? boundKeys.secondaryDbColumn : boundKeys.secondary;
      lastValues[boundKeys.secondary] = {value: lastRow[dbColumn]};
    }

    return utils.getPaginationLink(req, false, lastValues, order);
  }
}

module.exports = BaseController;
