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
    const {filters, order, ownerAccountId, limit} = query;
    const params = [ownerAccountId, limit];
    const tableAlias = `ta`;
    const tokenBalanceJoin =
      'join (select token_id,balance from token_balance where account_id = $1 and consensus_timestamp = (select max(consensus_timestamp) from account_balance_file)) tb on ' +
      tableAlias +
      '.token_id = tb.token_id';
    const tokenByIdQuery =
      `select ${tableAlias}.*
                           from ${TokenRelationship.tableName} ${tableAlias} ` +
      tokenBalanceJoin +
      `
    where ${tableAlias}.${TokenRelationship.ACCOUNT_ID} = $1`;
    let conditionsClause;
    if (filters !== undefined && filters.length != 0) {
      params.push(filters[0].value);
      conditionsClause = `
      and ${tableAlias}.${filters[0].key} ${filters[0].operator} $${params.length}`;
    }
    const limitClause = super.getLimitQuery(2);
    const orderClause = `order by ` + tableAlias + `.` + TokenRelationship.TOKEN_ID + ` ` + order;
    let sqlQuery = [tokenByIdQuery, conditionsClause, orderClause, limitClause].join('\n');

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
