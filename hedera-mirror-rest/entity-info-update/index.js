#!/usr/bin/env node
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

const logger = log4js
  .configure({
    appenders: {
      console: {
        layout: {
          pattern: '%d{yyyy-MM-ddThh:mm:ss.SSSO} %p %m',
          type: 'pattern',
        },
        type: 'stdout',
      },
    },
    categories: {
      default: {
        appenders: ['console'],
        level: 'debug',
      },
    },
  })
  .getLogger();

// local
const entityUpdateHandler = require('./entityUpdateHandler');
const utils = require('./utils');

// get entity objects from CSV
const entitiesToValidate = utils.readEntityCSVFileSync();

const getUpdateList = async (entities) => {
  return entityUpdateHandler.getUpdateList(entities);
};

// console.log(`*** entitiesToUpdate[0]: ${JSON.stringify(entitiesToUpdate[0])}`);
getUpdateList(entitiesToValidate).then(async (entitiesToUpdate) => {
  await entityUpdateHandler.updateStaleDBEntities(entitiesToUpdate);
  logger.info(`End of entity-info-update`);
});

// const accountInfo = networkEntityService.getAccountInfo(entitiesToUpdate[0].entity).then((accountResponse) => {
//   // console.log(`*** Index Retrieved account: ${JSON.stringify(accountResponse)}`);
//
//   const accountInfoCurrent = networkEntityService.getAccountInfo(accountResponse.accountId, entitiesToUpdate[0].keyDatabase).then((accountInfoCurrentResponse) => {
//     // console.log(`*** accountInfoCurrent: ${JSON.stringify(accountInfoCurrentResponse)}`);
//     return accountInfoCurrentResponse;
//   }).then((networkresp) => {
//     const entityFromDBb = dbEntityService.getEntity(entitiesToUpdate[0].entity).then((dbResponse) => {
//       // console.log(`*** Index Retrieved entity from db: ${JSON.stringify(dbResponse)}`);
//       console.log(`*** CSV entity key: ${entitiesToUpdate[0].keyDatabase}`);
//       console.log(`*** Network entity key: ${networkresp.key._keys.toString()}`);
//       // console.log(`*** Network entity Publickey: ${PublicKey.fromString(networkresp.key._keys).toString()}`);
//       console.log(`*** DB entity key: ${dbResponse.key.toString('hex')}`);
//       return dbResponse;
//     });
//   });
// });
//
// logger.info(`*** End of entity-info-update`);

// utils.readEntityCSVFile().then((entitiesToUpdate) => {
//   const accountInfo = entityService.getAccountInfo(entitiesToUpdate[0].entity).then((accountResponse) => {
//     console.log(`Index Retrieved account`);
//   });
//
//   console.log(`*** End of entity-info-update`);
// });

// determine input - single account (x.y.z), range (x1.y1.z1-x2.y2.z2), CSV (file-path)

// if CSV read in file and get list of entityIds

// for each entity do a select from DB and a get AccountInfo from network.

// compare items. Where it differs create a new list to be inserted. Not cases where you merge results vs just update db

// Have dedicated logic for pulling from db where possible, comparing objects, inserting into db
