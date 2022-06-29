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
  validateFilters(bounds, spenderIdFilters) {
    this.validateBounds(bounds);

    const spenderOperators = spenderIdFilters.map((f) => f.operator);
    if (
      spenderOperators.filter((o) => o === utils.opsMap.lte || o === utils.opsMap.lt).length > 1 ||
      spenderOperators.filter((o) => o === utils.opsMap.gte || o === utils.opsMap.gt).length > 1
    ) {
      throw new InvalidArgumentError(`Multiple range params not allowed for spender.id`);
    }

    if (spenderIdFilters.map((f) => f.operator).filter((o) => utils.opsMap.ne === o).length > 0) {
      throw new InvalidArgumentError(`Not equals (ne) comparison operator is not supported`);
    }
  }

  extractNftMultiUnionQuery(filters, ownerAccountId) {
    const bounds = {
      primary: new Bound(constants.filterKeys.TOKEN_ID, 'token_id'),
      secondary: new Bound(constants.filterKeys.SERIAL_NUMBER, 'serial_number'),
    };
    let limit = defaultLimit;
    let order = constants.orderFilterValues.DESC;
    const spenderIdFilters = [];
    const spenderIdInFilters = [];

    for (const filter of filters) {
      switch (filter.key) {
        case constants.filterKeys.SERIAL_NUMBER:
          bounds.secondary.parse(filter);
          break;
        case constants.filterKeys.TOKEN_ID:
          bounds.primary.parse(filter);
          break;
        case constants.filterKeys.LIMIT:
          limit = filter.value;
          break;
        case constants.filterKeys.ORDER:
          order = filter.value;
          break;
        case constants.filterKeys.SPENDER_ID:
          filter.operator === utils.opsMap.eq ? spenderIdInFilters.push(filter) : spenderIdFilters.push(filter);
          break;
        default:
          break;
      }
    }

    this.validateFilters(bounds, spenderIdFilters);

    const lower = this.getLowerFilters(bounds);
    const inner = this.getInnerFilters(bounds);
    const upper = this.getUpperFilters(bounds);
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
}

module.exports = new AccountController();
