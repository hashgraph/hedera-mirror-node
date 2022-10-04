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
import {Token} from '../model';
import TokenRelationship from '../model/tokenRelationship';
import {OrderSpec} from '../sql';
import BaseService from './baseService';

/**
 * Token retrieval business logic
 */
class TokenService extends BaseService {
  static tokenByIdQuery = `select *
                           from ${TokenRelationship.tableName}
                           where ${TokenRelationship.ACCOUNT_ID} = $1`;

  async getToken(tokenId) {
    const {rows} = await pool.queryQuietly(TokenService.tokenByIdQuery, tokenId);
    return _.isEmpty(rows) ? null : new Token(rows[0]);
  }

  /**
   * Gets the full sql query and params to retrieve an account's token relationships
   *
   * @param query
   * @return {{sqlQuery: string, params: *[]}}
   */
  getQuery(query) {
    console.log('Query: ' + query);
    const {conditions, order, ownerAccountId, limit} = query;
    const params = [ownerAccountId, limit];
    const accountIdCondition = `${TokenRelationship.ACCOUNT_ID} = $1`;
    const limitClause = super.getLimitQuery(limit);
    const orderClause = super.getOrderByQuery(OrderSpec.from(TokenRelationship.TOKEN_ID, order));
    console.log('Conditions are: ' + conditions);
    let sqlQuery = [
      TokenService.tokenByIdQuery,
      `where ${conditions.join(' and ')}`,
      accountIdCondition,
      orderClause,
      limitClause,
    ].join('\n');

    return {sqlQuery, params};
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
