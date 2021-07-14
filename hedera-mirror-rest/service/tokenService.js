/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const {Token} = require('../model');

/**
 * Token retrieval business logic
 */
class TokenService {
  static tokenByIdQuery = `select *
                           from ${Token.tableName}
                           where ${Token.TOKEN_ID} = $1`;

  async getToken(tokenId) {
    const {rows} = await pool.queryQuietly(TokenService.tokenByIdQuery, tokenId);
    return _.isEmpty(rows) ? null : new Token(rows[0]);
  }
}

module.exports = new TokenService();
