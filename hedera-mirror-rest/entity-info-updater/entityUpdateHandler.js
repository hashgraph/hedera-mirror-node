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
const config = require('./config');

const logger = log4js.getLogger();

const getUpdateCriteriaCount = () => {
  return {
    auto_renew_period: 0,
    deleted: 0,
    expiration_timestamp: 0,
    key: 0,
    memo: 0,
    proxy_account_id: 0,
  };
};

const getCombinedUpdateCriteriaCount = (updateList) => {
  const updateCriteriaCount = getUpdateCriteriaCount();
  for (const counts of updateList) {
    if (counts !== undefined) {
      Object.keys(counts).forEach((x) => {
        updateCriteriaCount[x] += counts[x];
      });
    }
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

  // compare expiration times as BigInts
  const ns = utils.timestampToNs(networkEntity.expirationTime.seconds, networkEntity.expirationTime.nanos);
  const dbExpTimeNs = dbEntity.expiration_timestamp == null ? null : BigInt(dbEntity.expiration_timestamp);
  if (dbExpTimeNs !== ns) {
    updateEntity.expiration_timestamp = ns.toString();
    updateNeeded = true;
    updateCriteriaCount.expiration_timestamp += 1;
    logger.trace(`expirationTime mismatch on ${dbEntity.id}, db: ${dbExpTimeNs}, network: ${ns.toString()}`);
  }

  // compare keys as buffers
  const {protoBuffer, ed25519Hex} = utils.getBufferAndEd25519HexFromKey(networkEntity.key);
  const dbKey = dbEntity.key === null ? Buffer.from('') : dbEntity.key;
  if (Buffer.compare(dbKey, protoBuffer) !== 0) {
    updateEntity.public_key = ed25519Hex;
    updateEntity.key = protoBuffer;
    updateNeeded = true;
    updateCriteriaCount.key += 1;
    logger.trace(`key mismatch on ${dbEntity.id}, db: ${dbKey}, network: ${protoBuffer}`);
  }

  // compare memos as strings
  const dbMemo = dbEntity.memo === null ? '' : dbEntity.memo;
  if (dbMemo !== networkEntity.accountMemo) {
    updateEntity.memo = networkEntity.accountMemo;
    updateNeeded = true;
    updateCriteriaCount.memo += 1;
    logger.trace(`memo mismatch on ${dbEntity.id}, db: ${dbMemo}, network: ${networkEntity.accountMemo}`);
  }

  // compares proxy ids as strings and supports proxy_account_id 0 or null value from db and null value from network.
  const dbProxy = dbEntity.proxy_account_id === '0' ? null : dbEntity.proxy_account_id;
  const networkProxy = utils.getEntityId(networkEntity.proxyAccountId);
  if (dbProxy !== networkProxy) {
    updateEntity.proxy_account_id = networkEntity.proxyAccountId;
    updateNeeded = true;
    updateCriteriaCount.proxy_account_id += 1;
    logger.trace(`proxy_account_id mismatch on ${dbEntity.id}, db: ${dbProxy}, network: ${networkProxy}}`);
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

  let networkEntity;
  try {
    if (dbEntity.type === 1) {
      networkEntity = await networkEntityService.getAccountInfo(csvEntity.entity);
    } else {
      logger.debug(`Entity type '${dbEntity.type}' is not supported, only accounts (1) are supported, skipping`);
      return null;
    }
  } catch (e) {
    logger.debug(`Error retrieving account ${csvEntity.entity} from network, script should be rerun, error: ${e}`);

    return null;
  }

  return getUpdatedEntity(dbEntity, networkEntity);
};

/**
 * Batch retrieve the entities from the network to avoid potential throttle related errors
 * @param csvEntities
 * @returns {Promise<[]>}
 */
const batchGetVerifiedEntities = async (csvEntities) => {
  const batchedEntities = Array.from(
    {length: Math.ceil(csvEntities.length / config.accountInfoBatchSize)},
    (_, index) => csvEntities.slice(index * config.accountInfoBatchSize, (index + 1) * config.accountInfoBatchSize)
  );

  logger.trace(
    `batched update list retrieval into: ${batchedEntities.length} buckets of ${config.accountInfoBatchSize}`
  );
  let updateList = [];
  for (let i = 0; i < batchedEntities.length; i++) {
    try {
      await dbEntityService.getClientConnection();
      updateList = updateList.concat((await Promise.all(batchedEntities[i].map(getVerifiedEntity))).filter((x) => !!x));
    } catch (e) {
      logger.debug(`Error batch retrieving accounts from network, error: ${e}. ${e.stack}`);
    } finally {
      await dbEntityService.releaseClientConnection();
    }
  }

  return updateList;
};

const printBalanceSpent = (startBalance, endBalance) => {
  if (startBalance > 0 && endBalance > 0) {
    logger.debug(
      `Network accountInfo calls cost ${
        startBalance - endBalance
      } tℏ. startBalance: ${startBalance}, endBalance: ${endBalance}`
    );
  } else {
    logger.debug(
      `Network error encountered retrieving balances. startBalance: ${startBalance}, endBalance: ${endBalance}`
    );
  }
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

  updateList = await batchGetVerifiedEntities(csvEntities);
  const elapsedTime = process.hrtime(mergeStart);

  logger.info(
    `${csvEntities.length} entities were retrieved and compared in ${utils.getElapsedTimeString(elapsedTime)},
    ${updateList.length} were found to be out-of-date`
  );

  if (updateList.length > 0) {
    logger.debug(
      `updateCriteriaCount ${JSON.stringify(
        getCombinedUpdateCriteriaCount(updateList.map((x) => x.updateCriteriaCount))
      )}`
    );
  }

  const endBalance = await networkEntityService.getAccountBalance();
  printBalanceSpent(startBalance, endBalance);

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
  try {
    await dbEntityService.getClientConnection();
    await dbEntityService.beginTransaction();
    const updatedList = await Promise.all(entitiesToUpdate.map(dbEntityService.updateEntity));
    const elapsedTime = process.hrtime(updateStart);
    await dbEntityService.commitTransaction();

    logger.info(`Updated ${updatedList.length} entities in ${utils.getElapsedTimeString(elapsedTime)}`);
  } catch (e) {
    await dbEntityService.rollbackTransaction();
    logger.debug(`Error updating entities in db. Rolled back updates, error: ${e}. ${e.stack}`);
    throw e;
  } finally {
    await dbEntityService.releaseClientConnection();
  }
};

module.exports = {
  updateStaleDBEntities,
  getUpdateList,
};
