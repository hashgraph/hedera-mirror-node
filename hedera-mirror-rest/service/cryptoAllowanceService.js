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

const {CryptoAllowance} = require('../model');
const BaseService = require('./baseService');

/**
 * CryptoAllowance business model
 */
class CryptoAllowanceService extends BaseService {
  static accountAllowanceQuery = `select
    ${CryptoAllowance.AMOUNT},
    ${CryptoAllowance.OWNER},
    ${CryptoAllowance.PAYER_ACCOUNT_ID},
    ${CryptoAllowance.SPENDER},
    ${CryptoAllowance.TIMESTAMP_RANGE}
    from ${CryptoAllowance.tableName}`;

  async getAccountCrytoAllowances(conditions, initParams, order, limit) {
    const [query, params] = this.getAccountCryptoAllowancesWithFiltersQuery(conditions, initParams, order, limit);

    const rows = await super.getRows(query, params, 'getAccountCryptoAllowancesWithFilters');
    return rows.map((ca) => new CryptoAllowance(ca));
  }

  getAccountCryptoAllowancesWithFiltersQuery(whereConditions, whereParams, spenderOrder, limit) {
    const params = whereParams;
    const query = [
      CryptoAllowanceService.accountAllowanceQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(CryptoAllowance.SPENDER, spenderOrder),
      super.getLimitQuery(params.length + 1),
    ].join('\n');
    params.push(limit);

    return [query, params];
  }
}

module.exports = new CryptoAllowanceService();
