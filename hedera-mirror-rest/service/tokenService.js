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

import _ from 'lodash';
import {Nft, Token} from '../model';
import TokenRelationship from '../model/tokenRelationship';

/**
 * Token retrieval business logic
 */
class TokenService {
  static tokenByIdQuery = `select *
                           from ${TokenRelationship.tableName}
                           where ${TokenRelationship.TOKEN_ID} = $1`;

  async getToken(tokenId) {
    const {rows} = await pool.queryQuietly(TokenService.tokenByIdQuery, tokenId);
    return _.isEmpty(rows) ? null : new Token(rows[0]);
  }

  /**
   * Gets the tokens for the query
   *
   * @param query
   * @return {Promise<Token[]>}
   */
  async getTokens(query) {
    const {sqlQuery, params} = this.getQuery(query);
    const rows = await super.getRows(sqlQuery, params, 'getTokens');
    return rows.map((ta) => new TokenRelationship(ta));
  }
}

export default new TokenService();
