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
import {CryptoAllowanceService, EntityService, NftService} from '../service';
import {CryptoAllowanceViewModel, NftViewModel} from '../viewmodel';
import * as utils from '../utils';
import {CryptoAllowance} from '../model';
import _ from 'lodash';

const {default: defaultLimit} = getResponseLimit();

class TokenController extends BaseController {
  /**
   * Extracts SQL where conditions, params, order, and limit from crypto allowances query
   *
   * @param {[]} filters parsed and validated filters
   * @param {Number} tokenId parsed accountId from path
   */
  extractTokensRelationshipQuery = (filters, tokenId) => {
    let limit = defaultLimit;
    let order = orderFilterValues.DESC;
    const conditions = [`${Token.TOKEN_ID} = $1`];
    const params = [tokenId];
    const spenderInValues = [];

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.SPENDER_ID:
          if (utils.opsMap.ne === filter.operator) {
            throw new InvalidArgumentError(`Not equal (ne) comparison operator is not supported for ${filter.key}`);
          }
          this.updateConditionsAndParamsWithInValues(
            filter,
            spenderInValues,
            params,
            conditions,
            CryptoAllowance.SPENDER,
            conditions.length + 1
          );
          break;
        case filterKeys.LIMIT:
          limit = filter.value;
          break;
        case filterKeys.ORDER:
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
   * Handler function for /accounts/:idOrAliasOrEvmAddress/tokens API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getTokensByAccountId = async (req, res) => {
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
}

export default new TokenController();
