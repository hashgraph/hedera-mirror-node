/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *
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

const extend = require('extend');
const fs = require('fs');
const _ = require('lodash');
const log4js = require('log4js');
const path = require('path');
const logger = log4js.getLogger();

const REQUIRED_FIELDS = [
  'servers',
  'interval',
  'shard',
  'account.intervalMultiplier',
  'account.limit',
  'balance.freshnessThreshold',
  'balance.intervalMultiplier',
  'balance.limit',
  'stateproof.intervalMultiplier',
  'transaction.freshnessThreshold',
  'transaction.intervalMultiplier',
  'transaction.limit',
  'topic.freshnessThreshold',
  'topic.intervalMultiplier',
  'topic.limit',
];

function load(configFile) {
  let config = {};

  try {
    config = JSON.parse(fs.readFileSync(configFile).toString('utf-8'));
  } catch (err) {
    logger.warn(`Skipping configuration ${configFile}: ${err}`);
    return config;
  }

  for (const field of REQUIRED_FIELDS) {
    if (!_.has(config, field)) {
      throw new Error(`required field "${field}" not found in configuration file ${configFile}`);
    }
  }

  if (!Array.isArray(config.servers) || config.servers.length === 0) {
    throw new Error(`Invalid servers "${JSON.stringify(config.servers)}" in configuration file ${configFile}`);
  }

  return config;
}

let config = {};
let loaded = false;

if (!loaded) {
  config = load(path.join(__dirname, 'config', 'default.serverlist.json'));
  const customConfig = load(path.join(__dirname, 'config', 'serverlist.json'));
  extend(true, config, customConfig);
  loaded = true;
}

module.exports = config;
