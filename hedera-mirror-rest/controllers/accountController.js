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

const Bound = require('./bound');
const BaseController = require('./baseController');
const {EntityService, NftService} = require('../service');
const {NftViewModel} = require('../viewmodel');

// errors
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

class AccountController extends BaseController {
  validateFilters(serialNumberBound, tokenIdBound, spenderIdFilters) {
    if (!serialNumberBound.isEmpty() && tokenIdBound.isEmpty()) {
      throw new InvalidArgumentError(`Cannot search NFTs with serialnumber without a tokenId parameter filter`);
    }

    if (serialNumberBound.hasLower() && !tokenIdBound.hasLower()) {
      throw new InvalidArgumentError(
        'A lower bound serialnumber filter requires a lower bound tokenId parameter filter'
      );
    }

    if (serialNumberBound.hasUpper() && !tokenIdBound.hasUpper()) {
      throw new InvalidArgumentError(
        'An upper bound serialnumber filter requires an upper bound tokenId parameter filter'
      );
    }

    let invalidRange = false;
    if (
      spenderIdFilters.map((f) => f.operator).filter((o) => o === utils.opsMap.lte || o === utils.opsMap.lt).length > 1
    ) {
      invalidRange = true;
    }
    if (
      spenderIdFilters.map((f) => f.operator).filter((o) => o === utils.opsMap.gte || o === utils.opsMap.gt).length > 1
    ) {
      invalidRange = true;
    }
    if (invalidRange) {
      throw new InvalidArgumentError(`Multiple range params not allowed for spender.id`);
    }

    if (spenderIdFilters.map((f) => f.operator).filter((o) => utils.opsMap.ne === o).length > 1) {
      throw new InvalidArgumentError(`Not equals (ne) comparison operator is not supported`);
    }
  }

  extractNftMultiUnionQuery(filters, ownerAccountId) {
    const serialNumberBound = new Bound();
    const tokenIdBound = new Bound();
    const bounds = {
      [constants.filterKeys.SERIAL_NUMBER]: serialNumberBound,
      [constants.filterKeys.TOKEN_ID]: tokenIdBound,
    };
    let limit = defaultLimit;
    let order = constants.orderFilterValues.DESC;
    const spenderIdFilters = [];
    const spenderIdInFilters = [];

    for (const filter of filters) {
      switch (filter.key) {
        case constants.filterKeys.SERIAL_NUMBER:
        case constants.filterKeys.TOKEN_ID:
          try {
            bounds[filter.key].parse(filter);
          } catch (e) {
            throw new InvalidArgumentError(`Multiple range params not allowed for ${filter.key}`);
          }
          break;
        case constants.filterKeys.LIMIT:
          limit = filter.value;
          break;
        case constants.filterKeys.ORDER:
          order = filter.value;
          break;
        case constants.filterKeys.SPENDER_ID:
          filter.operator === utils.opsMap.eq ? spenderIdInFilters.push(filter) : spenderIdFilters.push(filter);
        default:
          break;
      }
    }

    this.validateFilters(serialNumberBound, tokenIdBound, spenderIdFilters);

    let lower = this.getLowerFilters(tokenIdBound, serialNumberBound);
    let inner = this.getInnerFilters(tokenIdBound, serialNumberBound);
    let upper = this.getUpperFilters(tokenIdBound, serialNumberBound);
    return {
      bounds,
      lower,
      inner,
      upper,
      order,
      ownerAccountId,
      limit,
      spenderIdInFilters,
      spenderIdFilters,
    };
  }

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/nfts API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getNftsByAccountId = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query);
    const query = this.extractNftMultiUnionQuery(filters, accountId);
    const nonFungibleTokens = await NftService.getNfts(query);
    const nfts = nonFungibleTokens.map((nft) => new NftViewModel(nft));

    res.locals[constants.responseDataLabel] = {
      nfts,
      links: {
        next: this.getPaginationLink(req, nfts, query.bounds, query.limit, query.order),
      },
    };
  };

  /**
   * Gets the pagination link for the accounts nfts query
   * @param {Request} req
   * @param {NftViewModel[]} nftViewModels
   * @param {{string: Bound}} bounds
   * @param {number} limit
   * @param {string} order
   * @return {string|null}
   */
  getPaginationLink(req, nftViewModels, bounds, limit, order) {
    const serialBound = bounds[constants.filterKeys.SERIAL_NUMBER];
    const tokenIdBound = bounds[constants.filterKeys.TOKEN_ID];

    if (nftViewModels.length < limit || (serialBound.hasEqual() && tokenIdBound.hasEqual())) {
      // fetched all matching rows or the query is for a specific serial number and token id combination
      return null;
    }

    const lastRow = _.last(nftViewModels);
    const lastValues = {};
    if (tokenIdBound.hasBound() || tokenIdBound.isEmpty()) {
      lastValues[constants.filterKeys.TOKEN_ID] = {value: lastRow.token_id, inclusive: !tokenIdBound.hasEqual()};
    }
    if (serialBound.hasBound() || serialBound.isEmpty()) {
      lastValues[constants.filterKeys.SERIAL_NUMBER] = {value: lastRow.serial_number};
    }

    return utils.getPaginationLink(req, false, lastValues, order);
  }
}

module.exports = new AccountController();
