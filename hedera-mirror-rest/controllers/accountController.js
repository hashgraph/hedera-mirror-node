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

import Bound from './bound';
import BaseController from './baseController';
import {getResponseLimit} from '../config';
import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import {InvalidArgumentError} from '../errors';
import {FileData} from '../model';
import {EntityService, NftService, StakingRewardTransferService} from '../service';
import {NftViewModel, StakingRewardTransferViewModel} from '../viewmodel';
import * as utils from '../utils';

const {default: defaultLimit} = getResponseLimit();

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

    if (spenderIdFilters.some((f) => f.operator === utils.opsMap.ne)) {
      throw new InvalidArgumentError(`Not equals (ne) comparison operator is not supported`);
    }
  }

  extractNftMultiUnionQuery(filters, ownerAccountId) {
    const bounds = {
      primary: new Bound(filterKeys.TOKEN_ID, 'token_id'),
      secondary: new Bound(filterKeys.SERIAL_NUMBER, 'serial_number'),
    };
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    const spenderIdFilters = [];
    const spenderIdInFilters = [];

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.SERIAL_NUMBER:
          bounds.secondary.parse(filter);
          break;
        case filterKeys.TOKEN_ID:
          bounds.primary.parse(filter);
          break;
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        case filterKeys.SPENDER_ID:
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
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query);
    const query = this.extractNftMultiUnionQuery(filters, accountId);
    const nonFungibleTokens = await NftService.getNfts(query);
    const nfts = nonFungibleTokens.map((nft) => new NftViewModel(nft));

    res.locals[responseDataLabel] = {
      nfts,
      links: {
        next: this.getPaginationLink(req, nfts, query.bounds, query.limit, query.order),
      },
    };
  };

  extractStakingRewardsQuery(filters, accountId) {
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    let whereQuery = [];
    const params = [accountId];

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        case filterKeys.TIMESTAMP:
          if (utils.opsMap.ne === filter.operator) {
            throw new InvalidArgumentError(`Not equals (ne) operator is not supported for ${filterKeys.TIMESTAMP}`);
          }
          whereQuery.push('srt.' + FileData.CONSENSUS_TIMESTAMP + ' ' + `${filter.operator}` + ' ' + filter.value);
          break;
        default:
          break;
      }
    }

    return {
      order,
      limit,
      whereQuery,
      params,
    };
  }

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/rewards API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  listStakingRewardsByAccountId = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query);
    const query = this.extractStakingRewardsQuery(filters, accountId);
    const stakingRewardsTransfers = await StakingRewardTransferService.getRewards(
      accountId,
      query.order,
      query.limit,
      query.whereQuery,
      query.params
    );
    const rewards = stakingRewardsTransfers.map((reward) => new StakingRewardTransferViewModel(reward));
    const response = {
      rewards,
      links: {
        next: null,
      },
    };

    if (response.rewards.length === query.limit) {
      const lastRow = _.last(response.rewards);
      const last = {
        [filterKeys.TIMESTAMP]: lastRow.consensus_timestamp,
      };
      response.links.next = utils.getPaginationLink(req, false, last, query.order);
    }

    res.locals[responseDataLabel] = response;
  };
}

export default new AccountController();
