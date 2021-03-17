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

const getConfiguredLogger = (loglevel) => {
  return log4js
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
          level: loglevel,
        },
      },
    })
    .getLogger();
};

// configure prior to local loads such as config to ensure we at least get debug logging initially
let logger = getConfiguredLogger('debug');

// local
const config = require('./config');
const entityUpdateHandler = require('./entityUpdateHandler');
const utils = require('./utils');

logger = getConfiguredLogger(config.log.level);

// get entity objects from CSV
const entitiesToValidate = utils.readEntityCSVFileSync();

const getUpdateList = async (entities) => {
  return entityUpdateHandler.getUpdateList(entities);
};

getUpdateList(entitiesToValidate).then(async (entitiesToUpdate) => {
  if (config.dryRun === false) {
    logger.info(`Updating stale db entries with updated information ...`);
    await entityUpdateHandler.updateStaleDBEntities(entitiesToUpdate);
  } else {
    logger.info(
      `Db update of entities will be skipped as 'hedera.mirror.entityUpdate.dryRun' is set to ${config.dryRun}`
    );
  }
  logger.info(`End of entity-info-update`);
});
