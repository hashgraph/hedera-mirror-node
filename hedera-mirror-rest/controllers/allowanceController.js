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

const AccountAlias = require('../accountAlias');
const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const constants = require('../constants');
const EntityId = require('../entityId');
const utils = require('../utils');

const {CryptoAllowance} = require('../model');
const {CryptoAllowanceService, EntityService} = require('../service');
const {CryptoAllowanceViewModel} = require('../viewmodel');

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

const updateConditionsAndParamsWithValues = (
  filter,
  existingParams,
  existingConditions,
  fullName,
  position = existingParams.length
) => {
  existingParams.push(filter.value);
  existingConditions.push(`${fullName}${filter.operator}$${position}`);
};

/**
 * Extracts SQL where conditions, params, order, and limit
 *
 * @param {[]} filters parsed and validated filters
 * @param {Number} accountId parsed accountId from path
 * @param {Number} startPosition param index start position
 */
const extractCryptoAllowancesQuery = (filters, accountId, startPosition = 1) => {
  let limit = defaultLimit;
  let order = constants.orderFilterValues.DESC;
  const conditions = [`${CryptoAllowance.OWNER} = $${startPosition}`];
  const params = [accountId];

  for (const filter of filters) {
    if (_.isNil(filter)) {
      continue;
    }

    switch (filter.key) {
      case constants.filterKeys.SPENDER_ID:
        if (!utils.isRegexMatch(constants.queryParamOperatorPatterns.eq, filter.operator)) {
          throw new InvalidArgumentError(
            `Only equals (eq) comparison operator is supported for ${constants.filterKeys.SPENDER_ID}`
          );
        }
        updateConditionsAndParamsWithValues(
          filter,
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
const getAccountCryptoAllowances = async (req, res) => {
  // extract filters from query param
  let accountId = null;
  try {
    accountId = await EntityService.getEncodedIdOfValidatedEntityId(req.params.accountAliasOrAccountId);
  } catch (err) {
    if (err instanceof InvalidArgumentError) {
      throw InvalidArgumentError.forParams(constants.filterKeys.ACCOUNT_ID_OR_ALIAS);
    }

    // rethrow any other error
    throw err;
  }

  // extract filters from query param
  const filters = utils.buildAndValidateFilters(req.query);

  const {conditions, params, order, limit} = extractCryptoAllowancesQuery(filters, accountId);
  const allowances = await CryptoAllowanceService.getAccountCrytoAllowances(conditions, params, order, limit);

  const response = {
    allowances: allowances.map((allowance) => new CryptoAllowanceViewModel(allowance)),
    links: {
      next: null,
    },
  };

  if (!_.isEmpty(response.allowances) && response.allowances.length === limit) {
    // skip limit on single account and spender combo with eq operator
    const spenderFilter = filters.filter((x) => x.key === constants.filterKeys.SPENDER_ID);
    const skipNext =
      spenderFilter.length === 1 &&
      utils.isRegexMatch(constants.queryParamOperatorPatterns.eq, spenderFilter[0].operator);
    if (!skipNext) {
      const lastRow = _.last(response.allowances);
      const last = {
        [constants.filterKeys.SPENDER_ID]: lastRow.spender,
      };
      response.links.next = utils.getPaginationLink(req, false, last, order);
    }
  }

  res.locals[constants.responseDataLabel] = response;
};

module.exports = {
  getAccountCryptoAllowances,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    extractCryptoAllowancesQuery,
  });
}
