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
const _ = require('lodash');
const log4js = require('log4js');
const Pool = require('pg-pool');

// local
const config = require('./config');
const utils = require('./utils');

const logger = log4js.getLogger();

const pool = new Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
});
let client;
let dbEntityCache = {};

const getClientConnection = async () => {
  if (!config.db.useCache) {
    client = await pool.connect();
  }

  logger.trace(`Obtained connection`);
};

const beginTransaction = async () => {
  if (!config.db.useCache) {
    await client.query('begin');
  }

  logger.trace(`Begun transaction`);
};

const commitTransaction = async () => {
  if (!config.db.useCache) {
    await client.query('commit');
  }

  logger.trace(`Committed transaction`);
};

const rollbackTransaction = async () => {
  if (!config.db.useCache) {
    await client.query('rollback');
  }

  logger.trace(`Rolled back transaction`);
};

const releaseClientConnection = async () => {
  if (!config.db.useCache) {
    await client.release();
  }

  logger.trace(`Released connection`);
};

/**
 * Extract entity object with shard, relam and num from entity id string
 * @param {String} id
 * @returns {{num: number, realm: number, shard: number}} entity
 */
const getEntityObjFromString = (id) => {
  let entityIdObj = {
    shard: 0,
    realm: 0,
    num: 0,
  };

  const idParts = id.split('.');
  if (idParts.length === 1) {
    entityIdObj.num = id;
  } else if (idParts.length === 3) {
    entityIdObj.shard = idParts[0];
    entityIdObj.realm = idParts[1];
    entityIdObj.num = idParts[2];
  } else {
    throw Error('Id format is incorrect');
  }

  return entityIdObj;
};

/**
 * Retrieve entity json object from db
 * @param {String} id
 * @returns {Promise<*>}
 */
const getEntity = async (id) => {
  const entityIdObj = getEntityObjFromString(id);

  logger.trace(`getEntity for ${id} from db`);

  let entity = null;
  if (config.db.useCache && dbEntityCache[id] !== undefined) {
    logger.trace(`Retrieved ${id} from cache: ${JSON.stringify(dbEntityCache[id])}`);
    entity = dbEntityCache[id];
    entity.key = Buffer.from(entity.key); // ensure key is a buffer
  } else {
    const paramValues = [entityIdObj.shard, entityIdObj.realm, entityIdObj.num];
    const entityFromDb = await client.query(
      `select *
         from entity
         where shard = $1
           and realm = $2
           and num = $3`,
      paramValues
    );

    entity = entityFromDb.rows[0];
  }

  return entity;
};

/**
 * Update matching entity in db
 * @param entity
 * @returns {Promise<void>}
 */
const updateEntity = async (entity) => {
  const paramValues = [
    entity.auto_renew_period,
    entity.deleted,
    entity.public_key,
    entity.expiration_timestamp,
    entity.key,
    entity.memo,
    entity.proxy_account_id,
    entity.id,
  ];

  if (config.dryRun === false) {
    try {
      await client.query(
        `update entity
           set auto_renew_period    = $1,
               deleted              = $2,
               public_key           = $3,
               expiration_timestamp = $4,
               key                  = $5,
               memo                 = $6,
               proxy_account_id     = $7
           where id = $8`,
        paramValues
      );
    } catch (e) {
      logger.trace(`Error updating entity ${entity.id}, entity: ${JSON.stringify(entity)}: ${e}`);
      throw e;
    }

    logger.trace(`Updated entity ${entity.id}`);
  }
};

const restoreDbEntityCache = () => {
  dbEntityCache = utils.getDbEntityCache();
};

restoreDbEntityCache();
if (config.db.useCache && !_.isEmpty(dbEntityCache)) {
  logger.info(
    "SDK network calls will pull from local cache as 'hedera.mirror.entityUpdate.db.useCache' is set to true and valid cache exists"
  );
}

module.exports = {
  beginTransaction,
  commitTransaction,
  getClientConnection,
  getEntity,
  releaseClientConnection,
  rollbackTransaction,
  updateEntity,
};
