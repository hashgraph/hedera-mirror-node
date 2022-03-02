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

const BaseService = require('./baseService');
const {Entity} = require('../model');

/**
 * Entity retrieval business logic
 */
class EntityService extends BaseService {
  constructor() {
    super();
  }

  static entityFromAliasQuery = `select ${Entity.ID} 
    from ${Entity.tableName} 
    where coalesce(${Entity.DELETED}, false) <> true and ${Entity.ALIAS} = $1`;

  /**
   * Gets the query to find the alive entity matching the account alias string.
   * @param {AccountAlias} accountAlias
   * @return {{query: string, params: *[]}}
   */
  getAccountAliasQuery = (accountAlias) => {
    const query = `select ${Entity.ID} from entity where coalesce(deleted, false) <> true and alias = $1`;
    const params = [accountAlias.alias];
    return {query, params};
  };

  /**
   * Retrieves the entity containing matching the given alias
   *
   * @param {AccountAlias} accountAlias accountAlias
   * @return {Promise<Object>} raw entity object
   */
  async getAccountFromAlias(accountAlias) {
    const rows = await super.getRows(EntityService.entityFromAliasQuery, [accountAlias.alias], 'getAccountFromAlias');

    if (_.isEmpty(rows)) {
      return null;
    } else if (rows.length > 1) {
      logger.error(`Incorrect db state: ${rows.length} alive entities matching alias ${accountAlias}`);
      throw new Error();
    }

    return new Entity(rows[0]);
  }
}

module.exports = new EntityService();
