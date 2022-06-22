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

const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const constants = require('../constants');
const utils = require('../utils');

const BaseController = require('./baseController');
const Bound = require('./bound');

const {EntityService, TokenAllowanceService} = require('../service');
const {TokenAllowanceViewModel} = require('../viewmodel');

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

class TokenAllowanceController extends BaseController {
  /**
   * Gets filters for the lower part of the multi-union query.
   *
   * @param {Bound} spenderBound
   * @param {Bound} tokenIdBound
   * @return {{key: string, operator: string, value: *}[]}
   */
  getLowerFilters(spenderBound, tokenIdBound) {
    let filters = [];
    if (!tokenIdBound.hasBound()) {
      // no token.id bound filters or no token.id filters at all, everything goes into the lower part and there
      // shouldn't be inner or upper part.
      filters = [spenderBound.equal, spenderBound.lower, spenderBound.upper, tokenIdBound.equal];
    } else if (spenderBound.hasLower() && tokenIdBound.hasLower()) {
      // both have lower. If spender.id has lower and token.id doesn't have lower, the lower bound of spender.id
      // will go into the inner part.
      filters = [{...spenderBound.lower, operator: utils.opsMap.eq}, tokenIdBound.lower];
    }
    return filters.filter((f) => !_.isNil(f));
  }

  /**
   * Gets filters for the inner part of the multi-union query
   *
   * @param {Bound} spenderBound
   * @param {Bound} tokenIdBound
   * @return {{key: string, operator: string, value: *}[]}
   */
  getInnerFilters(spenderBound, tokenIdBound) {
    if (!spenderBound.hasBound() || !tokenIdBound.hasBound()) {
      return [];
    }

    return [
      // if token.id has lower bound, the spender.id filter should be spender.id > ?
      {filter: spenderBound.lower, newOperator: tokenIdBound.hasLower() ? utils.opsMap.gt : null},
      // if token.id has upper bound, the spender.id filter should be spender.id < ?
      {filter: spenderBound.upper, newOperator: tokenIdBound.hasUpper() ? utils.opsMap.lt : null},
    ]
      .filter((f) => !_.isNil(f.filter))
      .map((f) => ({...f.filter, operator: f.newOperator || f.filter.operator}));
  }

  /**
   * Gets filters for the upper part of the multi-union query
   *
   * @param {Bound} spenderBound
   * @param {Bound} tokenIdBound
   * @return {{key: string, operator: string, value: *}[]}
   */
  getUpperFilters(spenderBound, tokenIdBound) {
    if (!spenderBound.hasUpper() || !tokenIdBound.hasUpper()) {
      return [];
    }
    // the upper part should always have spender.id = ?
    return [{...spenderBound.upper, operator: utils.opsMap.eq}, tokenIdBound.upper];
  }

  validateBounds(spenderBound, tokenIdBound) {
    if (!this.validateSecondaryBound(spenderBound, tokenIdBound)) {
      throw new InvalidArgumentError(
        `${constants.filterKeys.TOKEN_ID} without a ${constants.filterKeys.SPENDER_ID} parameter filter`
      );
    }

    if (!this.validateLowerBounds(spenderBound, tokenIdBound)) {
      throw new InvalidArgumentError('Unsupported combination');
    }

    if (!this.validateUpperBounds(spenderBound, tokenIdBound)) {
      throw new InvalidArgumentError('Unsupported combination');
    }
  }

  /**
   * Extracts multiple queries to be combined in union.
   *
   * @param {[]} filters req filters
   * @param {BigInt} ownerAccountId Encoded owner entityId
   * @returns {{bounds: {string: Bound}, lower: *[], inner: *[], upper: *[], accountId: BigInt, order: 'asc'|'desc', limit: number}}
   */
  extractTokenMultiUnionQuery(filters, ownerAccountId) {
    const spenderBound = new Bound();
    const tokenIdBound = new Bound();
    const bounds = {
      [constants.filterKeys.SPENDER_ID]: spenderBound,
      [constants.filterKeys.TOKEN_ID]: tokenIdBound,
    };
    let limit = defaultLimit;
    let order = constants.orderFilterValues.ASC;

    for (const filter of filters) {
      switch (filter.key) {
        case constants.filterKeys.SPENDER_ID:
        case constants.filterKeys.TOKEN_ID:
          bounds[filter.key].parse(filter);
          break;
        case constants.filterKeys.LIMIT:
          limit = filter.value;
          break;
        case constants.filterKeys.ORDER:
          order = filter.value;
          break;
        default:
          break;
      }
    }

    this.validateBounds(spenderBound, tokenIdBound);

    return {
      bounds,
      lower: this.getLowerFilters(spenderBound, tokenIdBound),
      inner: this.getInnerFilters(spenderBound, tokenIdBound),
      upper: this.getUpperFilters(spenderBound, tokenIdBound),
      order,
      ownerAccountId,
      limit,
    };
  }

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/allowances/tokens API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getAccountTokenAllowances = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query);
    const query = this.extractTokenMultiUnionQuery(filters, accountId);
    const tokenAllowances = await TokenAllowanceService.getAccountTokenAllowances(query);
    const allowances = tokenAllowances.map((model) => new TokenAllowanceViewModel(model));

    res.locals[constants.responseDataLabel] = {
      allowances,
      links: {
        next: this.getPaginationLink(req, allowances, query.bounds, query.limit, query.order),
      },
    };
  };

  /**
   * Gets the pagination link for token allowance query
   * @param {Request} req
   * @param {TokenAllowanceViewModel[]} allowances
   * @param {{string: Bound}} bounds
   * @param {number} limit
   * @param {string} order
   * @return {string|null}
   */
  getPaginationLink(req, allowances, bounds, limit, order) {
    const spenderBound = bounds[constants.filterKeys.SPENDER_ID];
    const tokenIdBound = bounds[constants.filterKeys.TOKEN_ID];

    if (allowances.length < limit || (spenderBound.hasEqual() && tokenIdBound.hasEqual())) {
      // fetched all matching rows or the query is for a specific spender and token id combination
      return null;
    }

    const lastRow = _.last(allowances);
    const lastValues = {};
    if (spenderBound.hasBound() || spenderBound.isEmpty()) {
      // page on spender.id when either the spender query has bound or no spender query at all
      // spender.id should be exclusive when the token.id operator is eq
      lastValues[constants.filterKeys.SPENDER_ID] = {value: lastRow.spender, inclusive: !tokenIdBound.hasEqual()};
    }

    if (tokenIdBound.hasBound() || tokenIdBound.isEmpty()) {
      // page on token.id when either the token.id query has bound or no token.id query at all
      lastValues[constants.filterKeys.TOKEN_ID] = {value: lastRow.token_id};
    }

    return utils.getPaginationLink(req, false, lastValues, order);
  }
}

module.exports = new TokenAllowanceController();
