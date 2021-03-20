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
const Pool = require('pg-pool');

// local
const config = require('./config');

const logger = log4js.getLogger();

const pool = new Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
});

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
  const paramValues = [entityIdObj.shard, entityIdObj.realm, entityIdObj.num];
  const entityFromDb = await pool.query(
    `select *
       from t_entities
       where entity_shard = $1
         and entity_realm = $2
         and entity_num = $3`,
    paramValues
  );

  return entityFromDb.rows[0];
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
    entity.ed25519_public_key_hex,
    entity.exp_time_ns,
    entity.key,
    entity.proxy_account_id,
    entity.id,
  ];

  if (config.dryRun === false) {
    await pool.query(
      `update t_entities
         set auto_renew_period      = $1,
             deleted                = $2,
             ed25519_public_key_hex = $3,
             exp_time_ns            = $4,
             key                    = $5,
             proxy_account_id       = $6
         where id = $7`,
      paramValues
    );
  }

  logger.trace(`Updated entity ${entity.id}`);
};

module.exports = {
  getEntity,
  updateEntity,
};
