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
const EntityId = require('../entityId');
const utils = require('../utils');

const {Nft} = require('../model');
const {NftService} = require('../service');
const {NftViewModel} = require('../viewmodel');

const defaultParamSupportMap = {
  [constants.filterKeys.LIMIT]: true,
  [constants.filterKeys.ORDER]: true,
};
const nftsByAccountIdParamSupportMap = {
  [constants.filterKeys.TOKEN_ID]: true,
  [constants.filterKeys.SERIAL_NUMBER]: true,
  ...defaultParamSupportMap,
};

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

const updateConditionsAndParamsWithInValues = (filter, invalues, existingParams, existingConditions, fullName) => {
  if (filter.operator === utils.opsMap.eq) {
    // aggregate '=' conditions and use the sql 'in' operator
    invalues.push(filter.value);
  } else {
    existingParams.push(filter.value);
    existingConditions.push(`${fullName}${filter.operator}$${existingParams.length}`);
  }
};

const updateQueryFiltersWithInValues = (existingParams, existingConditions, invalues, fullName) => {
  if (!_.isNil(invalues) && !_.isEmpty(invalues)) {
    // add the condition 'c.id in ()'
    const start = existingParams.length + 1; // start is the next positional index
    existingParams.push(...invalues);
    const positions = _.range(invalues.length)
      .map((position) => position + start)
      .map((position) => `$${position}`);
    existingConditions.push(`${fullName} in (${positions})`);
  }
};

/**
 * Extracts SQL where conditions, params, order, and limit
 *
 * @param {[]} filters parsed and validated filters
 * @param {Number} accountId parsed accountId from path
 * @param {string} contractId encoded contract ID
 * @return {{conditions: [], params: [], order: 'asc'|'desc', limit: number}}
 */
const extractNftsQuery = (filters, accountId, paramSupportMap = defaultParamSupportMap) => {
  let limit = defaultLimit;
  let order = constants.orderFilterValues.ASC;
  const conditions = [`${Nft.ACCOUNT_ID} = $1`];
  const params = [accountId];

  // token_id
  const nftTokenIdFullName = Nft.TOKEN_ID;
  const tokenInValues = [];

  // serialnumber
  const serialNumberFullName = Nft.SERIAL_NUMBER;
  const serialNumberInValues = [];

  let hasSerialNumber = false;
  let tokenIdFilter = null;
  for (const filter of filters) {
    if (_.isNil(paramSupportMap[filter.key])) {
      // param not supported for current endpoint
      continue;
    }

    switch (filter.key) {
      case constants.filterKeys.SERIAL_NUMBER:
        // handle repeated values
        updateConditionsAndParamsWithInValues(filter, serialNumberInValues, params, conditions, serialNumberFullName);
        hasSerialNumber = true;
        break;
      case constants.filterKeys.TOKEN_ID:
        // handle repeated values
        updateConditionsAndParamsWithInValues(filter, tokenInValues, params, conditions, nftTokenIdFullName);
        tokenIdFilter = filter;
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

  if (hasSerialNumber && (_.isNil(tokenIdFilter) || tokenIdFilter.operator !== utils.opsMap.eq)) {
    throw new InvalidArgumentError(
      `Cannot search NFTs with serialnumber without also specifying a single tokenId value`
    );
  }

  // update query with repeated values
  updateQueryFiltersWithInValues(params, conditions, tokenInValues, nftTokenIdFullName);
  updateQueryFiltersWithInValues(params, conditions, serialNumberInValues, serialNumberFullName);

  return {
    conditions: conditions,
    params: params,
    order: order,
    limit: limit,
  };
};

const validateAccountNftsQueryFilter = (param, op, val) => {
  let ret = false;

  if (op === undefined || val === undefined) {
    return ret;
  }

  // Validate operator
  if (!utils.isValidOperatorQuery(op)) {
    return ret;
  }

  // Validate the value
  switch (param) {
    case constants.filterKeys.ACCOUNT_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    case constants.filterKeys.LIMIT:
      ret = utils.isPositiveLong(val);
      break;
    case constants.filterKeys.ORDER:
      // Acceptable words: asc or desc
      ret = utils.isValidValueIgnoreCase(val, Object.values(constants.orderFilterValues));
      break;
    case constants.filterKeys.SERIAL_NUMBER:
      ret = utils.isPositiveLong(val);
      break;
    case constants.filterKeys.TOKEN_ID:
      ret = EntityId.isValidEntityId(val);
      break;
    default:
      // Every token parameter should be included here. Otherwise, it will not be accepted.
      break;
  }

  return ret;
};

const getAndValidateAccountIdRequestPathParam = (accountId) => {
  const accountIdString = accountId;
  if (!EntityId.isValidEntityId(accountIdString)) {
    throw InvalidArgumentError.forParams(constants.filterKeys.ACCOUNT_ID);
  }

  return EntityId.parse(accountIdString, constants.filterKeys.TOKENID).getEncodedId();
};

/**
 * Handler function for /accounts/:accountId/nfts API
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns {Promise<void>}
 */
const getNftsByAccountId = async (req, res) => {
  // extract filters from query param
  const accountId = getAndValidateAccountIdRequestPathParam(req.params.accountId);

  // extract filters from query param
  const filters = utils.buildAndValidateFilters(req.query, validateAccountNftsQueryFilter);
  const {conditions, params, order, limit} = extractNftsQuery(filters, accountId, nftsByAccountIdParamSupportMap);

  // get transactions using id and nonce, exclude duplicate transactions. there can be at most one
  const nfts = await NftService.getNftsByFilters(conditions, params, order, limit);
  const response = {
    nfts: nfts.map((nft) => new NftViewModel(nft)),
    links: {
      next: null,
    },
  };

  if (!_.isEmpty(response.results)) {
    const lastRow = _.last(response.results);
    const lastNftTimestamp = lastRow !== undefined ? lastRow.created_timestamp : null;
    const lastSerial = lastRow !== undefined ? lastRow.serial_number : null;
    const last = [
      utils.getLastObject(constants.filterKeys.TOKENID, lastNftTimestamp),
      utils.getLastObject(constants.filterKeys.SERIAL_NUMBER, lastSerial),
    ];
    response.links.next = utils.getPaginationLink(req, response.results.length !== limit, last, order);
  }

  res.locals[constants.responseDataLabel] = response;
};

module.exports = {
  getNftsByAccountId,
};

if (utils.isTestEnv()) {
  Object.assign(module.exports, {
    extractNftsQuery,
    nftsByAccountIdParamSupportMap,
  });
}
