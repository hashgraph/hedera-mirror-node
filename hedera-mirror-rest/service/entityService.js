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

const {NotFoundError} = require('../errors/notFoundError');

/**
 * Entity retrieval business logic
 */
class EntityService extends BaseService {
  constructor() {
    super();
  }

  static entityFromAliasQuery = `select ${Entity.ID} 
    from ${Entity.tableName}`;

  static aliasColumns = [Entity.SHARD, Entity.REALM, Entity.ALIAS];
  static aliasConditions = [`coalesce(${Entity.DELETED}, false) <> true`];

  /**
   * Retrieves the entity containing matching the given alias
   *
   * @param {AccountAlias} accountAlias accountAlias
   * @return {Promise<Entity>} raw entity object
   */
  async getAccountFromAlias(accountAlias) {
    const params = [];
    const conditions = [].concat(EntityService.aliasConditions);

    EntityService.aliasColumns
      .filter((column) => accountAlias[column] !== null)
      .forEach((column) => {
        const length = params.push(accountAlias[column]);
        conditions.push(`${column} = $${length}`);
      });

    const aliasQuery = `${EntityService.entityFromAliasQuery} where ${conditions.join(' and ')}`;

    const rows = await super.getRows(aliasQuery, params, 'getAccountFromAlias');

    if (_.isEmpty(rows)) {
      return null;
    } else if (rows.length > 1) {
      logger.error(`Incorrect db state: ${rows.length} alive entities matching alias ${accountAlias}`);
      throw new Error(`Multiple alive entities matching alias`);
    }

    return new Entity(rows[0]);
  }

  /**
   * Gets the encoded account id from the account alias string. Throws {@link InvalidArgumentError} if the account alias
   * string is invalid,
   * @param {AccountAlias} accountAlias the account alias object
   * @return {Promise}
   */
  async getAccountIdFromAlias(accountAlias) {
    const entity = await this.getAccountFromAlias(accountAlias);
    if (_.isNil(entity)) {
      throw new NotFoundError('No account with a matching alias found');
    }

    return entity.id;
  }
}

module.exports = new EntityService();
