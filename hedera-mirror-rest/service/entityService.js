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
const {Contract, Entity} = require('../model');

const constants = require('../constants');
const AccountAlias = require('../accountAlias');
const EntityId = require('../entityId');

const {InvalidArgumentError} = require('../errors/invalidArgumentError');
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
  static entityFromEvmAddressQuery = `select ${Entity.ID}
    from ${Entity.tableName}
    where ${Entity.DELETED} <> true and ${Entity.EVM_ADDRESS} = $1
    union all
    select ${Contract.ID}
    from ${Contract.tableName}
    where ${Contract.DELETED} <> true and ${Contract.EVM_ADDRESS} = $1`;
  // use a small column in existence check to reduce return payload size
  static entityExistenceQuery = `select ${Entity.TYPE}
    from ${Entity.tableName} where ${Entity.ID} = $1`;

  static aliasColumns = [Entity.SHARD, Entity.REALM, Entity.ALIAS];
  static aliasConditions = [`coalesce(${Entity.DELETED}, false) <> true`];

  static missingEntityIdMessage = 'No entity with a matching id found';
  static missingAccountAlias = 'No account with a matching alias found';
  static multipleAliasMatch = `Multiple alive entities matching alias`;
  static multipleEvmAddressMatch = `Multiple alive entities matching evm address`;

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
      throw new Error(EntityService.multipleAliasMatch);
    }

    return new Entity(rows[0]);
  }

  /**
   * Checks if provided accountId maps to a valid entity
   * @param {String} accountId
   * @returns {Promise} valid flag
   */
  async isValidAccount(accountId) {
    const entity = await super.getSingleRow(EntityService.entityExistenceQuery, [accountId], 'isValidAccount');
    return !_.isNil(entity);
  }

  /**
   * Gets the encoded account id from the account alias string.
   * @param {AccountAlias} accountAlias the account alias object
   * @return {Promise<BigInt>}
   */
  async getAccountIdFromAlias(accountAlias) {
    const entity = await this.getAccountFromAlias(accountAlias);
    if (_.isNil(entity)) {
      throw new NotFoundError(EntityService.missingAccountAlias);
    }

    return entity.id;
  }

  /**
   * Gets the encoded
   *
   * @param {String} evmAddress
   * @return {Promise<BigInt|Number>}
   */
  async getEntityIdFromEvmAddress(evmAddress) {
    const evmAddressBytes = Buffer.from(evmAddress, 'hex');
    const rows = await this.getRows(EntityService.entityFromEvmAddressQuery, evmAddressBytes);
    if (rows.length === 0) {
      throw new NotFoundError();
    } else if (rows.length > 1) {
      logger.error(`Incorrect db state: ${rows.length} alive entities matching evm address ${evmAddress}`);
      throw new Error(EntityService.multipleEvmAddressMatch);
    }

    return rows[0].id;
  }

  /**
   * Retrieve the encodedId of a validated EntityId from a shard.realm.num string, encoded id string, evm address with
   * optional shard and realm, or alias string.
   * Throws {@link InvalidArgumentError} if the entity id string is invalid
   * Throws {@link NotFoundError} if the account is not present when retrieving by alias or the entity is not present
   * when retrieving by evm address
   *
   * @param {String} entityIdString
   * @param {String} paramName the parameter name
   * @returns {Promise} entityId
   */
  async getEncodedId(entityIdString, paramName = constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS) {
    try {
      if (EntityId.isValidEntityId(entityIdString)) {
        const entityId = EntityId.parse(entityIdString, {paramName});
        return entityId.evmAddress === null
          ? entityId.getEncodedId()
          : await this.getEntityIdFromEvmAddress(entityId.evmAddress);
      } else if (AccountAlias.isValid(entityIdString)) {
        return await this.getAccountIdFromAlias(AccountAlias.fromString(entityIdString));
      }
    } catch (ex) {
      if (ex instanceof InvalidArgumentError) {
        throw InvalidArgumentError.forParams(paramName);
      }
      // rethrow
      throw ex;
    }

    throw InvalidArgumentError.forParams(paramName);
  }
}

module.exports = new EntityService();
