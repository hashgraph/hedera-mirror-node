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

import {getResponseLimit} from '../config';
import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import BaseController from './baseController';
import {InvalidArgumentError} from '../errors';
import {EntityService, TokenService} from '../service';
import * as utils from '../utils';
import TokenRelationshipViewModel from '../viewmodel/tokenRelationshipViewModel';
import TokenRelationship from '../model/tokenRelationship';

const {default: defaultLimit} = getResponseLimit();

const tokenRelationshipDefaultLimit = 25;
const tokenRelationshipMaxLimit = 100;

class TokenController extends BaseController {
  /**
   * Extracts multiple queries to be combined in union.
   *
   * @param {[]} filters req filters
   * @param {BigInt} ownerAccountId Encoded owner entityId
   * @returns {{bounds: {string: Bound}, lower: *[], inner: *[], upper: *[],
   *  accountId: BigInt, order: 'asc'|'desc', limit: number}}
   */
  extractTokensRelationshipQuery = (filters, ownerAccountId) => {
    let conditions = [];
    let limit = tokenRelationshipDefaultLimit;
    let order = orderFilterValues.DESC;

    for (const filter of filters) {
      switch (filter.key) {
        case filterKeys.TOKEN_ID:
          //new Bound(filterKeys.TOKEN_ID, 'token_id').parse(filter);
          if (utils.opsMap.ne === filter.operator) {
            throw new InvalidArgumentError(`Not equal (ne) comparison operator is not supported for ${filter.key}`);
          }
          conditions = [{key: TokenRelationship.TOKEN_ID, operator: filter.operator, value: filter.value}];
          break;
        case filterKeys.LIMIT:
          if (filter.value > tokenRelationshipMaxLimit) {
            limit = tokenRelationshipMaxLimit;
          } else {
            limit = filter.value;
          }
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        default:
          break;
      }
    }

    return {
      conditions,
      order,
      ownerAccountId,
      limit,
    };
  };

  /**
   * Handler function for /accounts/:idOrAliasOrEvmAddress/tokens API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getTokenRelationships = async (req, res) => {
    const accountId = await EntityService.getEncodedId(req.params[filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS]);
    const filters = utils.buildAndValidateFilters(req.query);
    const query = this.extractTokensRelationshipQuery(filters, accountId);
    const tokenRelationships = await TokenService.getTokens(query);
    const tokens = tokenRelationships.map((token) => new TokenRelationshipViewModel(token));

    res.locals[responseDataLabel] = {
      tokens,
      links: {
        next: null,
      },
    };
  };
}

export default new TokenController();
