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

const {CryptoAllowance} = require('../model');
const {CryptoAllowanceService, EntityService} = require('../service');
const {CryptoAllowanceViewModel} = require('../viewmodel');

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

class AllowanceController extends BaseController {
  /**
   * Extracts SQL where conditions, params, order, and limit
   *
   * @param {[]} filters parsed and validated filters
   * @param {Number} accountId parsed accountId from path
   * @param {Number} startPosition param index start position
   */
  extractCryptoAllowancesQuery = (filters, accountId, startPosition = 1) => {
    let limit = defaultLimit;
    let order = constants.orderFilterValues.DESC;
    const conditions = [`${CryptoAllowance.OWNER} = $${startPosition}`];
    const params = [accountId];
    const spenderInValues = [];

    for (const filter of filters) {
      if (_.isNil(filter)) {
        continue;
      }

      switch (filter.key) {
        case constants.filterKeys.SPENDER_ID:
          if (utils.opsMap.eq !== filter.operator) {
            throw new InvalidArgumentError(
              `Only equals (eq) comparison operator is supported for ${constants.filterKeys.SPENDER_ID}`
            );
          }
          this.updateConditionsAndParamsWithInValues(
            filter,
            spenderInValues,
            params,
            conditions,
            CryptoAllowance.SPENDER,
            startPosition + conditions.length
          );
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

    this.updateQueryFiltersWithInValues(params, conditions, spenderInValues, CryptoAllowance.SPENDER);

    return {
      conditions,
      params,
      order,
      limit,
    };
  };

  /**
   * Handler function for /accounts/:accountAliasOrAccountId/allowances/crypto API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getAccountCryptoAllowances = async (req, res) => {
    // extract filters from query param
    let accountId = null;
    try {
      accountId = await EntityService.getEncodedIdAccountIdOrAlias(req.params.accountAliasOrAccountId);
    } catch (err) {
      if (err instanceof InvalidArgumentError) {
        throw InvalidArgumentError.forParams(constants.filterKeys.ACCOUNT_ID_OR_ALIAS);
      }

      // rethrow any other error
      throw err;
    }

    // extract filters from query param
    const filters = utils.buildAndValidateFilters(req.query);

    const {conditions, params, order, limit} = this.extractCryptoAllowancesQuery(filters, accountId);
    const allowances = await CryptoAllowanceService.getAccountCryptoAllowances(conditions, params, order, limit);

    const response = {
      allowances: allowances.map((allowance) => new CryptoAllowanceViewModel(allowance)),
      links: {
        next: null,
      },
    };

    if (response.allowances.length === limit) {
      // skip limit on single account and spender combo. Doesn't check for operator since only eq is supported
      const spenderFilter = filters.filter((x) => x.key === constants.filterKeys.SPENDER_ID);
      if (spenderFilter.length !== 1) {
        const lastRow = _.last(response.allowances);
        const last = {
          [constants.filterKeys.SPENDER_ID]: lastRow.spender,
        };
        response.links.next = utils.getPaginationLink(req, false, last, order);
      }
    }

    res.locals[constants.responseDataLabel] = response;
  };
}

module.exports = new AllowanceController();
