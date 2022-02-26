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

const {Nft} = require('../model');
const BaseService = require('./baseService');
const {
  response: {
    limit: {default: defaultLimit},
  },
} = require('../config');
const {orderFilterValues} = require('../constants');

/**
 * Nft business model
 */
class NftService extends BaseService {
  static nftByIdQuery = 'select * from nft where token_id = $1 and serial_number = $2';
  static nftsByAccountIdQuery = 'select * from nft where account_id = $1';

  static nftQuery = `select
    ${Nft.ACCOUNT_ID},
    ${Nft.CREATED_TIMESTAMP},
    ${Nft.DELETED},
    ${Nft.METADATA},
    ${Nft.MODIFIED_TIMESTAMP},
    ${Nft.SERIAL_NUMBER},
    ${Nft.TOKEN_ID}
    from ${Nft.tableName}`;

  async getNft(tokenId, serialNumber) {
    const {rows} = await pool.queryQuietly(NftService.nftByIdQuery, [tokenId, serialNumber]);
    return _.isEmpty(rows) ? null : new Nft(rows[0]);
  }

  getNftsFiltersQuery(whereConditions, whereParams, nftOrder, limit) {
    const params = whereParams;
    const orderClause = [super.getOrderByQuery(Nft.TOKEN_ID, nftOrder), `${Nft.SERIAL_NUMBER} ${nftOrder}`].join(', ');
    const query = [
      NftService.nftQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      orderClause,
      super.getLimitQuery(params.length + 1),
    ].join('\n');
    params.push(limit);

    return [query, params];
  }

  /**
   * Retrieves nfts based on various filters
   *
   * @param whereConditions the conditions to build a where clause out of
   * @param whereParams the parameters for the where clause
   * @param nftOrder the sorting order for field token_id and serial
   * @param limit the limit parameter for the query
   * @returns {Promise<*[]|*>} the result of the getNftsByFilters query
   */
  async getNftsByFilters(
    whereConditions = [],
    whereParams = [],
    nftOrder = orderFilterValues.DESC,
    limit = defaultLimit
  ) {
    const [query, params] = this.getNftsFiltersQuery(whereConditions, whereParams, nftOrder, limit);
    const rows = await super.getRows(query, params, 'getNftsByFilters');
    return rows.map((nft) => new Nft(nft));
  }
}

module.exports = new NftService();
