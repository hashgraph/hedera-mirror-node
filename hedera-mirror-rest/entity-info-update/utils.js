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
const fs = require('fs');
const log4js = require('log4js');
const math = require('mathjs');

// local
const config = require('./config');

const logger = log4js.getLogger();

const constructEntity = (index, headerRow, entityRow) => {
  const entityObj = {};
  const splitEntityRow = Array.from(entityRow.split(',')).filter((x) => x != null);

  for (let i = 0; i < headerRow.length; i++) {
    entityObj[headerRow[i].trim()] = splitEntityRow[i].trim();
  }

  return entityObj;
};

const readEntityCSVFileSync = () => {
  logger.info(`Parsing csv entity file ...`);
  const entities = [];

  const data = fs.readFileSync(config.filePath, 'utf-8');

  const fileContent = data.split('\n');
  const headers = Array.from(fileContent[0].split(',')).filter((x) => x != null);

  // ensure 1st column is entity num of expected format
  if (!_.eq(headers[0], 'entity')) {
    throw Error("CSV must have a header column with 1st column being 'entity'");
  }

  for (let i = 1; i < fileContent.length; i++) {
    if (!fileContent[i]) {
      // End of file
      break;
    }

    entities.push(constructEntity(i, headers, fileContent[i]));
  }

  logger.info(`${entities.length} entities were extracted from ${config.filePath}`);

  return entities;
};

/**
 * Converts seconds since epoch (seconds nnnnnnnnn format) to  nanoseconds
 * @param {String} Seconds since epoch (seconds.nnnnnnnnn format)
 * @return {String} ns Nanoseconds since epoch
 */
const secNsToNs = (secNs, ns) => {
  return math
    .add(math.multiply(math.bignumber(secNs.toString()), math.bignumber(1e9)), math.bignumber(ns.toString()))
    .toString();
};

module.exports = {
  readEntityCSVFileSync,
  secNsToNs,
};
