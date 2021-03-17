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

// external libraries
const log4js = require('log4js');

// local
const networkEntityService = require('./networkEntityService');
const dbEntityService = require('./dbEntityService');
const utils = require('./utils');

const logger = log4js.getLogger();

/**
 * Create merge entity object that takes the based db entity and updates it with more recent network retrieved values
 * @param dbEntity
 * @param networkEntity
 * @returns {Object} Updated db entity to insert
 */
const getUpdatedEntity = (dbEntity, networkEntity) => {
  // create duplicate of db entity to update with network values to eventually insert into db
  const updateEntity = {
    ...dbEntity,
  };

  let updateNeeded = false;

  if (dbEntity.auto_renew_period !== networkEntity.autoRenewPeriod.seconds.toString()) {
    updateEntity.auto_renew_period = networkEntity.autoRenewPeriod.seconds.toString();
    updateNeeded = true;
    logger.trace(
      `auto_renew_period mismatch, db: ${dbEntity.auto_renew_period}, network: ${networkEntity.autoRenewPeriod.seconds}`
    );
  }

  // Accounts can't be undeleted
  if (dbEntity.deleted !== networkEntity.isDeleted && networkEntity.isDeleted === true) {
    updateEntity.deleted = networkEntity.isDeleted;
    updateNeeded = true;
    logger.trace(`deleted mismatch, db: ${dbEntity.deleted}, network: ${networkEntity.isDeleted}`);
  }

  const ns = utils.secNsToNs(networkEntity.expirationTime.seconds, networkEntity.expirationTime.nanos);
  if (dbEntity.exp_time_ns !== ns) {
    updateEntity.exp_time_ns = ns;
    updateNeeded = true;
    logger.trace(
      `expirationTime mismatch, db: ${dbEntity.exp_time_ns}, network: ${JSON.stringify(networkEntity.expirationTime)}`
    );
  }

  // mirror t_entities.ed25519_public_key_hex is created based on key so we can use this for both key and hex comparison
  const {protoBuffer, ed25519Hex} = utils.getBufferAndEd25519HexFromKey(networkEntity.key);
  if (dbEntity.ed25519_public_key_hex !== ed25519Hex) {
    updateEntity.ed25519_public_key_hex = ed25519Hex;
    updateEntity.key = protoBuffer;
    updateNeeded = true;
    logger.trace(`ed25519 public key mismatch, db: ${dbEntity.ed25519_public_key_hex}, network: ${ed25519Hex}`);
    logger.trace(
      `key mismatch, db: ${JSON.stringify(dbEntity.key)}, network: ${JSON.stringify(
        networkEntity.key._keys[0]._keyData
      )}`
    );
  }

  if (dbEntity.proxy_account_id !== networkEntity.proxyAccountId) {
    updateEntity.proxy_account_id = networkEntity.proxyAccountId;
    updateNeeded = true;
    logger.trace(
      `proxy_account_id mismatch, db: ${dbEntity.proxy_account_id}, network: ${networkEntity.proxyAccountId}`
    );
  }

  if (updateNeeded) {
    logger.trace(
      `created update entity: ${JSON.stringify(updateEntity)} to replace current db entity ${JSON.stringify(dbEntity)}`
    );
  }

  return updateNeeded ? updateEntity : null;
};

/**
 * Using csv entity id, compare entity information in mirror db and network state to obtained verified object
 * @param csvEntity
 * @returns {Promise<null>}
 */
const getVerifiedEntity = async (csvEntity) => {
  let networkEntity;
  try {
    networkEntity = await networkEntityService.getAccountInfo(csvEntity.entity);
  } catch (e) {
    logger.trace(`Error retrieving account ${csvEntity.entity} from network: ${e}`);
    return null;
  }
  // const networkEntity = await networkEntityService.getAccountInfo(csvEntity.entity);
  const dbEntity = await dbEntityService.getEntity(csvEntity.entity);

  if (!networkEntity.key || networkEntity.key._keys === undefined) {
    logger.trace(`Null network entity key, skipping`);
    return null;
  }

  if (!dbEntity) {
    logger.trace(`Null db entity, skipping`);
    return null;
  }

  return getUpdatedEntity(dbEntity, networkEntity);
};

/**
 * Retrieve list of objects to update mirror db with. List represent correct information for out of date entities
 * @param csvEntities
 * @returns {Promise<unknown[]|[]>}
 */
const getUpdateList = async (csvEntities) => {
  logger.info(`Validating entities entities against db entries ...`);
  let updateList = [];
  if (!csvEntities || csvEntities.length === 0) {
    return updateList;
  }

  const startBalance = await networkEntityService.getAccountBalance();

  updateList = (await Promise.all(csvEntities.map(getVerifiedEntity))).filter((x) => !!x);
  logger.info(`Update list of length ${updateList.length} retrieved`);

  const endBalance = await networkEntityService.getAccountBalance();
  logger.info(
    `*** Network accountInfo calls cost ${endBalance.hbars.toTinybars() - startBalance.hbars.toTinybars()} tℏ.
    start: ${startBalance.hbars.toTinybars()} tℏ, end: ${endBalance.hbars.toTinybars()} tℏ`
  );

  return updateList;
};

/**
 * Update mirror db with list of corrected entities
 * @param entitiesToUpdate
 * @returns {Promise<void>}
 */
const updateStaleDBEntities = async (entitiesToUpdate) => {
  if (!entitiesToUpdate || entitiesToUpdate.length === 0) {
    logger.info(`No entities to update, skipping update`);
    return;
  }

  const updatedList = await Promise.all(entitiesToUpdate.map(dbEntityService.updateEntity));

  logger.info(`Updated ${updatedList.length} entities`);
};

module.exports = {
  updateStaleDBEntities,
  getUpdateList,
};
