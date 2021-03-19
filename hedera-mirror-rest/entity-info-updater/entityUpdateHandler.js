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

const getUpdateCriteriaCount = () => {
  return {
    auto_renew_period: 0,
    deleted: 0,
    exp_time_ns: 0,
    ed25519_public_key_hex: 0,
    key: 0,
    memo: 0,
    proxy_account_id: 0,
  };
};

const getCombinedUpdateCriteriaCount = (updateList) => {
  const updateCriteriaCount = getUpdateCriteriaCount();
  for (const counts of updateList) {
    Object.keys(counts).forEach((x) => {
      updateCriteriaCount[x] += counts[x];
    });
  }

  return updateCriteriaCount;
};

/**
 * Create merge entity object that takes the base db entity and updates it with more recent network retrieved values
 * @param dbEntity
 * @param networkEntity
 * @returns {Object} Updated db entity to insert
 */
const getUpdatedEntity = (dbEntity, networkEntity) => {
  // create duplicate of db entity to update with network values to eventually insert into db
  let updateEntity = {
    ...dbEntity,
  };

  let updateNeeded = false;
  const updateCriteriaCount = getUpdateCriteriaCount();

  if (dbEntity.auto_renew_period !== networkEntity.autoRenewPeriod.seconds.toString()) {
    updateEntity.auto_renew_period = networkEntity.autoRenewPeriod.seconds.toString();
    updateNeeded = true;
    updateCriteriaCount.auto_renew_period += 1;
    logger.trace(
      `auto_renew_period mismatch on ${dbEntity.id}, db: ${dbEntity.auto_renew_period}, network: ${networkEntity.autoRenewPeriod.seconds}`
    );
  }

  // Accounts can't be undeleted
  if (dbEntity.deleted !== networkEntity.isDeleted && networkEntity.isDeleted === true) {
    updateEntity.deleted = networkEntity.isDeleted;
    updateNeeded = true;
    updateCriteriaCount.deleted += 1;
    logger.trace(`deleted mismatch on ${dbEntity.id}, db: ${dbEntity.deleted}, network: ${networkEntity.isDeleted}`);
  }

  const ns = utils.secNsToNs(networkEntity.expirationTime.seconds, networkEntity.expirationTime.nanos);
  if (dbEntity.exp_time_ns !== ns) {
    updateEntity.exp_time_ns = ns;
    updateNeeded = true;
    updateCriteriaCount.exp_time_ns += 1;
    logger.trace(`expirationTime mismatch on ${dbEntity.id}, db: ${dbEntity.exp_time_ns}, network: ${ns}`);
  }

  const {protoBuffer, ed25519Hex} = utils.getBufferAndEd25519HexFromKey(networkEntity.key);
  if (!utils.isEd25519PublicHexMatch(dbEntity.ed25519_public_key_hex, ed25519Hex)) {
    updateEntity.ed25519_public_key_hex = ed25519Hex;
    updateNeeded = true;
    updateCriteriaCount.ed25519_public_key_hex += 1;
    logger.trace(
      `ed25519 public key mismatch on ${dbEntity.id}, db: ${dbEntity.ed25519_public_key_hex}, network: ${ed25519Hex}`
    );
  }

  if (Buffer.compare(updateEntity.key, protoBuffer) !== 0) {
    updateEntity.key = protoBuffer;
    updateNeeded = true;
    updateCriteriaCount.key += 1;
    logger.trace(`key mismatch on ${dbEntity.id}, db: ${dbEntity.key}, network: ${protoBuffer}`);
  }

  if (networkEntity.proxyAccountId !== null && dbEntity.proxy_account_id !== networkEntity.proxyAccountId) {
    updateEntity.proxy_account_id = networkEntity.proxyAccountId;
    updateNeeded = true;
    updateCriteriaCount.proxy_account_id += 1;
    logger.trace(
      `proxy_account_id mismatch on ${dbEntity.id}, db: ${dbEntity.proxy_account_id}, network: ${networkEntity.proxyAccountId}`
    );
  }

  if (updateNeeded) {
    logger.trace(
      `created update entity for ${dbEntity.id}: ${JSON.stringify(
        updateEntity
      )} based on network entity: ${JSON.stringify(networkEntity)}, updateCriteriaCount: ${JSON.stringify(
        updateCriteriaCount
      )}.`
    );
  }

  return updateNeeded ? {updateEntity, updateCriteriaCount} : null;
};

/**
 * Using csv entity id, compare entity information in mirror db and network state to obtained verified object
 * @param csvEntity
 * @returns {Promise<null>}
 */
const getVerifiedEntity = async (csvEntity) => {
  const dbEntity = await dbEntityService.getEntity(csvEntity.entity);
  if (!dbEntity) {
    logger.debug(`Entity ${csvEntity.entity.id} was missing from db, skipping`);
    return null;
  }

  if (dbEntity.fk_entity_type_id !== 1) {
    logger.debug(`Currently only account entities are supported, skipping`);
    return null;
  }

  let networkEntity;
  try {
    networkEntity = await networkEntityService.getAccountInfo(csvEntity.entity);
  } catch (e) {
    logger.debug(`Error retrieving account ${csvEntity.entity} from network, skipping, error: ${e}`);
    return null;
  }

  return getUpdatedEntity(dbEntity, networkEntity);
};

/**
 * Retrieve list of objects to update mirror db with. List represents correct information for out-of-date entities
 * @param csvEntities
 * @returns {Promise<unknown[]|[]>}
 */
const getUpdateList = async (csvEntities) => {
  logger.info(`Validating entities against db and network entries ...`);
  const mergeStart = process.hrtime();
  let updateList = [];
  if (!csvEntities || csvEntities.length === 0) {
    return updateList;
  }

  const startBalance = await networkEntityService.getAccountBalance();

  updateList = (await Promise.all(csvEntities.map(getVerifiedEntity))).filter((x) => !!x);
  const elapsedTime = process.hrtime(mergeStart);

  logger.info(
    `${csvEntities.length} entities were retrieved and compared in ${utils.getElapsedTimeString(elapsedTime)},
    ${updateList.length} were found to be out-of-date`
  );
  logger.debug(
    `updateCriteriaCount ${JSON.stringify(
      getCombinedUpdateCriteriaCount(updateList.map((x) => x.updateCriteriaCount))
    )}`
  );

  const endBalance = await networkEntityService.getAccountBalance();
  logger.debug(`Network accountInfo calls cost ${startBalance.hbars.toTinybars() - endBalance.hbars.toTinybars()} tℏ`);

  return updateList.map((x) => x.updateEntity);
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

  logger.info(`Updating ${entitiesToUpdate.length} stale db entries with updated information ...`);
  const updateStart = process.hrtime();
  const updatedList = await Promise.all(entitiesToUpdate.map(dbEntityService.updateEntity));
  const elapsedTime = process.hrtime(updateStart);

  logger.info(`Updated ${updatedList.length} entities in ${utils.getElapsedTimeString(elapsedTime)}`);
};

module.exports = {
  updateStaleDBEntities,
  getUpdateList,
};
