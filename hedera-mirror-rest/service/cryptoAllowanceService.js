/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import BaseService from './baseService';
import {CryptoAllowance} from '../model';
import {OrderSpec} from '../sql';

/**
 * CryptoAllowance business model
 */
class CryptoAllowanceService extends BaseService {
  static accountAllowanceQuery = `select * from ${CryptoAllowance.tableName}`;

  async getAccountCryptoAllowances(conditions, initParams, order, limit) {
    const {query, params} = this.getAccountAllowancesQuery(conditions, initParams, order, limit);
    const rows = await super.getRows(query, params, 'getAccountCryptoAllowances');
    return rows.map((ca) => new CryptoAllowance(ca));
  }

  getAccountAllowancesQuery(whereConditions, whereParams, order, limit) {
    const params = whereParams;
    params.push(limit);
    const query = [
      CryptoAllowanceService.accountAllowanceQuery,
      whereConditions.length > 0 ? `where ${whereConditions.join(' and ')}` : '',
      super.getOrderByQuery(OrderSpec.from(CryptoAllowance.SPENDER, order)),
      super.getLimitQuery(params.length),
    ].join('\n');

    return {query, params};
  }
}

export default new CryptoAllowanceService();
